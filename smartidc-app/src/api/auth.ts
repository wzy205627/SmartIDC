import { request } from '@/utils/request';

export interface WxLoginParams {
  code?: string;
  mock?: boolean;
  mockUserCode?: string;
}

export interface WxBindParams {
  bindTicket: string;
  username: string;
  password: string;
}

export interface PasswordLoginParams {
  username: string;
  password: string;
  tenantId?: string;
}

export interface EngineerUser {
  userId: number;
  username: string;
  nickName: string;
  roleKey: string;
  phone: string;
  assignedRooms: string[];
}

export interface TenantSimple {
  tenantId: string;
  tenantName: string;
  isCurrent: boolean;
}

export interface MobileLoginResponse {
  authState: 'LOGIN_SUCCESS' | 'NEED_BIND' | 'UNAUTHORIZED_ACCESS';
  bindTicket?: string;
  accessToken?: string;
  refreshToken?: string;
  currentTenantId?: string;
  user?: EngineerUser;
  tenantList?: TenantSimple[];
}

export function wxLoginApi(data: WxLoginParams): Promise<MobileLoginResponse> {
  return request<MobileLoginResponse>({
    url: '/v1/mobile/auth/wx-login',
    method: 'POST',
    data
  });
}

export function bindWechatApi(data: WxBindParams): Promise<MobileLoginResponse> {
  return request<MobileLoginResponse>({
    url: '/v1/mobile/auth/bind-wechat',
    method: 'POST',
    data
  });
}

export function passwordLoginApi(data: PasswordLoginParams): Promise<MobileLoginResponse> {
  return request<MobileLoginResponse>({
    url: '/v1/mobile/auth/login',
    method: 'POST',
    data
  });
}

export function refreshTokenApi(refreshToken: string): Promise<{ accessToken: string }> {
  return request<{ accessToken: string }>({
    url: '/v1/mobile/auth/refresh-token',
    method: 'POST',
    data: { refreshToken }
  });
}

export function switchTenantApi(targetTenantId: string): Promise<{ accessToken: string; currentTenantId: string }> {
  return request<{ accessToken: string; currentTenantId: string }>({
    url: `/v1/mobile/auth/switch-tenant?targetTenantId=${encodeURIComponent(targetTenantId)}`,
    method: 'POST'
  });
}

export function logoutApi(refreshToken?: string): Promise<void> {
  return request<void>({
    url: `/v1/mobile/auth/logout${refreshToken ? `?refreshToken=${encodeURIComponent(refreshToken)}` : ''}`,
    method: 'POST'
  });
}

export function getUserInfoApi(): Promise<EngineerUser> {
  return request<EngineerUser>({
    url: '/v1/mobile/auth/user-info',
    method: 'GET'
  });
}
