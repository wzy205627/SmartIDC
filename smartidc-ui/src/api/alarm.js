import request from '@/utils/request'

/**
 * 获取当前所有未消除活动告警列表
 */
export function getActiveAlarms(params) {
  return request({
    url: '/v1/alarm/active',
    method: 'get',
    params
  })
}

/**
 * 获取动环告警统计数据 (活动总数、高危、警告、已消除)
 */
export function getAlarmStats() {
  return request({
    url: '/v1/alarm/stats',
    method: 'get'
  })
}

/**
 * 人工关闭告警
 */
export function closeAlarm(alarmId) {
  return request({
    url: '/v1/alarm/close',
    method: 'post',
    params: { alarmId }
  })
}

/**
 * 标记误报并平息
 */
export function markFalsePositive(alarmId) {
  return request({
    url: '/v1/alarm/false-positive',
    method: 'post',
    params: { alarmId }
  })
}

/**
 * 动环告警演练 - 模拟注入指定机架越限告警 (支持 CRITICAL 严重 / WARNING 预警)
 */
export function injectDrillAlarm(params = {}) {
  return request({
    url: '/v1/alarm/drill/inject',
    method: 'post',
    params: {
      rackCode: params.rackCode || 'A-01',
      alarmLevel: params.alarmLevel || 'CRITICAL',
      alarmType: params.alarmType || 'TEMP_HIGH',
      metricValue: params.metricValue || '35.80℃',
      summary: params.summary || '[应急演练] A-01机柜进风面发生严重过温，触发二级热岛警报'
    }
  })
}

/**
 * 动环告警演练 - 一键消除所有活动演练告警
 */
export function resetDrillAlarms() {
  return request({
    url: '/v1/alarm/drill/reset',
    method: 'post'
  })
}
