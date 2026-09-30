<template>
  <div class="screen-root" ref="screenRootRef">
    <!-- 1. 顶部数字化中控导航条 -->
    <header class="screen-header">
      <div class="header-left">
        <div class="logo-box" @click="goConsole">
          <span class="logo-icon">⚡</span>
          <span class="logo-text">SMART-IDC</span>
        </div>
        <div class="header-sub">
          <span class="header-badge font-mono">DIGITAL TWIN // V2.5</span>
          <span class="header-title">数据中心动环数字孪生指挥大屏</span>
        </div>
      </div>

      <div class="header-center">
        <span class="clock-time font-mono">{{ currentTimeStr }}</span>
        <span class="clock-sec font-mono">.{{ currentMilliStr }}</span>
      </div>

      <div class="header-right">
        <div class="tenant-pill">
          <span class="pill-dot"></span>
          <span class="pill-text">{{ tenantStore.currentTenantId === '000000' ? '智维云-自营机房' : '华东算力中心 (托管租户)' }}</span>
        </div>

        <!-- 告警蜂鸣开关按钮 -->
        <el-button
          size="small"
          :type="isMuted ? 'info' : 'warning'"
          plain
          class="ctrl-btn"
          @click="toggleAudioMute"
        >
          {{ isMuted ? '🔇 告警蜂鸣 (关)' : '🔊 告警蜂鸣 (开)' }}
        </el-button>

        <!-- 全屏模式切换 -->
        <el-button size="small" type="primary" plain class="ctrl-btn" @click="toggleFullscreen">
          {{ isFullscreen ? '🗗 退出全屏' : '⛶ 全屏模式' }}
        </el-button>

        <!-- 返回控制台 -->
        <el-button size="small" type="info" plain class="ctrl-btn" @click="goConsole">
          返回控制台 ➔
        </el-button>
      </div>
    </header>

    <!-- 顶栏严重告警声光通报横幅 (点击快速聚焦机柜与排障抽屉) -->
    <transition name="fade">
      <div v-if="topCriticalAlarm" class="alarm-beacon-banner" @click="handleFocusAlarm(topCriticalAlarm)">
        <span class="beacon-siren">🚨</span>
        <span class="beacon-tag font-mono">[动环越限响应]</span>
        <span class="beacon-text">
          机柜 <strong>{{ topCriticalAlarm.rackCode }}</strong> 触发 {{ topCriticalAlarm.alarmType || '动环告警' }}：
          越限采样 <span class="text-danger-val">{{ topCriticalAlarm.metricValue }}</span>
          <span v-if="topCriticalAlarm.rcaSummary">({{ topCriticalAlarm.rcaSummary }})</span>
        </span>
        <span class="beacon-action">立即排障诊断 ➔</span>
      </div>
    </transition>

    <!-- 2. 主体态势大屏视区 (三栏自适应工业布局) -->
    <main class="screen-body">
      <!-- 左侧：动力与能耗看板 -->
      <aside class="side-panel left-panel">
        <PueGaugeChart
          :pue-value="pueVal"
          :it-power="totalItPower"
          :cooling-power="coolingPower"
        />

        <PowerTrendChart
          :current-power="totalItPower"
        />

        <!-- 动力母线供电质量卡片 -->
        <div class="power-quality-card">
          <div class="pq-header">
            <span class="pq-title">⚡ 动力母线配电质量监控</span>
            <el-tag size="small" type="success" effect="plain">市电供电中</el-tag>
          </div>
          <div class="pq-grid font-mono">
            <div class="pq-item">
              <span class="pq-lbl">三相平均电压</span>
              <span class="pq-num">{{ avgVoltage.toFixed(1) }} <small>V</small></span>
            </div>
            <div class="pq-item">
              <span class="pq-lbl">母线总负载电流</span>
              <span class="pq-num text-cyan">{{ totalCurrent.toFixed(1) }} <small>A</small></span>
            </div>
            <div class="pq-item">
              <span class="pq-lbl">功率因数 (PF)</span>
              <span class="pq-num text-green">0.98</span>
            </div>
            <div class="pq-item">
              <span class="pq-lbl">电网频率</span>
              <span class="pq-num">50.0 <small>Hz</small></span>
            </div>
          </div>
        </div>
      </aside>

      <!-- 中央：核心 2D 数字孪生拓扑阵列 -->
      <section class="center-stage">
        <RoomTopologyGrid
          :room-name="currentRoomName"
          :rack-list="combinedRackList"
          :focused-rack-code="focusedRackCode"
          @select-rack="onSelectRack"
        />

        <!-- 下方机房环境综合概览跑道 -->
        <div class="bottom-kpi-runway font-mono">
          <div class="kpi-block">
            <span class="kpi-name">管辖机柜</span>
            <strong class="kpi-value text-cyan">{{ rackCount }} <small>台</small></strong>
          </div>
          <div class="kpi-divider"></div>
          <div class="kpi-block">
            <span class="kpi-name">已占用 U 位</span>
            <strong class="kpi-value">{{ totalUsedU }} / {{ totalCapU }} <small>U</small></strong>
          </div>
          <div class="kpi-divider"></div>
          <div class="kpi-block">
            <span class="kpi-name">平均机房温度</span>
            <strong class="kpi-value" :style="{ color: avgTempColor }">{{ avgRoomTemp.toFixed(1) }} <small>℃</small></strong>
          </div>
          <div class="kpi-divider"></div>
          <div class="kpi-block">
            <span class="kpi-name">平均相对湿度</span>
            <strong class="kpi-value text-cyan">{{ avgRoomHumidity.toFixed(1) }} <small>%RH</small></strong>
          </div>
          <div class="kpi-divider"></div>
          <div class="kpi-block">
            <span class="kpi-name">当前越限告警</span>
            <strong class="kpi-value" :class="{ 'text-danger': activeAlarms.length > 0 }">
              {{ activeAlarms.length }} <small>起</small>
            </strong>
          </div>
        </div>
      </section>

      <!-- 右侧：微环境与告警速报流 -->
      <aside class="side-panel right-panel">
        <ThermalDistChart
          :cold-count="thermalDistribution.cold"
          :warm-count="thermalDistribution.warm"
          :hot-count="thermalDistribution.hot"
        />

        <AlarmLiveList
          :active-alarms="activeAlarms"
          @select-rack="focusRack"
          @focus-alarm="handleFocusAlarm"
        />

        <!-- 空间容量与承载统计卡片 -->
        <div class="capacity-summary-card">
          <div class="cap-header">
            <span class="cap-title">📦 机房空间与算力负荷率</span>
            <span class="cap-rate font-mono">{{ spaceUsageRate }}%</span>
          </div>
          <el-progress
            :percentage="spaceUsageRate"
            :status="spaceUsageRate >= 80 ? 'exception' : spaceUsageRate >= 50 ? 'warning' : 'success'"
            :stroke-width="10"
          />
          <div class="cap-details font-mono">
            <span>额定总供电: {{ totalPowerRating.toFixed(1) }} kVA</span>
            <span>负荷率: {{ powerLoadRate }}%</span>
          </div>
        </div>
      </aside>
    </main>

    <!-- 3. 机柜深度排障与资产画像抽屉 -->
    <RackDiagnosisDrawer
      v-model="drawerVisible"
      :rack="selectedRack"
      :selected-alarm-id="selectedAlarmId"
      @alarm-cleared="onAlarmCleared"
      @alarm-false-positive="onAlarmFalsePositive"
      @create-ticket="onCreateTicket"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useTenantStore } from '@/stores/tenant'
