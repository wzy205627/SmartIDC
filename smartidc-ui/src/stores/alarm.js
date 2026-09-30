import { defineStore } from 'pinia'
import { getActiveAlarms, getAlarmStats, closeAlarm, markFalsePositive } from '@/api/alarm'
import stompManager from '@/utils/websocket'
import { alarmAudio } from '@/utils/alarmAudio'
import { ElNotification } from 'element-plus'

export const useAlarmStore = defineStore('alarm', {
  state: () => ({
    activeAlarms: [],
    alarmStats: {
      activeTotal: 0,
      criticalCount: 0,
      warningCount: 0,
      clearedCount: 0
    },
    loading: false,
    initialized: false,
    wsSub: null
  }),

  getters: {
    /**
     * 判断指定机柜当前是否存在活动中的未解除告警
     */
    isRackInAlarm: (state) => (rackCode) => {
      if (!rackCode) return false
      return state.activeAlarms.some(a => a.rackCode === rackCode)
    },

    /**
     * 获取指定机柜关联的活动告警实体 (优先返回最高级别)
     */
    getRackAlarm: (state) => (rackCode) => {
      if (!rackCode) return null
      const matched = state.activeAlarms.filter(a => a.rackCode === rackCode)
      if (matched.length === 0) return null
      // 优先返回 CRITICAL
      return matched.find(a => a.alarmLevel === 'CRITICAL') || matched[0]
    }
  },

  actions: {
    async fetchActiveAlarms() {
      try {
        const data = await getActiveAlarms()
        this.activeAlarms = data || []
        this.alarmStats.activeTotal = this.activeAlarms.length
      } catch (err) {
        console.error('[AlarmStore] 拉取活动告警失败:', err)
      }
    },

    async fetchStats() {
      try {
        const data = await getAlarmStats()
        if (data) {
          this.alarmStats = {
            activeTotal: Number(data.activeTotal || 0),
            criticalCount: Number(data.criticalCount || 0),
            warningCount: Number(data.warningCount || 0),
            clearedCount: Number(data.clearedCount || 0)
          }
        }
      } catch (err) {
        console.error('[AlarmStore] 拉取告警统计失败:', err)
      }
    },

    /**
     * 初始化告警数据拉取并启动 STOMP 全局订阅管道
     */
    initWebSocket() {
      if (this.initialized) return
      this.initialized = true

      // 1. 初始化拉取当前已有告警态势
      this.fetchActiveAlarms()
      this.fetchStats()

      // 2. 订阅后端 STOMP 广播通道 /topic/alarms
      this.wsSub = stompManager.subscribe('/topic/alarms', (payload) => {
        this.handleAlarmPush(payload)
      })
    },

    /**
     * 处理服务端 STOMP 推送的告警报文
     */
    handleAlarmPush(payload) {
      if (!payload || !payload.action) return

      console.log('[AlarmStore] 收到 STOMP 告警推屏事件:', payload)

      if (payload.action === 'TRIGGERED' || payload.action === 'ESCALATED') {
        const existingIdx = this.activeAlarms.findIndex(a => a.alarmId === payload.alarmId)
        if (existingIdx >= 0) {
          // 存在则更新指标与等级
          this.activeAlarms.splice(existingIdx, 1, payload)
        } else {
          // 不存在则首位追加
          this.activeAlarms.unshift(payload)
        }
        this.alarmStats.activeTotal = this.activeAlarms.length

        // 声光通知提醒 (CRITICAL 严重告警红弹窗与工业蜂鸣警报)
        if (payload.alarmLevel === 'CRITICAL') {
          this.alarmStats.criticalCount++
          alarmAudio.playCritical()
          ElNotification({
            title: '🚨【动环严重故障告警】',
            message: `${payload.roomName || ''} 机柜 [${payload.rackCode}] 发生严重超温！\n指标: ${payload.metricValue} | 摘要: ${payload.rcaSummary || '温度严重超限'}`,
            type: 'error',
            duration: 8000,
            position: 'top-right'
          })
        } else {
          this.alarmStats.warningCount++
          alarmAudio.playWarning()
          ElNotification({
            title: '⚠️【动环越限预警】',
            message: `${payload.roomName || ''} 机柜 [${payload.rackCode}] 动环越限：${payload.metricValue}`,
            type: 'warning',
            duration: 5000,
            position: 'top-right'
          })
        }
      } else if (payload.action === 'CLEARED') {
        // 从活动告警池移除
        this.activeAlarms = this.activeAlarms.filter(a => a.alarmId !== payload.alarmId)
        this.alarmStats.activeTotal = this.activeAlarms.length
        this.alarmStats.clearedCount = (this.alarmStats.clearedCount || 0) + 1

        ElNotification({
          title: '✅【动环告警自动消除】',
          message: `机柜 [${payload.rackCode}] 动环指标已恢复安全阈值区间，告警自动消除。`,
          type: 'success',
          duration: 4000,
          position: 'top-right'
        })
      }
    },

    /**
     * 人工关闭告警
     */
    async handleCloseAlarm(alarmId) {
      await closeAlarm(alarmId)
      this.activeAlarms = this.activeAlarms.filter(a => a.alarmId !== alarmId)
      this.alarmStats.activeTotal = this.activeAlarms.length
      this.fetchStats()
    },

    /**
     * 标记误报
     */
    async handleMarkFalsePositive(alarmId) {
      await markFalsePositive(alarmId)
      this.activeAlarms = this.activeAlarms.filter(a => a.alarmId !== alarmId)
      this.alarmStats.activeTotal = this.activeAlarms.length
      this.fetchStats()
    }
  }
})
