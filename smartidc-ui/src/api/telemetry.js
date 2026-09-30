import request from '@/utils/request'

/**
 * 查询指定机架最新实时遥测快照 (Redis 缓存优先)
 */
export function getLatestTelemetry(rackCode) {
  return request({
    url: `/v1/telemetry/latest/${rackCode}`,
    method: 'get'
  })
}

/**
 * 查询机架历史遥测指标序列
 */
export function getTelemetryHistory(rackCode, limit = 60) {
  return request({
    url: `/v1/telemetry/history/${rackCode}`,
    method: 'get',
    params: { limit }
  })
}

/**
 * 查询机架多轴动环历史时序与降采样数据
 * @param {string} rackCode 机柜编号 (如: A-03)
 * @param {object} params { timeRange: '1h' | '6h' | '24h' | '7d', metrics: string }
 */
export function getTelemetryTimeline(rackCode, params = {}) {
  return request({
    url: `/v1/telemetry/timeline/${rackCode}`,
    method: 'get',
    params
  })
}