import { useAlarmStore } from '@/stores/alarm'
import { listRacks } from '@/api/rack'
import { getLatestTelemetry } from '@/api/telemetry'
import { stompManager } from '@/utils/websocket'
import { alarmAudio } from '@/utils/alarmAudio'
import PueGaugeChart from './components/PueGaugeChart.vue'
import PowerTrendChart from './components/PowerTrendChart.vue'
import ThermalDistChart from './components/ThermalDistChart.vue'
import AlarmLiveList from './components/AlarmLiveList.vue'
import RoomTopologyGrid from './components/RoomTopologyGrid.vue'
import RackDiagnosisDrawer from './components/RackDiagnosisDrawer.vue'

const router = useRouter()
const tenantStore = useTenantStore()
const alarmStore = useAlarmStore()

const screenRootRef = ref(null)
const isFullscreen = ref(false)

// 基础机柜台账与遥测映射表
const rawRacks = ref([])
const telemetryMap = ref({}) // key: rackCode => TelemetryMetric
let clockTimer = null
const currentTimeStr = ref('')
const currentMilliStr = ref('000')

// STOMP 订阅句柄保存，退出时优雅取消
const stompSubscriptions = []

// 计算属性：当前机房名称
const currentRoomName = computed(() => {
  if (rawRacks.value.length > 0) {
    return rawRacks.value[0].roomName
  }
  return tenantStore.currentTenantId === '000000' ? '华东01-A区机房' : '华东02-B区机房'
})

