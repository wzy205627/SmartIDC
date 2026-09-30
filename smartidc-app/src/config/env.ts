/**
 * 跨端环境变量与 BaseURL 规范注入
 */

// #ifdef H5
// H5 环境走 Vite devServer proxy 避免跨域
const BASE_URL = '/api';
// #endif

// #ifdef MP-WEIXIN
// 小程序生产环境需配置微信公众平台合法域名，本地局域网调试使用开发宿主机地址
const BASE_URL = 'http://127.0.0.1:8080/api';
// #endif

// #ifndef H5
// #ifndef MP-WEIXIN
// 其他端 (App/快应用等)
const BASE_URL = 'http://127.0.0.1:8080/api';
// #endif
// #endif

export function getEnvBaseUrl(): string {
  return BASE_URL;
}

export const ENV_CONFIG = {
  timeout: 10000,
  tokenHeader: 'Authorization',
  clientPlatform: 'uni-app-mobile'
};
