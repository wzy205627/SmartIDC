import request from '@/utils/request'

/**
 * 获取指定机架 1~42U 连续插槽全景画像 (包含空闲与占位切片)
 * @param {Number|String} rackId
 */
export function getRackSlots(rackId) {
  return request({
    url: `/v1/device/rack/${rackId}/slots`,
    method: 'get'
  })
}

/**
 * 设备上架操作 (包含 U 位边界、功率容量与几何碰撞检测)
 * @param {Object} data { rackId, deviceName, deviceType, iotDeviceKey, startU, uHeight, ratedPower }
 */
export function mountDevice(data) {
  return request({
    url: '/v1/device/mount',
    method: 'post',
    data
  })
}

/**
 * 设备下架操作 (释放 U 位插槽，级联扣减机架已用 U 位)
 * @param {Number|String} deviceId
 */
export function unmountDevice(deviceId) {
  return request({
    url: `/v1/device/unmount/${deviceId}`,
    method: 'post'
  })
}

/**
 * 设备热替换与容量规划执行 (原子下架旧设备并上架新设备)
 * @param {Number|String} oldDeviceId
 * @param {Object} data
 */
export function replaceDevice(oldDeviceId, data) {
  return request({
    url: `/v1/device/replace/${oldDeviceId}`,
    method: 'post',
    data
  })
}

/**
 * 查询机房设备台账列表
 * @param {Object} params { rackId, deviceType, status }
 */
export function listDevices(params) {
  return request({
    url: '/v1/device/list',
    method: 'get',
    params
  })
}

/**
 * 一键清空指定机架的所有在架设备
 * @param {Number|String} rackId
 */
export function clearRackDevices(rackId) {
  return request({
    url: `/v1/device/rack/${rackId}/clear-all`,
    method: 'post'
  })
}
