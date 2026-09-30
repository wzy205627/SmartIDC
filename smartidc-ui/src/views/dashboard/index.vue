<template>
  <div class="dashboard-container">
    <!-- 顶部核心指标看板 -->
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon-wrapper bg-blue">
              <el-icon><DataBoard /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-title">在管机架总数</div>
              <div class="stat-value">6 <span class="stat-unit">架 (A区)</span></div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon-wrapper bg-green">
              <el-icon><Odometer /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-title">当前机房 PUE 能效</div>
              <div class="stat-value">1.28 <span class="stat-tag tag-green">优</span></div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon-wrapper bg-purple">
              <el-icon><Lightning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-title">额定供电容量</div>
              <div class="stat-value">31.00 <span class="stat-unit">kVA</span></div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card shadow="hover" class="stat-card" :class="{ 'stat-card-alarm': alarmStore.alarmStats.activeTotal > 0 }">
          <div class="stat-content">
            <div class="stat-icon-wrapper" :class="alarmStore.alarmStats.activeTotal > 0 ? 'bg-red' : 'bg-orange'">
              <el-icon><WarningFilled /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-title">当前活动越限告警</div>
              <div class="stat-value" :class="{ 'text-danger': alarmStore.alarmStats.activeTotal > 0 }">
                {{ alarmStore.alarmStats.activeTotal }} <span class="stat-unit">起</span>
                <el-tag v-if="alarmStore.alarmStats.criticalCount > 0" size="small" type="danger" effect="dark" class="stat-tag">
                  {{ alarmStore.alarmStats.criticalCount }} 高危
                </el-tag>
                <el-tag v-else-if="alarmStore.alarmStats.warningCount > 0" size="small" type="warning" effect="dark" class="stat-tag">
                  {{ alarmStore.alarmStats.warningCount }} 预警
                </el-tag>
                <el-tag v-else size="small" type="success" class="stat-tag tag-green">
                  正常
                </el-tag>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 动环物联仿真演练与故障注入中枢 -->
    <el-card shadow="hover" class="mt-20 chaos-card">
      <template #header>
        <div class="card-header">
          <div class="header-title-box">
            <span class="header-icon">🧪</span>
            <span class="header-title">动环物联网数字化仿真与混沌演练中枢 (IoT Simulation & Chaos Console)</span>
            <el-tag :type="mockStatus.running ? 'success' : 'info'" effect="dark">
              {{ mockStatus.running ? '🟢 遥测流发生中' : '⚪ 仿真器已暂停' }}
            </el-tag>
            <el-tag :type="getChaosTagType(mockStatus.chaosMode)" effect="plain">
              当前演练模式: {{ mockStatus.chaosTitle || mockStatus.chaosMode || '正常工况' }}
            </el-tag>
          </div>
          <div class="header-actions">
            <el-button
              v-if="!mockStatus.running"
              type="success"
              size="small"
              :icon="VideoPlay"
              @click="handleStartMock"
            >
              启动心跳遥测流
            </el-button>
            <el-button
              v-else
              type="info"
              size="small"
              :icon="VideoPause"
              @click="handleStopMock"
            >
              暂停模拟流
            </el-button>
            <el-button
              type="primary"
              size="small"
              :icon="Refresh"
              @click="fetchMockStatus"
            >
              刷新状态
            </el-button>
          </div>
        </div>
      </template>

      <div class="chaos-body">
        <div class="chaos-intro">
          <span>
            💡 <strong>演练导引</strong>：SmartIDC 内置高斯随机游走动环生成器，定时向 EMQX 投递 A-01~A-06 机柜合规心跳报文。
            点击下方演练动作按钮，可一键测试【消抖防误报】、【单柜高危超温推屏】、【机房集群告警风暴归并】与【自动消警闭环自愈】：
          </span>
        </div>

        <div class="chaos-buttons-grid">
          <!-- 故障 1: 瞬态毛刺消抖 -->
          <div class="chaos-btn-item">
            <el-button
              type="warning"
              plain
              class="chaos-btn"
              @click="handleInjectGlitch"
            >
              🧹 注入单次瞬态毛刺 (42℃)
            </el-button>
            <div class="btn-tip">验证消抖拦截：偶发突发异常被状态机自动消除，<strong>0 误报告警</strong></div>
          </div>

          <!-- 故障 2: 单柜严重高温 -->
          <div class="chaos-btn-item">
            <el-button
              type="danger"
              plain
              class="chaos-btn"
              @click="handleInjectOverheat"
            >
              🚨 注入 A-03 持续严重高温 (38.5℃)
            </el-button>
            <div class="btn-tip">突破消抖窗口：激活 CRITICAL 告警，顶栏声光通知，机柜红光呼吸闪烁</div>
          </div>

          <!-- 故障 3: 越限告警演练 (A-01 严重超温) -->
          <div class="chaos-btn-item">
            <el-button
              type="danger"
              class="chaos-btn"
              @click="handleInjectDrillCritical"
            >
              🔥 注入 A-01 严重超温告警 (35.8℃)
            </el-button>
            <div class="btn-tip">越限告警演练：触发 <strong>CRITICAL 严重告警</strong>，数字大屏声光蜂鸣并联动排障抽屉</div>
          </div>

          <!-- 故障 4: 越限预警演练 (B-01 供电欠压) -->
          <div class="chaos-btn-item">
            <el-button
              type="warning"
              class="chaos-btn"
              @click="handleInjectDrillWarning"
            >
              ⚡ 注入 B-01 供电欠压预警 (195.2V)
            </el-button>
            <div class="btn-tip">越限预警演练：触发 <strong>WARNING 越限预警</strong>，拓扑机柜点亮橙色供电预警提示</div>
          </div>

          <!-- 故障 5: 告警风暴空间聚合 -->
          <div class="chaos-btn-item">
            <el-button
              type="danger"
              plain
              class="chaos-btn"
              @click="handleInjectStorm"
            >
              🌪️ 注入机房告警风暴 (A-01/02/03)
            </el-button>
            <div class="btn-tip">空间关联聚合：同机房多柜并发高温，<strong>自动归并为 1 条机房级主告警</strong></div>
          </div>

          <!-- 故障 6: 市电中断断电 -->
          <div class="chaos-btn-item">
            <el-button
              type="info"
              plain
              class="chaos-btn"
              @click="handleInjectBlackout"
            >
              ⚡ 注入 A-01 市电中断欠压 (0V)
            </el-button>
            <div class="btn-tip">供电安全演练：模拟母线欠压掉电停机异常</div>
          </div>

          <!-- 恢复: 一键自愈与消警 -->
          <div class="chaos-btn-item">
            <el-button
              type="success"
              class="chaos-btn"
              @click="handleResetMock"
            >
              ✅ 一键恢复全机房正常工况
            </el-button>
            <div class="btn-tip">闭环自愈：一键消除所有活动越限告警，系统<strong>全面恢复绿色常态</strong></div>
          </div>
        </div>

        <div class="chaos-meta-bar font-mono">
          <span>📡 累计广播遥测报文: <strong>{{ mockStatus.totalPacketsSent || 0 }}</strong> 条</span>
          <span>⏱️ 发生采样周期: {{ mockStatus.intervalMs || 2000 }} ms</span>
          <span>🏢 纳管仿真机架: {{ (mockStatus.managedRacks || []).join(', ') }}</span>
        </div>
      </div>
    </el-card>

    <!-- 基础设施就绪状态与 Phase 进展卡片 -->
    <el-card shadow="hover" class="mt-20">
      <template #header>
        <div class="card-header">
          <span>🚀 SmartIDC 基础设施集群健康状态</span>
          <el-tag type="success">Phase 0 验收就绪</el-tag>
        </div>
      </template>

      <el-descriptions :column="3" border>
        <el-descriptions-item label="MySQL 8.0 (业务数据)">
          <el-badge is-dot type="success">127.0.0.1:3308 (已避让端口)</el-badge>
        </el-descriptions-item>
        <el-descriptions-item label="Redis 7.x (遥测缓存)">
          <el-badge is-dot type="success">127.0.0.1:6389 (已避让端口)</el-badge>
        </el-descriptions-item>
        <el-descriptions-item label="EMQX 5.x (MQTT Broker)">
          <el-badge is-dot type="success">1883 / Dashboard 18083</el-badge>
        </el-descriptions-item>
        <el-descriptions-item label="PostgreSQL 16 (pgvector)">
          <el-badge is-dot type="success">5433 (vector 0.8.6 激活)</el-badge>
        </el-descriptions-item>
        <el-descriptions-item label="MinIO (对象存储)">
          <el-badge is-dot type="success">9000 / Console 9001</el-badge>
        </el-descriptions-item>
        <el-descriptions-item label="Spring Boot 3.3 (后端)">
          <el-badge is-dot type="success">8080 (JDK 21 虚拟线程运行中)</el-badge>
        </el-descriptions-item>
      </el-descriptions>

      <div class="action-bar mt-20">
        <el-button type="primary" size="large" @click="router.push('/asset/rack')">
          前往查看机柜资产与 U 位详情 ➔
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAlarmStore } from '@/stores/alarm'
import { VideoPlay, VideoPause, Refresh } from '@element-plus/icons-vue'
import {
  getMockStatus,
  startMock,
  stopMock,
  injectOverheat,
  injectBlackout,
  injectStorm,
  injectGlitch,
  resetMock
} from '@/api/mock'
import { injectDrillAlarm, resetDrillAlarms } from '@/api/alarm'
import { ElMessage } from 'element-plus'