// 组合机柜列表（合并实时物联指标）
const combinedRackList = computed(() => {
  return rawRacks.value.map(r => {
    const t = telemetryMap.value[r.rackCode] || null
    return {
      ...r,
      telemetry: t
    }
  })
})

const rackCount = computed(() => rawRacks.value.length)

const totalUsedU = computed(() => {
  return rawRacks.value.reduce((acc, cur) => acc + (cur.usedU || 0), 0)
})

const totalCapU = computed(() => {
  return rawRacks.value.reduce((acc, cur) => acc + (cur.totalU || 42), 0)
})

const spaceUsageRate = computed(() => {
  if (totalCapU.value === 0) return 0
  return Math.round((totalUsedU.value / totalCapU.value) * 100)
})

const totalPowerRating = computed(() => {
  return rawRacks.value.reduce((acc, cur) => acc + Number(cur.powerRating || 5), 0)
})

// 实时 IT 设备总功耗
const totalItPower = computed(() => {
  let p = 0
  for (const r of combinedRackList.value) {
    if (r.telemetry?.powerKw) {
      p += Number(r.telemetry.powerKw)
    }
  }
  return p > 0 ? p : (tenantStore.currentTenantId === '000000' ? 8.45 : 3.41)
})

// 动环公摊能耗（精密空调与照明配电损耗）
const coolingPower = computed(() => {
  return Number((totalItPower.value * 0.28).toFixed(2))
})

// 实时 PUE 动态推演
const pueVal = computed(() => {
  if (totalItPower.value === 0) return 1.25
  const val = 1.0 + (coolingPower.value / totalItPower.value)
  return Math.min(1.85, Math.max(1.15, Number(val.toFixed(2))))
})

const powerLoadRate = computed(() => {
  if (totalPowerRating.value === 0) return 0
  return Math.round((totalItPower.value / totalPowerRating.value) * 100)
})

// 平均电压
const avgVoltage = computed(() => {
  let vSum = 0
  let count = 0
  for (const r of combinedRackList.value) {
    if (r.telemetry?.voltage) {
      vSum += Number(r.telemetry.voltage)
      count++
    }
  }
  return count > 0 ? vSum / count : 220.0
})

// 总电流
const totalCurrent = computed(() => {
  let iSum = 0
  for (const r of combinedRackList.value) {
    if (r.telemetry?.currentAmp) {
      iSum += Number(r.telemetry.currentAmp)
    }
  }
  return iSum > 0 ? iSum : 38.5
})

// 平均机房温度
const avgRoomTemp = computed(() => {
  let tSum = 0
  let count = 0
  for (const r of combinedRackList.value) {
    if (r.telemetry?.temperature) {
      tSum += Number(r.telemetry.temperature)
      count++
    }
  }
  return count > 0 ? tSum / count : 22.5
})

const avgTempColor = computed(() => {
  if (avgRoomTemp.value >= 30.0) return '#EF4444'
  if (avgRoomTemp.value >= 26.0) return '#F59E0B'
  return '#10B981'
})

// 平均相对湿度
const avgRoomHumidity = computed(() => {
  let hSum = 0
  let count = 0
  for (const r of combinedRackList.value) {
    if (r.telemetry?.humidity) {
      hSum += Number(r.telemetry.humidity)
      count++
    }
  }
  return count > 0 ? hSum / count : 50.0
})

// 温区分布统计
const thermalDistribution = computed(() => {
  let cold = 0
  let warm = 0
  let hot = 0

  for (const r of combinedRackList.value) {
    const t = r.telemetry?.temperature
    if (t == null) {
      cold++
    } else if (t >= 30.0) {
      hot++
    } else if (t >= 26.0) {
      warm++
    } else {
      cold++
    }
  }
  return { cold, warm, hot }
})

const activeAlarms = computed(() => {
  return alarmStore.activeAlarms || []
})

const topCriticalAlarm = computed(() => {
  return activeAlarms.value.find(a => a.alarmLevel === 'CRITICAL') || (activeAlarms.value.length > 0 ? activeAlarms.value[0] : null)
})

// Web Audio 声光警报控制
const isMuted = ref(alarmAudio.isMuted)
function toggleAudioMute() {
  isMuted.value = alarmAudio.toggleMute()
  if (isMuted.value) {
    ElMessage.warning('告警蜂鸣音效已关闭 (静音模式)')
  } else {
    ElMessage.success('告警蜂鸣音效已开启')
    alarmAudio.testAudio()
  }
}

