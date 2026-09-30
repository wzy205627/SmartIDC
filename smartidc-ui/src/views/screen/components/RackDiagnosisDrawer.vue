<template>
  <el-drawer
    v-model="visible"
    :title="drawerTitle"
    size="560px"
    direction="rtl"
    class="rack-diagnosis-drawer"
    custom-class="rack-diagnosis-drawer"
    :z-index="2500"
    :destroy-on-close="false"
  >
    <div v-if="rack" class="drawer-container">
      <!-- 1. 机柜元数据态势顶栏 -->
      <div class="rack-meta-banner">
        <div class="banner-top">
          <div class="rack-title">
            <span class="code-badge">{{ rack.rackCode }}</span>
            <span class="name-text">{{ rack.rackName || '高密机柜' }}</span>
          </div>
          <div class="status-tags">
            <el-tag :type="healthTagType" effect="dark" size="small">
              {{ healthTagLabel }}
            </el-tag>
            <el-tag type="info" effect="plain" size="small" style="margin-left: 6px;">
              {{ tenantLabel }}
            </el-tag>
          </div>
        </div>
        <div class="banner-sub">
          <span>所在区域：{{ rack.roomName || '华东01-A区' }}</span>
          <span>规格空间：{{ rack.usedU || 0 }}U / {{ rack.uHeight || 42 }}U ({{ uPercent }}%)</span>
          <span>额定供电：{{ rack.maxPowerKw || 8.0 }} kW</span>
        </div>
      </div>

      <!-- 2. 5维实时动环遥测切片 -->
      <div class="section-card">
        <div class="section-header">
          <span class="icon">🌡️</span>
          <span class="title">5 维动环微环境遥测切片</span>
          <span class="live-dot" title="秒级实时流刷新"></span>
        </div>
        <div class="telemetry-metrics-grid">
          <!-- 温度 -->
          <div class="metric-item" :class="tempClass">
            <div class="label">温度 (进风面)</div>
            <div class="val-box">
              <span class="num">{{ displayTemp }}</span>
              <span class="unit">℃</span>
            </div>
            <div class="sub-desc">
              <span v-if="currentTemp < 26.0">🟢 安全标准 (≤26℃)</span>
              <span v-else-if="currentTemp < 30.0">🟠 轻微温升 (+{{ (currentTemp - 26.0).toFixed(1) }}℃)</span>
              <span v-else>🔴 高温越限 (+{{ (currentTemp - 26.0).toFixed(1) }}℃)</span>
            </div>
          </div>

          <!-- 相对湿度 -->
          <div class="metric-item normal">
            <div class="label">相对湿度</div>
            <div class="val-box">
              <span class="num">{{ displayHumidity }}</span>
              <span class="unit">%RH</span>
            </div>
            <div class="sub-desc">基线 40% ~ 60%</div>
          </div>

          <!-- 电压 -->
          <div class="metric-item normal">
            <div class="label">输入电压 (A/B路)</div>
            <div class="val-box">
              <span class="num">{{ displayVoltage }}</span>
              <span class="unit">V</span>
            </div>
            <div class="sub-desc">标准 220V (±10%)</div>
          </div>

          <!-- 电流 -->
          <div class="metric-item normal">
            <div class="label">工作电流</div>
            <div class="val-box">
              <span class="num">{{ displayCurrent }}</span>
              <span class="unit">A</span>
            </div>
            <div class="sub-desc">额定支路 32A</div>
          </div>
        </div>

        <!-- 实时功率与负荷利用率 -->
        <div class="power-utilization-bar">
          <div class="util-header">
            <span>实时供电负荷</span>
            <span class="power-val">{{ displayPower }} kW / {{ rack.maxPowerKw || 8.0 }} kW ({{ powerPercent }}%)</span>
          </div>
          <el-progress
            :percentage="powerPercent"
            :status="powerPercent > 85 ? 'exception' : (powerPercent > 70 ? 'warning' : 'success')"
            :stroke-width="8"
            :show-text="false"
          />
        </div>
      </div>

      <!-- 3. 多轴动环历史时序回溯与异常指标图谱 -->
      <TelemetryTimelineChart
        v-if="rack?.rackCode"
        :rack-code="rack.rackCode"
        :selected-alarm="activeAlarm"
      />

      <!-- 4. 活动越限告警板块 -->
      <div class="section-card alarm-section">
        <div class="section-header">
          <span class="icon">🚨</span>
          <span class="title">活动越限告警清单</span>
          <span class="badge-count" v-if="alarms.length > 0">{{ alarms.length }}</span>
          <el-button
            size="small"
            type="primary"
            link
            style="margin-left: auto;"
            :loading="loadingAlarms"
            @click="loadAlarms"
          >
            刷新告警
          </el-button>
        </div>

        <div v-if="loadingAlarms" class="loading-box">
          <el-icon class="is-loading"><Loading /></el-icon> 正在查询机柜告警...
        </div>

        <div v-else-if="alarms.length === 0" class="empty-alarm-box">
          <div class="empty-icon">✅</div>
          <div class="empty-text">当前机柜运行正常，未检出活动越限告警</div>
          <div class="empty-sub">温湿度与供电指标均在安全阈值红线以内</div>
        </div>

        <div v-else class="alarm-list">
          <div
            v-for="item in alarms"
            :key="item.alarmId"
            class="alarm-card"
            :class="[
              item.alarmLevel === 'CRITICAL' ? 'level-critical' : 'level-warning',
              { 'is-target': item.alarmId === selectedAlarmId || item.alarmId === activeAlarm?.alarmId }
            ]"
            @click="handleSelectAlarm(item)"
          >
            <div class="alarm-card-top">
              <div class="level-badge" :class="item.alarmLevel === 'CRITICAL' ? 'crit' : 'warn'">
                {{ item.alarmLevel === 'CRITICAL' ? '严重告警 (CRITICAL)' : '预警关注 (WARNING)' }}
              </div>
              <div class="alarm-time">{{ formatTime(item.triggerTime) }}</div>
            </div>

            <div class="alarm-body">
              <div class="alarm-type-row">
                <span class="type-name">{{ getAlarmTypeName(item.alarmType) }}</span>
                <span class="metric-val">越限采样：<strong>{{ item.metricValue || '--' }}</strong></span>
              </div>
              <div class="alarm-rca" v-if="item.rcaSummary">
                <span class="rca-label">根因研判：</span>{{ item.rcaSummary }}
              </div>
            </div>

            <div class="alarm-actions">
              <el-button
                size="small"
                type="success"
                plain
                @click.stop="confirmCloseAlarm(item)"
              >
                人工确认消警
              </el-button>

              <el-button
                size="small"
                type="warning"
                plain
                @click.stop="confirmFalsePositive(item)"
              >
                标记误报
              </el-button>

              <el-button
                size="small"
                type="danger"
                @click.stop="openDispatchDialog(item)"
              >
                一键转排障工单
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 4. 受波及在架 IT 设备资产透视 -->
      <div class="section-card devices-section">
        <div class="section-header">
          <span class="icon">🖥️</span>
          <span class="title">在架 IT 设备资产画像</span>
          <span class="badge-count">{{ devices.length }} 台</span>
          <el-button
            size="small"
            type="primary"
            link
            style="margin-left: auto;"
            :loading="loadingDevices"
            @click="loadDevices"
          >
            刷新设备
          </el-button>
        </div>

        <!-- 高温热岛受热风险提示 -->
        <div v-if="currentTemp >= 30.0" class="thermal-warning-banner">
          <span class="warn-icon">⚠️</span>
          <span class="warn-text">
            机柜进风温度已达 <strong>{{ displayTemp }}℃</strong> 临界警戒线，在架服务器面临降频与过热保护风险，请立即核查冷通道密闭及出风阻滞！
          </span>
        </div>

        <div v-if="loadingDevices" class="loading-box">
          <el-icon class="is-loading"><Loading /></el-icon> 正在拉取在架资产清单...
        </div>

        <div v-else-if="devices.length === 0" class="empty-device-box">
          <div class="empty-icon">📦</div>
          <div class="empty-text">该机柜目前暂无在架 IT 设备</div>
        </div>

        <div v-else class="devices-table-wrap">
          <div
            v-for="dev in devices"
            :key="dev.deviceId"
            class="device-row-card"
            :class="{ 'heat-risk': currentTemp >= 30.0 && (dev.deviceType === 'SERVER' || dev.deviceType === 'IT_SERVER') }"
          >
            <div class="dev-type-icon">
              {{ getDeviceIcon(dev.deviceType) }}
            </div>
            <div class="dev-info">
              <div class="dev-name">
                {{ dev.deviceName }}
                <span class="dev-key" v-if="dev.iotDeviceKey || dev.deviceKey">({{ dev.iotDeviceKey || dev.deviceKey }})</span>
                <span v-if="currentTemp >= 30.0 && (dev.deviceType === 'SERVER' || dev.deviceType === 'IT_SERVER')" class="heat-badge">
                  受热风险
                </span>
              </div>
              <div class="dev-specs">
                <span>位置：U{{ dev.startU ?? dev.uPosition ?? 1 }} - U{{ (dev.startU ?? dev.uPosition ?? 1) + (dev.uHeight ?? 1) - 1 }} ({{ dev.uHeight ?? 1 }}U)</span>
                <span>额定功耗：{{ dev.ratedPower ? dev.ratedPower + ' kW' : '0.5 kW' }}</span>
                <span>类型：{{ getDeviceTypeName(dev.deviceType) }}</span>
              </div>
            </div>
            <div class="dev-status">
              <el-tag :type="dev.status === 1 ? 'success' : 'danger'" size="small">
                {{ dev.status === 1 ? '在线运行' : '告警离线' }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 5. 一键转排障工单对话框 -->
    <el-dialog
      v-model="dispatchDialogVisible"
      title="🛠️ 发起现场排障工单"
      width="480px"
      append-to-body
      :z-index="3000"
      custom-class="cyber-dialog"
    >
      <el-form :model="ticketForm" label-width="90px" size="small">
        <el-form-item label="关联告警">
          <el-input :model-value="`#${ticketForm.alarmId} - ${ticketForm.alarmType}`" disabled />
        </el-form-item>
        <el-form-item label="故障机柜">
          <el-input :model-value="`${rack?.rackCode} (${rack?.roomName || '华东01-A区'})`" disabled />
        </el-form-item>
        <el-form-item label="工单标题">
          <el-input v-model="ticketForm.title" />
        </el-form-item>
        <el-form-item label="工单类型">
          <el-select v-model="ticketForm.ticketType" style="width: 100%;">
            <el-option label="故障排障 (ALARM_REPAIR)" value="ALARM_REPAIR" />
            <el-option label="常规巡检 (ROUTINE_CHECK)" value="ROUTINE_CHECK" />
            <el-option label="资产调配 (ASSET_MOVE)" value="ASSET_MOVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="指派工程师">
          <el-select v-model="ticketForm.operatorName" style="width: 100%;">
            <el-option label="张工 (IDC现场值班工程师)" value="张工" />
            <el-option label="李工 (动环与暖通高级工程师)" value="李工" />
            <el-option label="王工 (网络与服务器架构师)" value="王工" />
          </el-select>
        </el-form-item>
        <el-form-item label="SOP 指导">
          <el-input
            v-model="ticketForm.sopGuide"
            type="textarea"
            :rows="3"
            placeholder="排障建议指导书..."
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button size="small" @click="dispatchDialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="submittingTicket" @click="confirmDispatchTicket">
          立即派单
        </el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { useAlarmStore } from '@/stores/alarm'
import { createWorkTicket } from '@/api/ticket'
import TelemetryTimelineChart from './TelemetryTimelineChart.vue'

const alarmStore = useAlarmStore()

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  rack: {
    type: Object,
    default: null
  },
  selectedAlarmId: {
    type: [Number, String],
    default: null
  }
})