const router = useRouter()
const alarmStore = useAlarmStore()

const mockStatus = ref({
  running: false,
  chaosMode: 'NORMAL',
  chaosTitle: '正常工况',
  targetRack: 'A-03',
  intervalMs: 2000,
  managedRacks: [],
  totalPacketsSent: 0
})

let statusTimer = null

function getChaosTagType(mode) {
  if (mode === 'OVERHEAT' || mode === 'STORM' || mode === 'DRILL_CRITICAL') return 'danger'
  if (mode === 'GLITCH' || mode === 'DRILL_WARNING') return 'warning'
  if (mode === 'BLACKOUT') return 'info'
  return 'success'
}

async function fetchMockStatus() {
  try {
    const data = await getMockStatus()
    if (data) {
      mockStatus.value = data
    }
  } catch (err) {
    // 忽略刷新异常
  }
}

async function handleStartMock() {
  await startMock()
  ElMessage.success('已成功启动后台动环心跳遥测流')
  await fetchMockStatus()
}

async function handleStopMock() {
  await stopMock()
  ElMessage.info('已暂停动环心跳数据发生')
  await fetchMockStatus()
}

async function handleInjectGlitch() {
  await injectGlitch('A-03')
  ElMessage.warning('【演练】已向 A-03 注入单次 42℃ 瞬态毛刺，请观察消抖引擎拦截 (0 误报)')
  await fetchMockStatus()
}