// 全屏控制
function toggleFullscreen() {
  if (!document.fullscreenElement) {
    if (screenRootRef.value?.requestFullscreen) {
      screenRootRef.value.requestFullscreen().then(() => {
        isFullscreen.value = true
      }).catch(err => {
        console.warn('全屏受限:', err)
      })
    } else if (document.documentElement.requestFullscreen) {
      document.documentElement.requestFullscreen().then(() => {
        isFullscreen.value = true
      })
    }
  } else {
    if (document.exitFullscreen) {
      document.exitFullscreen().then(() => {
        isFullscreen.value = false
      })
    }
  }
}

// 返回主控台
function goConsole() {
  router.push('/dashboard')
}

// 时钟跳动
function updateClock() {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  const hh = String(now.getHours()).padStart(2, '0')
  const mm = String(now.getMinutes()).padStart(2, '0')
  const ss = String(now.getSeconds()).padStart(2, '0')
  const ms = String(now.getMilliseconds()).padStart(3, '0')

  currentTimeStr.value = `${y}-${m}-${d} ${hh}:${mm}:${ss}`
  currentMilliStr.value = ms
}

// 加载机柜真实台账并获取最新遥测数据
async function loadRackData() {
  try {
    const list = await listRacks({})
    rawRacks.value = list || []

    for (const r of rawRacks.value) {
      try {
        const metric = await getLatestTelemetry(r.rackCode)
        if (metric) {
          telemetryMap.value[r.rackCode] = metric
        }
      } catch {}
    }

    setupStompSubscriptions()
  } catch (err) {
    console.error('加载机柜大屏资产失败:', err)
  }
}

// 建立 STOMP 订阅通道
function setupStompSubscriptions() {
  if (!stompManager) return

  for (const r of rawRacks.value) {
    const sub = stompManager.subscribe(`/topic/rack-telemetry/${r.rackCode}`, data => {
      if (data && data.rackCode) {
        telemetryMap.value[data.rackCode] = data
      }
    })
    stompSubscriptions.push(sub)
  }
}

// 排障抽屉与拓扑双向联动状态
const drawerVisible = ref(false)
const selectedRack = ref(null)
const selectedAlarmId = ref(null)
const focusedRackCode = ref('')
let focusTimer = null

function buildRackProfile(rack) {
  const matchedDbRack = rawRacks.value.find(r => r.rackCode === rack.rackCode) || {}
  const t = telemetryMap.value[rack.rackCode] || rack.telemetry || {}
  const codeMap = { 'A-01': 1, 'A-02': 2, 'A-03': 3, 'A-04': 4, 'A-05': 5, 'A-06': 6, 'B-01': 7, 'B-02': 8 }
  const rackId = rack.rackId || matchedDbRack.rackId || codeMap[rack.rackCode] || 1

  return {
    ...matchedDbRack,
    ...rack,
    rackId: rackId,
    rackCode: rack.rackCode,
    rackName: rack.rackName || matchedDbRack.rackName || `${rack.rackCode} 机柜`,
    roomName: rack.roomName || matchedDbRack.roomName || currentRoomName.value,
    tenantId: rack.tenantId || matchedDbRack.tenantId || tenantStore.currentTenantId,
    temp: t.temperature ?? (rack.rackCode === 'B-01' ? 28.5 : 24.2),
    humidity: t.humidity ?? 48.2,
    voltage: t.voltage ?? 220.0,
    current: t.currentAmp ?? 15.2,
    powerKw: t.powerKw ?? (rack.rackCode === 'B-01' ? 3.4 : 1.5),
    usedU: rack.usedU ?? matchedDbRack.usedU ?? 16,
    uHeight: rack.totalU ?? rack.uHeight ?? matchedDbRack.uHeight ?? 42,
    maxPowerKw: rack.powerRating ?? rack.maxPowerKw ?? matchedDbRack.maxPowerKw ?? 8.0
  }
}

function onSelectRack(rack) {
  console.log('[Screen] 点击机柜展开深度排障画像:', rack.rackCode)
  selectedRack.value = buildRackProfile(rack)
  focusedRackCode.value = rack.rackCode
  selectedAlarmId.value = null
  drawerVisible.value = true
}

