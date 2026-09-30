import { getEnvBaseUrl } from '@/config/env';

let isRefreshing = false;
let refreshSubscribers: ((token: string) => void)[] = [];

function onTokenRefreshed(token: string) {
  refreshSubscribers.forEach((cb) => cb(token));
  refreshSubscribers = [];
}

function getStoredTokens(): { accessToken: string; refreshToken: string } {
  try {
    const raw = uni.getStorageSync('auth');
    if (!raw) return { accessToken: '', refreshToken: '' };
    const auth = typeof raw === 'string' ? JSON.parse(raw) : raw;
    return {
      accessToken: auth.accessToken || '',
      refreshToken: auth.refreshToken || ''
    };
  } catch (e) {
    return { accessToken: '', refreshToken: '' };
  }
}

function updateStoredAccessToken(newToken: string) {
  try {
    const raw = uni.getStorageSync('auth');
    if (!raw) return;
    const auth = typeof raw === 'string' ? JSON.parse(raw) : raw;
    auth.accessToken = newToken;
    uni.setStorageSync('auth', typeof raw === 'string' ? JSON.stringify(auth) : auth);
  } catch (e) {
    console.error('[Request] 更新存储 AccessToken 失败', e);
  }
}

function clearStoredAuth() {
  try {
    uni.removeStorageSync('auth');
  } catch (e) {
    console.error('[Request] 清除存储失败', e);
  }
}

export function request<T = any>(options: UniApp.RequestOptions): Promise<T> {
  const baseUrl = getEnvBaseUrl();
  const tokens = getStoredTokens();

  const url = options.url.startsWith('http') ? options.url : `${baseUrl}${options.url}`;
  const header = {
    ...options.header,
    Authorization: tokens.accessToken ? `Bearer ${tokens.accessToken}` : '',
    'X-Client-Platform': 'uni-app'
  };

  return new Promise((resolve, reject) => {
    uni.request({
      ...options,
      url,
      header,
      success: async (res) => {
        // 1. 拦截 HTTP 401: Access Token 过期
        if (res.statusCode === 401) {
          const currentTokens = getStoredTokens();
          if (!currentTokens.refreshToken) {
            clearStoredAuth();
            uni.reLaunch({ url: '/pages/login/index' });
            return reject(new Error('登录凭证已失效，请重新登录'));
          }

          // 正在单飞刷新中，将后续并发请求压入挂起等待队列
          if (isRefreshing) {
            return new Promise<T>((retryResolve, retryReject) => {
              refreshSubscribers.push((newToken: string) => {
                options.header = { ...options.header, Authorization: `Bearer ${newToken}` };
                request<T>(options).then(retryResolve).catch(retryReject);
              });
            }).then(resolve).catch(reject);
          }

          // 开启单飞刷新
          isRefreshing = true;
          try {
            const refreshRes = await new Promise<{ accessToken: string }>((rResolve, rReject) => {
              uni.request({
                url: `${baseUrl}/v1/mobile/auth/refresh-token`,
                method: 'POST',
                data: { refreshToken: currentTokens.refreshToken },
                header: { 'X-Client-Platform': 'uni-app' },
                success: (rf) => {
                  if (rf.statusCode === 200) {
                    const b: any = rf.data;
                    const d = (b && typeof b.data !== 'undefined') ? b.data : b;
                    if (d && d.accessToken) {
                      rResolve(d);
                    } else {
                      rReject(new Error('未返回有效令牌'));
                    }
                  } else {
                    rReject(new Error('刷新令牌已失效'));
                  }
                },
                fail: (err) => rReject(err)
              });
            });

            const newAccessToken = refreshRes.accessToken;
            updateStoredAccessToken(newAccessToken);
            onTokenRefreshed(newAccessToken);

            options.header = { ...options.header, Authorization: `Bearer ${newAccessToken}` };
            const retryRes = await request<T>(options);
            return resolve(retryRes);
          } catch (err) {
            clearStoredAuth();
            uni.reLaunch({ url: '/pages/login/index' });
            return reject(new Error('会话过期，请重新登录'));
          } finally {
            isRefreshing = false;
          }
        }

        // 2. 正常业务响应
        if (res.statusCode >= 200 && res.statusCode < 300) {
          const body: any = res.data;
          if (body && typeof body.code !== 'undefined' && body.code !== 200 && body.code !== 0) {
            return reject(new Error(body.message || body.msg || '业务操作异常'));
          }
          return resolve((body && typeof body.data !== 'undefined') ? body.data : body);
        }

        const body: any = res.data;
        const errorMsg = (body && (body.message || body.msg)) || `HTTP 异常 [${res.statusCode}]`;
        reject(new Error(errorMsg));
      },
      fail: (err) => {
        // 弱网容灾提示
        uni.showToast({ title: '机房网络不稳定，已启用离线重试', icon: 'none' });
        reject(err);
      }
    });
  });
}