async function handleInjectOverheat() {
  await injectOverheat('A-03')
  ElMessage.error('【演练】已向 A-03 注入持续 38.5℃ 高温，即将触发 CRITICAL 告警！')
  await fetchMockStatus()
}

// 🧪 新增：A-01 严重超温越限告警演练
async function handleInjectDrillCritical() {
  try {
    await injectDrillAlarm({
      rackCode: 'A-01',
      alarmLevel: 'CRITICAL',
      alarmType: 'TEMP_HIGH',
      metricValue: '35.80℃',
      summary: '[应急演练] A-01高密算力排进风面突破35℃红线，已触发一级热岛熔断预警'
    })
    ElMessage.error({
      message: '🔥 【演练】已注入【A-01 机柜严重超温越限告警 (35.8℃)】！大屏与数字孪生看板已同步响应！',
      duration: 4000
    })
    mockStatus.value.chaosMode = 'DRILL_CRITICAL'
    mockStatus.value.chaosTitle = 'A-01 严重超温越限'
    await alarmStore.fetchStats()
    await alarmStore.fetchActiveAlarms()
    await fetchMockStatus()
  } catch (e) {
    ElMessage.error('注入演练告警失败: ' + (e.message || '网络异常'))
  }
}

// 🧪 新增：B-01 供电欠压越限预警演练
async function handleInjectDrillWarning() {
  try {
    await injectDrillAlarm({
      rackCode: 'B-01',
      alarmLevel: 'WARNING',
      alarmType: 'VOLTAGE_LOW',
      metricValue: '195.20V',
      summary: '[应急演练] B-01机柜输入支路电压跌落至195V，已触发电能质量预警'
    })
    ElMessage.warning({
      message: '⚡ 【演练】已注入【B-01 供电欠压越限预警 (195.2V)】！',
      duration: 3500
    })
    mockStatus.value.chaosMode = 'DRILL_WARNING'
    mockStatus.value.chaosTitle = 'B-01 供电欠压预警'
    await alarmStore.fetchStats()
    await alarmStore.fetchActiveAlarms()
    await fetchMockStatus()
  } catch (e) {
    ElMessage.error('注入演练预警失败: ' + (e.message || '网络异常'))
  }
}

