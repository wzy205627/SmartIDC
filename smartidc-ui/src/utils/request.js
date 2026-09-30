import axios from 'axios'
import { ElMessage } from 'element-plus'

const service = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器
service.interceptors.request.use(
  config => {
    // 兼容修复：若传入的 url 带有 /api 前缀，自动剥离避免拼接成 /api/api/... 导致 403
    if (config.url && config.url.startsWith('/api/')) {
      config.url = config.url.substring(4)
    }

    // 从 localStorage 获取当前租户，未登录默认为平台租户 000000
    const tenantId = localStorage.getItem('smartidc_tenant_id') || '000000'
    config.headers['Tenant-Id'] = tenantId

    const token = localStorage.getItem('smartidc_token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器：增加错误提示防抖，防止短时间刷屏弹窗
let lastErrorMsg = ''
let lastErrorTime = 0

service.interceptors.response.use(
  response => {
    const res = response.data
    // 如果响应结果为 Blob/二进制流，直接返回
    if (response.request.responseType === 'blob' || response.request.responseType === 'arraybuffer') {
      return res
    }

    // 业务统一判断
    if (res.code !== 200) {
      ElMessage.error(res.msg || '系统执行异常')
      return Promise.reject(new Error(res.msg || 'Error'))
    }
    return res.data
  },
  error => {
    const message = error.response?.data?.msg || error.message || '网络连接异常'
    const now = Date.now()
    // 同类型错误信息在 3 秒内不重复弹窗，防止轮询失败时 Network Error 刷屏
    if (message !== lastErrorMsg || (now - lastErrorTime) > 3000) {
      lastErrorMsg = message
      lastErrorTime = now
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default service