function handleFocusAlarm(alarm) {
  if (!alarm) return
  console.log('[Screen] 告警反向聚焦机柜:', alarm.rackCode)
  focusedRackCode.value = alarm.rackCode
  selectedAlarmId.value = alarm.alarmId

  const matched = combinedRackList.value.find(r => r.rackCode === alarm.rackCode)
  if (matched) {
    selectedRack.value = buildRackProfile(matched)
  } else {
    selectedRack.value = buildRackProfile({
      rackId: alarm.rackId || (alarm.rackCode === 'A-01' ? 1 : 7),
      rackCode: alarm.rackCode,
      roomName: alarm.roomName || currentRoomName.value,
      temp: parseFloat(alarm.metricValue) || 35.8,
      usedU: 16,
      totalU: 42,
      maxPowerKw: 8.0,
      powerKw: 4.8
    })
  }
  drawerVisible.value = true

  if (focusTimer) clearTimeout(focusTimer)
  focusTimer = setTimeout(() => {
    focusedRackCode.value = ''
  }, 3500)
}

function focusRack(rackCode) {
  const matchedAlarm = activeAlarms.value.find(a => a.rackCode === rackCode)
  if (matchedAlarm) {
    handleFocusAlarm(matchedAlarm)
  } else {
    const matched = combinedRackList.value.find(r => r.rackCode === rackCode)
    if (matched) {
      onSelectRack(matched)
    } else {
      onSelectRack({ rackCode })
    }
  }
}

function onAlarmCleared(alarmId) {
  alarmStore.activeAlarms = alarmStore.activeAlarms.filter(a => a.alarmId !== alarmId)
  alarmStore.fetchStats()
}

function onAlarmFalsePositive(alarmId) {
  alarmStore.activeAlarms = alarmStore.activeAlarms.filter(a => a.alarmId !== alarmId)
  alarmStore.fetchStats()
}

function onCreateTicket(draft) {
  console.log('转排障工单草稿:', draft)
}

onMounted(() => {
  alarmStore.initWebSocket()
  loadRackData()

  clockTimer = setInterval(updateClock, 100)

  // 监听全屏切换事件
  document.addEventListener('fullscreenchange', () => {
    isFullscreen.value = !!document.fullscreenElement
  })
})

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
  // 清理 STOMP 订阅
  stompSubscriptions.forEach(sub => sub.unsubscribe())
})
</script>

