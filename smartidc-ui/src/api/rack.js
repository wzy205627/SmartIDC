import request from '@/utils/request'

/**
 * 查询机房机架资产列表
 * @param {Object} params { rackCode, roomName, status, keyword }
 */
export function listRacks(params) {
  const cleanParams = {}
  if (params) {
    Object.keys(params).forEach(k => {
      const v = params[k]
      if (v !== '' && v !== null && v !== undefined) {
        cleanParams[k] = v
      }
    })
  }
  return request({
    url: '/v1/rack/list',
    method: 'get',
    params: cleanParams
  })
}

/**
 * 获取机架详细信息
 * @param {Number|String} rackId 
 */
export function getRack(rackId) {
  return request({
    url: `/v1/rack/${rackId}`,
    method: 'get'
  })
}