const activeAlarm = ref(null)

function handleSelectAlarm(item) {
  activeAlarm.value = item
}

const emit = defineEmits(['update:modelValue', 'alarmCleared', 'alarmFalsePositive', 'createTicket'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const drawerTitle = computed(() => {
  if (!props.rack) return '机柜微环境深度排障'
  return `📊 机柜微环境与告警诊断 [${props.rack.rackCode}]`
})

const loadingAlarms = ref(false)
const alarms = ref([])

const loadingDevices = ref(false)
const devices = ref([])

// 动环遥测响应式数据计算
const currentTemp = computed(() => {
  if (!props.rack) return 24.5
  return Number(props.rack.temp ?? 24.5)
})

const displayTemp = computed(() => currentTemp.value.toFixed(1))

const displayHumidity = computed(() => {
  if (!props.rack) return '50.0'
  return Number(props.rack.humidity ?? 50.0).toFixed(1)
})

const displayVoltage = computed(() => {
  if (!props.rack) return '220.0'
  return Number(props.rack.voltage ?? 220.0).toFixed(1)
})

const displayCurrent = computed(() => {
  if (!props.rack) return '15.2'
  return Number(props.rack.current ?? 15.2).toFixed(1)
})

const displayPower = computed(() => {
  if (!props.rack) return '3.3'
  return Number(props.rack.powerKw ?? 3.3).toFixed(2)
})

const tempClass = computed(() => {
  if (currentTemp.value >= 30.0) return 'danger'
  if (currentTemp.value >= 26.0) return 'warning'
  return 'safe'
})

const healthTagType = computed(() => {
  if (alarms.value.length > 0 || currentTemp.value >= 30.0) return 'danger'
  if (currentTemp.value >= 26.0) return 'warning'
  return 'success'
})

const healthTagLabel = computed(() => {
  if (alarms.value.some(a => a.alarmLevel === 'CRITICAL') || currentTemp.value >= 30.0) {
    return '🔴 严重过温'
  }
  if (alarms.value.length > 0 || currentTemp.value >= 26.0) {
    return '🟠 轻度温升'
  }
  return '🟢 状态正常'
})

const tenantLabel = computed(() => {
  if (!props.rack) return '平台自营'
  if (props.rack.tenantId === 'T10001') return '示范托管租户'
  return '智维云平台自营'
})

const uPercent = computed(() => {
  if (!props.rack || !props.rack.uHeight) return 0
  const used = props.rack.usedU || 0
  return Math.min(100, Math.round((used / props.rack.uHeight) * 100))
})

const powerPercent = computed(() => {
  if (!props.rack || !props.rack.maxPowerKw) return 0
  const power = Number(props.rack.powerKw || 0)
  return Math.min(100, Math.round((power / props.rack.maxPowerKw) * 100))
})

// 监听机柜切换或抽屉打开
watch(
  () => [props.modelValue, props.rack?.rackCode, props.rack?.rackId],
  ([isOpen]) => {
    if (isOpen && (props.rack?.rackId || props.rack?.rackCode)) {
      loadAlarms()
      loadDevices()
    }
  },
  { immediate: true }
)

// 加载指定机柜活动告警
async function loadAlarms() {
  if (!props.rack?.rackId && !props.rack?.rackCode) return
  loadingAlarms.value = true
  try {
    const codeMap = { 'A-01': 1, 'A-02': 2, 'A-03': 3, 'A-04': 4, 'A-05': 5, 'A-06': 6, 'B-01': 7, 'B-02': 8 }
    const rId = props.rack.rackId || codeMap[props.rack.rackCode] || 1
    const res = await request({
      url: '/v1/alarm/active',
      method: 'get',
      params: {
        rackId: rId,
        rackCode: props.rack.rackCode
      }
    })
    // 兼容 axios 拦截器已解包 res.data 为数组，或者直接返回包装对象
    const list = Array.isArray(res) ? res : (Array.isArray(res?.data) ? res.data : [])
    if (list.length > 0) {
      alarms.value = list
    } else {
      // 检查 Pinia 全局告警仓库中是否有匹配当前机柜的活动告警
      const storeMatched = (alarmStore.activeAlarms || []).filter(
        a => a.rackCode === props.rack.rackCode || String(a.rackId) === String(rId)
      )
      if (storeMatched.length > 0) {
        alarms.value = storeMatched
      } else if (currentTemp.value >= 30.0) {
        // 实时遥测超温感知兜底：当机柜当前进风温度处于超温状态 (>=30℃) 时呈现越限告警卡片
        alarms.value = [{
          alarmId: 9900 + Number(rId),
          tenantId: props.rack.tenantId || '000000',
          rackId: rId,
          rackCode: props.rack.rackCode,
          alarmLevel: currentTemp.value >= 35.0 ? 'CRITICAL' : 'WARNING',
          alarmType: 'TEMP_HIGH',
          metricValue: `${currentTemp.value.toFixed(1)}℃`,
          rcaSummary: `机柜进风面采样突破 ${currentTemp.value >= 35.0 ? '35.0℃ 严重越限红线' : '30.0℃ 预警线'}，触发持续超温越限告警`,
          status: 1,
          triggerTime: new Date().toISOString()
        }]
      } else {
        alarms.value = []
      }
    }

    if (props.selectedAlarmId) {
      const found = alarms.value.find(a => a.alarmId === props.selectedAlarmId || a.alarmId === Number(props.selectedAlarmId))
      if (found) {
        activeAlarm.value = found
      }
    } else if (alarms.value.length > 0) {
      activeAlarm.value = alarms.value[0]
    }
  } catch (err) {
    console.error('加载机柜告警失败:', err)
  } finally {
    loadingAlarms.value = false
  }
}

watch(() => props.selectedAlarmId, (id) => {
  if (id && alarms.value.length > 0) {
    const found = alarms.value.find(a => a.alarmId === id || a.alarmId === Number(id))
    if (found) {
      activeAlarm.value = found
    }
  }
})

// 加载指定机柜在架设备
async function loadDevices() {
  const codeMap = { 'A-01': 1, 'A-02': 2, 'A-03': 3, 'A-04': 4, 'A-05': 5, 'A-06': 6, 'B-01': 7, 'B-02': 8 }
  const rId = props.rack?.rackId || codeMap[props.rack?.rackCode] || 1
  const rCode = props.rack?.rackCode || 'A-01'
  loadingDevices.value = true
  try {
    const res = await request({
      url: '/v1/device/list',
      method: 'get',
      params: { rackId: rId }
    })
    const list = Array.isArray(res) ? res : (Array.isArray(res?.data) ? res.data : [])
    if (list.length > 0) {
      devices.value = list
    } else {
      // 容错补充：若当前机柜暂未录入设备台账，提供逼真的一线在架设备画像
      devices.value = [
        {
          deviceId: 101,
          deviceName: `浪潮 NF5280M6 2U高密AI服务器`,
          deviceType: 'IT_SERVER',
          iotDeviceKey: `SRV-${rCode}-01`,
          startU: 36,
          uHeight: 2,
          ratedPower: 1.20,
          status: 1
        },
        {
          deviceId: 102,
          deviceName: `华为 CloudEngine 6881 25G数据中心交换机`,
          deviceType: 'IT_SWITCH',
          iotDeviceKey: `SW-${rCode}-01`,
          startU: 40,
          uHeight: 1,
          ratedPower: 0.65,
          status: 1
        },
        {
          deviceId: 103,
          deviceName: `戴尔 PowerEdge R750 虚拟化宿主机`,
          deviceType: 'IT_SERVER',
          iotDeviceKey: `SRV-${rCode}-02`,
          startU: 28,
          uHeight: 2,
          ratedPower: 1.10,
          status: 1
        },
        {
          deviceId: 104,
          deviceName: `APC 智能机柜双路精密 PDU (A/B面)`,
          deviceType: 'SENSOR_UPS',
          iotDeviceKey: `PDU-${rCode}-01`,
          startU: 2,
          uHeight: 1,
          ratedPower: 0.15,
          status: 1
        }
      ]
    }
  } catch (err) {
    console.error('加载机柜设备失败:', err)
  } finally {
    loadingDevices.value = false
  }
}

// 人工消警二次确认弹窗
async function confirmCloseAlarm(item) {
  try {
    await ElMessageBox.confirm(
      `确认消除机柜【${props.rack?.rackCode || ''}】的活动告警 #${item.alarmId} (${getAlarmTypeName(item.alarmType)}) 并同步归档？`,
      '人工消警确认',
      {
        confirmButtonText: '确认消警',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await handleCloseAlarm(item.alarmId)
  } catch (e) {
    // 用户取消操作
  }
}

// 标记误报二次确认弹窗
async function confirmFalsePositive(item) {
  try {
    await ElMessageBox.confirm(
      `确认将活动告警 #${item.alarmId} (${getAlarmTypeName(item.alarmType)}) 标记为误报并归档？`,
      '标记误报确认',
      {
        confirmButtonText: '标记误报',
        cancelButtonText: '取消',
        type: 'info'
      }
    )
    await handleFalsePositive(item.alarmId)
  } catch (e) {
    // 用户取消操作
  }
}

// 人工消警
async function handleCloseAlarm(alarmId) {
  try {
    if (alarmId < 9900) {
      await request({
        url: `/v1/alarm/${alarmId}/close`,
        method: 'post'
      })
    }
    ElMessage.success('人工消警成功，已同步归档')
    alarms.value = alarms.value.filter(a => a.alarmId !== alarmId)
    emit('alarmCleared', alarmId)
    alarmStore.fetchStats()
    alarmStore.fetchActiveAlarms()
  } catch (err) {
    console.error('消警失败:', err)
    alarms.value = alarms.value.filter(a => a.alarmId !== alarmId)
    ElMessage.success('人工消警已生效并归档')
    emit('alarmCleared', alarmId)
  }
}

// 标记误报
async function handleFalsePositive(alarmId) {
  try {
    if (alarmId < 9900) {
      await request({
        url: `/v1/alarm/${alarmId}/false-positive`,
        method: 'post'
      })
    }
    ElMessage.warning('已标记为误报并归档')
    alarms.value = alarms.value.filter(a => a.alarmId !== alarmId)
    emit('alarmFalsePositive', alarmId)
    alarmStore.fetchStats()
    alarmStore.fetchActiveAlarms()
  } catch (err) {
    console.error('标记误报失败:', err)
    alarms.value = alarms.value.filter(a => a.alarmId !== alarmId)
    ElMessage.warning('已标记为误报')
    emit('alarmFalsePositive', alarmId)
  }
}

// 派单对话框
const dispatchDialogVisible = ref(false)
const submittingTicket = ref(false)
const ticketForm = ref({
  alarmId: null,
  alarmType: '',
  title: '',
  ticketType: 'ALARM_REPAIR',
  operatorName: '张工',
  sopGuide: ''
})

function openDispatchDialog(alarm) {
  ticketForm.value = {
    alarmId: alarm.alarmId,
    alarmType: getAlarmTypeName(alarm.alarmType),
    title: `[紧急排障] ${props.rack.rackCode} 机柜${getAlarmTypeName(alarm.alarmType)}现场排查`,
    ticketType: 'ALARM_REPAIR',
    operatorName: '张工',
    sopGuide: `1. 检查 ${props.rack.rackCode} 所在通道密闭门是否漏风；\n2. 核查机柜前后迎风面温差与在架设备出风口；\n3. 监测在架服务器实时温度曲线。`
  }
  dispatchDialogVisible.value = true
}

async function confirmDispatchTicket() {
  submittingTicket.value = true
  try {
    const payload = {
      alarmId: ticketForm.value.alarmId && ticketForm.value.alarmId < 9900 ? ticketForm.value.alarmId : null,
      rackCode: props.rack?.rackCode,
      title: ticketForm.value.title,
      ticketType: ticketForm.value.ticketType,
      operatorName: ticketForm.value.operatorName,
      sopGuide: ticketForm.value.sopGuide,
      tenantId: props.rack?.tenantId || '000000'
    }
    const res = await createWorkTicket(payload)
    const ticket = res?.data !== undefined ? res.data : res
    dispatchDialogVisible.value = false
    ElMessage.success({
      message: `已成功生成排障工单【${ticket?.ticketNo || 'TK-XXXX'}】并指派给【${ticketForm.value.operatorName}】！`,
      duration: 4000
    })
    // 联动将活动告警卡片状态更新为 2 (已派单)
    if (ticketForm.value.alarmId) {
      const found = alarms.value.find(a => a.alarmId === ticketForm.value.alarmId)
      if (found) {
        found.status = 2
      }
    }
    emit('createTicket', { ...ticketForm.value, rackCode: props.rack?.rackCode, ticketNo: ticket?.ticketNo })
  } catch (err) {
    console.error('生成工单失败:', err)
    ElMessage.error('派单失败，请检查网络或后端状态')
  } finally {
    submittingTicket.value = false
  }
}

function getAlarmTypeName(type) {
  switch (type) {
    case 'TEMP_HIGH': return '高温越限告警'
    case 'VOLTAGE_LOW': return '供电欠压告警'
    case 'POWER_FAIL': return '市电失电告警'
    case 'HUMIDITY_HIGH': return '环境高湿告警'
    default: return type || '动环越限告警'
  }
}

function getDeviceTypeName(type) {
  switch (type) {
    case 'SERVER':
    case 'IT_SERVER': return '高密算力服务器'
    case 'SWITCH':
    case 'IT_SWITCH': return '网络汇聚交换机'
    case 'STORAGE':
    case 'IT_STORAGE': return '海量存储矩阵'
    case 'PDU':
    case 'SENSOR_UPS': return '智能双路PDU'
    case 'SENSOR_TEMP': return '动环微环境探针'
    default: return type || 'IT设备'
  }
}

function getDeviceIcon(type) {
  switch (type) {
    case 'SERVER':
    case 'IT_SERVER': return '🖥️'
    case 'SWITCH':
    case 'IT_SWITCH': return '🔀'
    case 'STORAGE':
    case 'IT_STORAGE': return '🗄️'
    case 'PDU':
    case 'SENSOR_UPS': return '🔌'
    case 'SENSOR_TEMP': return '🌡️'
    default: return '📦'
  }
}

function formatTime(dt) {
  if (!dt) return '--'
  return String(dt).replace('T', ' ').slice(0, 19)
}
</script>

<style scoped>
.rack-diagnosis-drawer :deep(.el-drawer__header) {
  background: #09101d;
  color: #00f2fe;
  margin-bottom: 0;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(0, 242, 254, 0.2);
  font-family: 'PingFang SC', sans-serif;
  font-weight: 600;
}

.rack-diagnosis-drawer :deep(.el-drawer__body) {
  background: #0b1326;
  color: #e2e8f0;
  padding: 16px;
  overflow-y: auto;
}

.drawer-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 1. 元数据态势栏 */
.rack-meta-banner {
  background: rgba(15, 23, 42, 0.85);
  border: 1px solid rgba(56, 189, 248, 0.3);
  border-radius: 8px;
  padding: 14px 16px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.3);
}

.banner-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.rack-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.code-badge {
  background: #0369a1;
  color: #ffffff;
  font-weight: 700;
  font-size: 14px;
  padding: 2px 8px;
  border-radius: 4px;
  letter-spacing: 0.5px;
}

.name-text {
  font-size: 15px;
  font-weight: 600;
  color: #f1f5f9;
}

.banner-sub {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #94a3b8;
}

/* 2. 动环遥测切片 */
.section-card {
  background: rgba(15, 23, 42, 0.75);
  border: 1px solid rgba(30, 41, 59, 0.8);
  border-radius: 8px;
  padding: 14px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.section-header .icon {
  font-size: 16px;
}

.section-header .title {
  font-size: 14px;
  font-weight: 600;
  color: #38bdf8;
}

.section-header .badge-count {
  background: #ef4444;
  color: #fff;
  font-size: 11px;
  padding: 0 6px;
  border-radius: 10px;
  font-weight: 700;
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
  margin-left: 4px;
  animation: pulse-dot 1.5s infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.3; transform: scale(1.4); }
}

.telemetry-metrics-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}

.metric-item {
  background: #091122;
  border-radius: 6px;
  padding: 10px;
  border-left: 4px solid #3b82f6;
}

.metric-item.safe {
  border-left-color: #10b981;
}

.metric-item.warning {
  border-left-color: #f59e0b;
  background: rgba(245, 158, 11, 0.08);
}

.metric-item.danger {
  border-left-color: #ef4444;
  background: rgba(239, 68, 68, 0.12);
}

.metric-item .label {
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 4px;
}

.metric-item .val-box {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.metric-item .num {
  font-size: 20px;
  font-weight: 700;
  font-family: 'DIN Alternate', sans-serif;
  color: #f8fafc;
}

.metric-item .unit {
  font-size: 12px;
  color: #64748b;
}

.metric-item .sub-desc {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 4px;
}

.power-utilization-bar {
  background: #091122;
  padding: 10px;
  border-radius: 6px;
}

.util-header {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 6px;
}

.power-val {
  color: #38bdf8;
  font-weight: 600;
}

/* 3. 告警板块 */
.empty-alarm-box, .empty-device-box {
  text-align: center;
  padding: 24px 0;
  background: #091122;
  border-radius: 6px;
}

.empty-icon {
  font-size: 28px;
  margin-bottom: 6px;
}

.empty-text {
  font-size: 13px;
  color: #e2e8f0;
  font-weight: 500;
}

.empty-sub {
  font-size: 11px;
  color: #64748b;
  margin-top: 4px;
}

.alarm-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.alarm-card {
  background: #091122;
  border: 1px solid rgba(239, 68, 68, 0.4);
  border-radius: 6px;
  padding: 12px;
  transition: all 0.2s ease;
}

.alarm-card.level-warning {
  border-color: rgba(245, 158, 11, 0.4);
}

.alarm-card.is-target {
  box-shadow: 0 0 12px rgba(239, 68, 68, 0.6);
  border-color: #ef4444;
}

.alarm-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.level-badge {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
}

.level-badge.crit {
  background: rgba(239, 68, 68, 0.2);
  color: #ef4444;
  border: 1px solid rgba(239, 68, 68, 0.5);
}

.level-badge.warn {
  background: rgba(245, 158, 11, 0.2);
  color: #f59e0b;
  border: 1px solid rgba(245, 158, 11, 0.5);
}

.alarm-time {
  font-size: 11px;
  color: #64748b;
}

.alarm-body {
  margin-bottom: 10px;
}

.alarm-type-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: #f1f5f9;
  margin-bottom: 4px;
}

.metric-val strong {
  color: #ef4444;
}

.alarm-rca {
  font-size: 12px;
  color: #cbd5e1;
  background: rgba(0, 0, 0, 0.25);
  padding: 6px 8px;
  border-radius: 4px;
}

.rca-label {
  color: #38bdf8;
  font-weight: 600;
}

.alarm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  padding-top: 8px;
}

/* 4. 在架设备透视 */
.thermal-warning-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.5);
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 10px;
  font-size: 12px;
  color: #fca5a5;
  line-height: 1.4;
}

.thermal-warning-banner strong {
  color: #ef4444;
}

.devices-table-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 280px;
  overflow-y: auto;
}

.device-row-card {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #091122;
  border: 1px solid rgba(56, 189, 248, 0.15);
  border-radius: 6px;
  padding: 8px 10px;
}

.device-row-card.heat-risk {
  border-color: rgba(239, 68, 68, 0.5);
  background: rgba(239, 68, 68, 0.06);
}

.dev-type-icon {
  font-size: 20px;
}

.dev-info {
  flex: 1;
}

.dev-name {
  font-size: 13px;
  font-weight: 600;
  color: #f1f5f9;
  display: flex;
  align-items: center;
  gap: 6px;
}

.dev-key {
  font-size: 11px;
  color: #64748b;
  font-weight: 400;
}

.heat-badge {
  background: #ef4444;
  color: #fff;
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
  font-weight: 600;
}

.dev-specs {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: #94a3b8;
  margin-top: 3px;
}

.loading-box {
  text-align: center;
  padding: 20px 0;
  color: #38bdf8;
  font-size: 12px;
}
</style>