<style scoped>
.screen-root {
  width: 100vw;
  min-height: 100vh;
  background: radial-gradient(circle at 50% 20%, #0F172A 0%, #020617 100%);
  color: #F8FAFC;
  display: flex;
  flex-direction: column;
  overflow-x: hidden;
  user-select: none;
}

/* 顶部数字化中控 Header */
.screen-header {
  height: 60px;
  background: rgba(15, 23, 42, 0.95);
  border-bottom: 1.5px solid rgba(56, 189, 248, 0.35);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.6);
  padding: 0 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: relative;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.logo-box {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.logo-icon {
  font-size: 22px;
  color: #38BDF8;
  filter: drop-shadow(0 0 8px #38BDF8);
}

.logo-text {
  font-size: 18px;
  font-weight: 900;
  letter-spacing: 1.5px;
  background: linear-gradient(90deg, #38BDF8, #818CF8);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.header-sub {
  display: flex;
  align-items: center;
  gap: 8px;
  border-left: 1px solid rgba(255, 255, 255, 0.15);
  padding-left: 16px;
}

.header-badge {
  font-size: 10px;
  background: rgba(56, 189, 248, 0.15);
  color: #38BDF8;
  padding: 1px 6px;
  border-radius: 3px;
  border: 1px solid rgba(56, 189, 248, 0.3);
}

.header-title {
  font-size: 14px;
  font-weight: 600;
  color: #E2E8F0;
  letter-spacing: 0.5px;
}

.header-center {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: baseline;
}

.clock-time {
  font-size: 18px;
  font-weight: 700;
  color: #38BDF8;
  letter-spacing: 1px;
}

.clock-sec {
  font-size: 12px;
  color: #64748B;
  margin-left: 2px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.tenant-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  padding: 4px 10px;
  border-radius: 16px;
  font-size: 12px;
  color: #CBD5E1;
}

.pill-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10B981;
  box-shadow: 0 0 6px #10B981;
}

.ctrl-btn {
  background: rgba(30, 41, 59, 0.6);
  border-color: rgba(56, 189, 248, 0.3);
}

/* 主视区布局 */
.screen-body {
  flex: 1;
  display: grid;
  grid-template-columns: 290px 1fr 310px;
  gap: 16px;
  padding: 16px;
}

.side-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.center-stage {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* 动力母线指标 */
.power-quality-card {
  background: rgba(15, 23, 42, 0.75);
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-radius: 8px;
  padding: 12px 14px;
  backdrop-filter: blur(10px);
}

.pq-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.pq-title {
  font-size: 13px;
  font-weight: 600;
  color: #E2E8F0;
}

.pq-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.pq-item {
  display: flex;
  flex-direction: column;
  background: rgba(30, 41, 59, 0.5);
  padding: 8px;
  border-radius: 4px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.pq-lbl {
  font-size: 10px;
  color: #94A3B8;
  margin-bottom: 2px;
}

.pq-num {
  font-size: 15px;
  font-weight: 700;
  color: #F8FAFC;
}

.pq-num small {
  font-size: 10px;
  color: #64748B;
  font-weight: normal;
}

/* 底部 KPI 跑道 */
.bottom-kpi-runway {
  background: rgba(15, 23, 42, 0.85);
  border: 1px solid rgba(56, 189, 248, 0.25);
  border-radius: 8px;
  padding: 10px 20px;
  display: flex;
  justify-content: space-around;
  align-items: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.kpi-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.kpi-name {
  font-size: 11px;
  color: #94A3B8;
}

.kpi-value {
  font-size: 17px;
  font-weight: 800;
  color: #F8FAFC;
}

.kpi-value small {
  font-size: 11px;
  font-weight: normal;
}

.kpi-divider {
  width: 1px;
  height: 28px;
  background: rgba(255, 255, 255, 0.1);
}

/* 空间负荷 */
.capacity-summary-card {
  background: rgba(15, 23, 42, 0.75);
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-radius: 8px;
  padding: 12px 14px;
  backdrop-filter: blur(10px);
}

.cap-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.cap-title {
  font-size: 13px;
  font-weight: 600;
  color: #E2E8F0;
}

.cap-rate {
  font-size: 14px;
  font-weight: bold;
  color: #38BDF8;
}

.cap-details {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #94A3B8;
  margin-top: 8px;
}

.text-cyan {
  color: #38BDF8 !important;
}

.text-green {
  color: #10B981 !important;
}

.text-danger {
  color: #EF4444 !important;
}

/* 顶栏严重告警呼吸光晕跑马横幅 */
.alarm-beacon-banner {
  background: linear-gradient(90deg, rgba(220, 38, 38, 0.95) 0%, rgba(185, 28, 28, 0.98) 50%, rgba(220, 38, 38, 0.95) 100%);
  color: #ffffff;
  padding: 8px 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  cursor: pointer;
  box-shadow: 0 4px 18px rgba(239, 68, 68, 0.6);
  border-bottom: 1px solid #f87171;
  animation: banner-glow 2s ease-in-out infinite;
  z-index: 9;
}

@keyframes banner-glow {
  0%, 100% { box-shadow: 0 4px 15px rgba(239, 68, 68, 0.5); }
  50% { box-shadow: 0 4px 25px rgba(239, 68, 68, 0.9); }
}

.beacon-siren {
  font-size: 16px;
  animation: pulse-siren 0.8s infinite alternate;
}

@keyframes pulse-siren {
  0% { transform: scale(1); }
  100% { transform: scale(1.25); }
}

.beacon-tag {
  background: #7f1d1d;
  font-size: 11px;
  font-weight: bold;
  padding: 2px 8px;
  border-radius: 4px;
  border: 1px solid #ef4444;
}

.beacon-text {
  font-size: 13px;
  letter-spacing: 0.3px;
}

.beacon-text strong {
  color: #fef08a;
  font-family: monospace;
}

.text-danger-val {
  color: #fef08a;
  font-weight: 700;
}

.beacon-action {
  font-size: 12px;
  background: rgba(255, 255, 255, 0.2);
  padding: 3px 10px;
  border-radius: 4px;
  font-weight: 600;
  transition: all 0.2s;
}

.beacon-action:hover {
  background: rgba(255, 255, 255, 0.35);
}

.drill-btn {
  border-color: #ef4444 !important;
  color: #ef4444 !important;
  font-weight: 600;
  box-shadow: 0 0 8px rgba(239, 68, 68, 0.2);
}

.drill-btn:hover {
  background: rgba(239, 68, 68, 0.15) !important;
  box-shadow: 0 0 12px rgba(239, 68, 68, 0.4);
}
</style>
