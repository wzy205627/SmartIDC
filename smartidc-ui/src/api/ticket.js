import request from '@/utils/request'

/**
 * 创建排障工单 (支持从活动告警一键生成)
 */
export function createWorkTicket(data) {
  return request({
    url: '/v1/ops/ticket/create',
    method: 'post',
    data
  })
}

/**
 * 分页查询工单台账列表
 */
export function getWorkTicketPage(params) {
  return request({
    url: '/v1/ops/ticket/page',
    method: 'get',
    params
  })
}

/**
 * 获取指定工单详情与处置画像
 */
export function getWorkTicketDetail(ticketId) {
  return request({
    url: `/v1/ops/ticket/${ticketId}`,
    method: 'get'
  })
}

/**
 * 指派/转派运维工程师
 */
export function assignWorkTicket(ticketId, data) {
  return request({
    url: `/v1/ops/ticket/${ticketId}/assign`,
    method: 'post',
    data
  })
}

/**
 * 工程师接单开工 (已指派 -> 排障中)
 */
export function startWorkTicket(ticketId) {
  return request({
    url: `/v1/ops/ticket/${ticketId}/start`,
    method: 'post'
  })
}

/**
 * 提交现场排障处置记录 (排障中 -> 待复核)
 */
export function resolveWorkTicket(ticketId, data) {
  return request({
    url: `/v1/ops/ticket/${ticketId}/resolve`,
    method: 'post',
    data
  })
}

/**
 * 主管复核办结并闭环消警 (待复核 -> 已办结)
 */
export function completeWorkTicket(ticketId, closeAlarm = true) {
  return request({
    url: `/v1/ops/ticket/${ticketId}/complete`,
    method: 'post',
    params: { closeAlarm }
  })
}

/**
 * 获取工单态势全盘统计
 */
export function getWorkTicketStats() {
  return request({
    url: '/v1/ops/ticket/stats',
    method: 'get'
  })
}
