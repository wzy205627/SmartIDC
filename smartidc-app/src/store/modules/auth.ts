import { defineStore } from 'pinia';
import { ref } from 'vue';
import {
  type EngineerUser,
  type TenantSimple,
  type MobileLoginResponse,
  type PasswordLoginParams,
  type WxLoginParams,
  type WxBindParams,
  passwordLoginApi,
  wxLoginApi,
  bindWechatApi,
  refreshTokenApi,
  switchTenantApi,
  logoutApi
} from '@/api/auth';
import { uniStorageAdapter } from '@/utils/storage';

export const useAuthStore = defineStore(
  'auth',
  () => {
    const accessToken = ref<string>('');
    const refreshToken = ref<string>('');
    const currentTenantId = ref<string>('000000');
    const user = ref<EngineerUser | null>(null);
    const tenantList = ref<TenantSimple[]>([]);

    function setAuthData(data: MobileLoginResponse) {
      if (data.accessToken) accessToken.value = data.accessToken;
      if (data.refreshToken) refreshToken.value = data.refreshToken;
      if (data.currentTenantId) currentTenantId.value = data.currentTenantId;
      if (data.user) user.value = data.user;
      if (data.tenantList) tenantList.value = data.tenantList;
    }

    async function loginWithPassword(params: PasswordLoginParams): Promise<MobileLoginResponse> {
      const res = await passwordLoginApi(params);
      if (res.authState === 'LOGIN_SUCCESS') {
        setAuthData(res);
      }
      return res;
    }

    async function loginWithWechat(params: WxLoginParams): Promise<MobileLoginResponse> {
      const res = await wxLoginApi(params);
      if (res.authState === 'LOGIN_SUCCESS') {
        setAuthData(res);
      }
      return res;
    }

    async function bindWechat(params: WxBindParams): Promise<MobileLoginResponse> {
      const res = await bindWechatApi(params);
      if (res.authState === 'LOGIN_SUCCESS') {
        setAuthData(res);
      }
      return res;
    }

    async function refreshAccessToken(): Promise<boolean> {
      if (!refreshToken.value) {
        return false;
      }
      try {
        const res = await refreshTokenApi(refreshToken.value);
        if (res && res.accessToken) {
          accessToken.value = res.accessToken;
          return true;
        }
        return false;
      } catch (e) {
        console.error('[AuthStore] 令牌续期失败:', e);
        return false;
      }
    }

    async function switchTenant(targetTenantId: string): Promise<boolean> {
      try {
        const res = await switchTenantApi(targetTenantId);
        if (res && res.accessToken) {
          accessToken.value = res.accessToken;
          currentTenantId.value = res.currentTenantId || targetTenantId;
          tenantList.value = tenantList.value.map((t) => ({
            ...t,
            isCurrent: t.tenantId === currentTenantId.value
          }));
          return true;
        }
        return false;
      } catch (e) {
        console.error('[AuthStore] 租户切换失败:', e);
        throw e;
      }
    }

    async function logout() {
      const tokenToRevoke = refreshToken.value;
      accessToken.value = '';
      refreshToken.value = '';
      user.value = null;
      tenantList.value = [];
      try {
        if (tokenToRevoke) {
          await logoutApi(tokenToRevoke);
        }
      } catch (ignored) {}
    }

    return {
      accessToken,
      refreshToken,
      currentTenantId,
      user,
      tenantList,
      setAuthData,
      loginWithPassword,
      loginWithWechat,
      bindWechat,
      refreshAccessToken,
      switchTenant,
      logout
    };
  },
  {
    persist: {
      storage: uniStorageAdapter,
      paths: ['accessToken', 'refreshToken', 'currentTenantId', 'user', 'tenantList']
    }
  }
);
