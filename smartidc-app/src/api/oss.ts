import { request } from '@/utils/request';
import { getEnvBaseUrl } from '@/config/env';

export interface PresignedUploadVO {
  uploadUrl: string;
  objectKey: string;
  viewUrl: string;
  expiresAt: number;
}

/**
 * 申请 MinIO S3 预签名直传凭证 (15分钟有效期)
 */
export function getPresignedUrlApi(params?: {
  ticketId?: number;
  fileName?: string;
  fileType?: string;
  contentType?: string;
}): Promise<PresignedUploadVO> {
  const query = new URLSearchParams();
  if (params?.ticketId) query.append('ticketId', String(params.ticketId));
  if (params?.fileName) query.append('fileName', params.fileName);
  if (params?.fileType) query.append('fileType', params.fileType);
  if (params?.contentType) query.append('contentType', params.contentType);

  const queryString = query.toString();
  return request<PresignedUploadVO>({
    url: `/v1/mobile/oss/presigned-url${queryString ? `?${queryString}` : ''}`,
    method: 'POST'
  });
}

/**
 * 客户端直传 MinIO S3 (HTTP PUT)
 */
export async function uploadToPresignedUrl(
  uploadUrl: string,
  filePath: string,
  contentType = 'image/jpeg'
): Promise<boolean> {
  return new Promise((resolve, reject) => {
    // #ifdef H5
    // H5 环境将本地 blob/dataURL 转换为 ArrayBuffer 进行 PUT
    fetch(filePath)
      .then((res) => res.arrayBuffer())
      .then((buffer) => {
        return fetch(uploadUrl, {
          method: 'PUT',
          headers: { 'Content-Type': contentType },
          body: buffer
        });
      })
      .then((res) => {
        if (res.ok) resolve(true);
        else reject(new Error(`S3 直传失败 [${res.status}]`));
      })
      .catch(reject);
    // #endif

    // #ifndef H5
    // 小程序与 App 端读取文件内容并通过 uni.request PUT
    // 若特定平台不支持 PUT 二进制，则建议使用 uploadFileFallbackApi
    const fs = uni.getFileSystemManager ? uni.getFileSystemManager() : null;
    if (fs) {
      fs.readFile({
        filePath,
        success: (readRes) => {
          uni.request({
            url: uploadUrl,
            method: 'PUT',
            data: readRes.data,
            header: {
              'Content-Type': contentType
            },
            success: (resp) => {
              if (resp.statusCode === 200) resolve(true);
              else reject(new Error(`S3 直传失败 [${resp.statusCode}]`));
            },
            fail: reject
          });
        },
        fail: reject
      });
    } else {
      resolve(true);
    }
    // #endif
  });
}

/**
 * 专网隔离或域名解析异常时的流式中转兜底上传
 */
export function uploadFileFallbackApi(filePath: string, ticketId?: number): Promise<PresignedUploadVO> {
  const baseUrl = getEnvBaseUrl();
  const rawAuth = uni.getStorageSync('auth');
  let token = '';
  if (rawAuth) {
    try {
      const parsed = typeof rawAuth === 'string' ? JSON.parse(rawAuth) : rawAuth;
      token = parsed.accessToken || '';
    } catch (e) {}
  }

  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${baseUrl}/v1/mobile/oss/upload${ticketId ? `?ticketId=${ticketId}` : ''}`,
      filePath,
      name: 'file',
      header: {
        Authorization: token ? `Bearer ${token}` : '',
        'X-Client-Platform': 'uni-app'
      },
      success: (res) => {
        if (res.statusCode === 200) {
          try {
            const body = JSON.parse(res.data);
            if (body.code === 200 && body.data) {
              resolve(body.data);
            } else {
              reject(new Error(body.message || '上传中转失败'));
            }
          } catch (e) {
            reject(new Error('解析上传响应失败'));
          }
        } else {
          reject(new Error(`中转上传异常 [${res.statusCode}]`));
        }
      },
      fail: reject
    });
  });
}
