import request from '@/utils/request'

/**
 * 查询动环模拟器与演练当前状态
 */
export function getMockStatus() {
  return request({
    url: '/v1/mock/status',
    method: 'get'
  })
}

/**
 * 启动动环数据流模拟
 */
export function startMock() {
  return request({
    url: '/v1/mock/start',
    method: 'post'
  })
}

/**
 * 暂停动环数据流模拟
 */
export function stopMock() {
  return request({
    url: '/v1/mock/stop',
    method: 'post'
  })
}

/**
 * 一键故障注入：单机柜严重超温 (>38℃)
 */
export function injectOverheat(rackCode = 'A-03') {
  return request({
    url: '/v1/mock/inject-overheat',
    method: 'post',
    params: { rackCode }
  })
}

/**
 * 一键故障注入：市电中断欠压掉电 (0V / 0A)
 */
export function injectBlackout(rackCode = 'A-01') {
  return request({
    url: '/v1/mock/inject-blackout',
    method: 'post',
    params: { rackCode }
  })
}

/**
 * 一键故障注入：机房集群告警风暴 (A-01, A-02, A-03 同时高温)
 */
export function injectStorm() {
  return request({
    url: '/v1/mock/inject-storm',
    method: 'post'
  })
}

/**
 * 一键故障注入：瞬态偶发毛刺 (42℃ 单次)
 */
export function injectGlitch(rackCode = 'A-03') {
  return request({
    url: '/v1/mock/inject-glitch',
    method: 'post',
    params: { rackCode }
  })
}

/**
 * 一键重置恢复全机房正常工况
 */
export function resetMock() {
  return request({
    url: '/v1/mock/reset',
    method: 'post'
  })
}
