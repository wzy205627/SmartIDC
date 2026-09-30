import { Client } from '@stomp/stompjs'

/**
 * 智维云 STOMP WebSocket 全双工长连接管理单例
 * 支持自动心跳保活、断线指数重连、订阅队列自动恢复与多主题并发订阅
 */
class StompClientManager {
  constructor() {
    this.client = null
    this.connected = false
    this.subIdCounter = 0
    // 活跃订阅注册表: subId -> { destination, callback, stompSub }
    this.subRegistry = new Map()
  }

  init(options = {}) {
    if (this.client && this.client.active) {
      return this.client
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    // 优先直连后端 8080 端口，若端口一致则沿用当前 host
    const defaultBrokerUrl = `${protocol}//${window.location.hostname}:8080/ws/smartidc`
    const brokerURL = options.brokerURL || defaultBrokerUrl

    this.client = new Client({
      brokerURL: brokerURL,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: (frame) => {
        this.connected = true
        console.log(`[STOMP] 成功连接至智维云 WebSocket 网关: ${brokerURL}`)
        
        // 重新挂载全部已注册的订阅通道 (断线重连自愈)
        this.subRegistry.forEach((item) => {
          this._executeSubscribe(item)
        })

        if (options.onConnect) {
          options.onConnect(frame)
        }
      },
      onDisconnect: () => {
        this.connected = false
        console.warn('[STOMP] 与智维云 WebSocket 网关断开连接')
        this.subRegistry.forEach((item) => {
          item.stompSub = null
        })
        if (options.onDisconnect) {
          options.onDisconnect()
        }
      },
      onStompError: (frame) => {
        console.error('[STOMP] 协议通信错误:', frame.headers?.['message'], frame.body)
      },
      onWebSocketClose: () => {
        this.connected = false
        console.warn('[STOMP] 底层 WebSocket 连接关闭，等待自动重连...')
      }
    })

    this.client.activate()
    return this.client
  }

  _executeSubscribe(item) {
    if (!this.client || !this.connected) return
    try {
      item.stompSub = this.client.subscribe(item.destination, (message) => {
        try {
          const payload = JSON.parse(message.body)
          item.callback(payload, message)
        } catch (e) {
          item.callback(message.body, message)
        }
      })
      console.log(`[STOMP] 成功订阅频道: ${item.destination}`)
    } catch (err) {
      console.error(`[STOMP] 订阅频道失败 [${item.destination}]:`, err)
    }
  }

  /**
   * 订阅指定主题通道 (支持在连接建立前后随时调用，断网自动恢复)
   * @param {string} destination 例如 '/topic/alarms' 或 '/topic/rack-telemetry/A-03'
   * @param {Function} callback 接收到报文时的回调函数
   * @returns {{ unsubscribe: Function }} 包含注销订阅方法的句柄
   */
  subscribe(destination, callback) {
    if (!this.client) {
      this.init()
    }

    const subId = ++this.subIdCounter
    const subItem = {
      id: subId,
      destination,
      callback,
      stompSub: null
    }

    this.subRegistry.set(subId, subItem)

    // 若当前已处于连接态，立即执行订阅
    if (this.connected) {
      this._executeSubscribe(subItem)
    }

    return {
      unsubscribe: () => {
        const item = this.subRegistry.get(subId)
        if (item) {
          if (item.stompSub) {
            try {
              item.stompSub.unsubscribe()
              console.log(`[STOMP] 已注销订阅: ${destination}`)
            } catch (e) {
              // 忽略注销时的连接异常
            }
          }
          this.subRegistry.delete(subId)
        }
      }
    }
  }

  /**
   * 主动断开连接
   */
  disconnect() {
    if (this.client) {
      this.client.deactivate()
      this.client = null
      this.connected = false
      this.subRegistry.clear()
      console.log('[STOMP] WebSocket 客户端已主动注销关闭')
    }
  }
}

export const stompManager = new StompClientManager()
export default stompManager