async function handleInjectStorm() {
  await injectStorm()
  ElMessage.error('【演练】已注入机房集群高温告警风暴，系统将自动空间归并为 1 条主告警！')
  await fetchMockStatus()
}

async function handleInjectBlackout() {
  await injectBlackout('A-01')
  ElMessage.warning('【演练】已向 A-01 注入市电中断断电异常 (0V/0A)')
  await fetchMockStatus()
}

async function handleResetMock() {
  try {
    await resetMock()
    await resetDrillAlarms()
    ElMessage.success('✅ 【工况自愈】全机房标准安全工况已复原，所有活动越限告警已全部消除！')
    await alarmStore.fetchStats()
    await alarmStore.fetchActiveAlarms()
    await fetchMockStatus()
  } catch (e) {
    ElMessage.error('工况复位失败: ' + (e.message || '网络异常'))
  }
}

onMounted(() => {
  alarmStore.fetchStats()
  alarmStore.fetchActiveAlarms()
  fetchMockStatus()
  // 每 3 秒轻量轮询更新模拟器累计报文计数
  statusTimer = setInterval(fetchMockStatus, 3000)
})

onUnmounted(() => {
  if (statusTimer) {
    clearInterval(statusTimer)
    statusTimer = null
  }
})
</script>

<style scoped>
.dashboard-container {
  padding-bottom: 20px;
}

.stat-card {
  border-radius: 8px;
  transition: all 0.3s ease;
}

.stat-card-alarm {
  border-color: #fbc4c4 !important;
  box-shadow: 0 0 12px rgba(245, 108, 108, 0.3) !important;
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon-wrapper {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 28px;
}

.bg-blue { background: linear-gradient(135deg, #36d1dc, #5b86e5); }
.bg-green { background: linear-gradient(135deg, #11998e, #38ef7d); }
.bg-purple { background: linear-gradient(135deg, #8a2387, #e94057); }
.bg-orange { background: linear-gradient(135deg, #f7971e, #ffd200); }
.bg-red { background: linear-gradient(135deg, #eb3349, #f45c43); }

.text-danger {
  color: #f56c6c !important;
}

.stat-info {
  flex: 1;
}

.stat-title {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 26px;
  font-weight: bold;
  color: #303133;
}

.stat-unit {
  font-size: 14px;
  font-weight: normal;
  color: #909399;
  margin-left: 4px;
}

.stat-tag {
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 4px;
  margin-left: 6px;
}

.tag-green {
  background-color: #e1f3d8;
  color: #67c23a;
}

.mt-20 {
  margin-top: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: bold;
}

.action-bar {
  display: flex;
  justify-content: center;
}

/* 动环物联仿真演练中枢样式 */
.chaos-card {
  border-radius: 8px;
  border-top: 3px solid #409eff;
}

.header-title-box {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.header-icon {
  font-size: 20px;
}

.header-title {
  font-size: 15px;
  font-weight: bold;
  color: #303133;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.chaos-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chaos-intro {
  font-size: 13px;
  color: #606266;
  background-color: #ecf5ff;
  padding: 10px 14px;
  border-radius: 6px;
  border-left: 4px solid #409eff;
  line-height: 1.6;
}

.chaos-buttons-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
}

.chaos-btn-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: #f8fafc;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  transition: all 0.2s ease;
}

.chaos-btn-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border-color: #cbd5e1;
}

.chaos-btn {
  width: 100%;
  font-weight: bold;
  height: 38px;
}

.btn-tip {
  font-size: 11px;
  color: #64748b;
  line-height: 1.4;
}

.chaos-meta-bar {
  display: flex;
  align-items: center;
  gap: 24px;
  font-size: 12px;
  color: #64748b;
  padding-top: 8px;
  border-top: 1px dashed #e2e8f0;
  flex-wrap: wrap;
}
</style>
