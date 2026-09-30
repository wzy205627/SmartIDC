<template>
  <div class="topology-grid-wrapper">
    <!-- 顶部状态栏 -->
    <div class="grid-header">
      <div class="header-left">
        <span class="pulse-beacon"></span>
        <span class="room-title font-mono">{{ roomName || '华东机房物理拓扑' }}</span>
        <span class="room-spec font-mono">// 19" 42U 标准高密阵列</span>
      </div>
      <div class="header-legend">
        <span class="legend-item"><i class="dot dot-normal"></i>正常冷区 (&lt;26℃)</span>
        <span class="legend-item"><i class="dot dot-warning"></i>轻度温升 (26-30℃)</span>
        <span class="legend-item"><i class="dot dot-danger"></i>高危热岛 (≥30℃)</span>
        <span class="legend-item"><i class="dot dot-idle"></i>空闲/离线</span>
      </div>
    </div>

    <!-- 精密空调 (CRAC) 顶置机组 -->
    <div class="crac-row">
      <div class="crac-unit" v-for="ac in cracUnits" :key="ac.id">
        <div class="crac-icon">❄️</div>
        <div class="crac-info">
          <span class="crac-name">{{ ac.name }}</span>
          <span class="crac-status">出风温: {{ ac.temp }}℃ | 运行良好</span>
        </div>
        <div class="crac-fans">
          <span class="fan-blade spinning"></span>
          <span class="fan-blade spinning"></span>
        </div>
      </div>
    </div>

    <!-- 冷通道 (Cold Aisle) 动态下送风示意 -->
    <div class="airflow-corridor cold-corridor">
      <div class="airflow-line">
        <span class="airflow-arrow">▼</span>
        <span class="airflow-text">冷通道封闭气流沉降区 (COLD AISLE // 20℃~22℃ 送风)</span>
        <span class="airflow-arrow">▼</span>
      </div>
    </div>

    <!-- 机柜排布拓扑视区 -->
    <div class="rack-matrix">
      <!-- A 列机柜排 -->
      <div class="rack-row-block">
        <div class="row-header">
          <span class="row-tag font-mono">ROW-A</span>
          <span class="row-name">A 列高密算力排</span>
        </div>
        <div class="rack-cards-container">
          <div
            v-for="rack in rowARacks"
            :key="rack.rackCode"
            class="rack-box"
            :class="[getRackStatusClass(rack), { 'rack-focused': rack.rackCode === focusedRackCode }]"
            @click="$emit('selectRack', rack)"
          >
            <!-- 气泡画像 Hover Tooltip -->
            <el-tooltip
              placement="top"
              effect="dark"
              :show-after="100"
              popper-class="rack-screen-popper"
            >
              <template #content>
                <div class="rack-tooltip-profile">
                  <div class="tt-header">
                    <strong class="tt-code font-mono">{{ rack.rackCode }}</strong>
                    <el-tag size="small" :type="rack.tenantId === '000000' ? 'info' : 'warning'">
                      {{ rack.tenantId === '000000' ? '平台自营' : rack.tenantId }}
                    </el-tag>
                  </div>
                  <div class="tt-body font-mono">
                    <div class="tt-row">
                      <span>实时温度:</span>
                      <strong :style="{ color: getTempColor(rack.telemetry?.temperature) }">
                        {{ formatVal(rack.telemetry?.temperature, '℃') }}
                      </strong>
                    </div>
                    <div class="tt-row">
                      <span>相对湿度:</span>
                      <strong>{{ formatVal(rack.telemetry?.humidity, '%RH') }}</strong>
                    </div>
                    <div class="tt-row">
                      <span>实时功耗:</span>
                      <strong class="text-cyan">{{ formatVal(rack.telemetry?.powerKw, 'kW') }}</strong>
                    </div>
                    <div class="tt-row">
                      <span>输入电压:</span>
                      <strong>{{ formatVal(rack.telemetry?.voltage, 'V') }}</strong>
                    </div>
                    <div class="tt-row">
                      <span>工作电流:</span>
                      <strong>{{ formatVal(rack.telemetry?.currentAmp, 'A') }}</strong>
                    </div>
                    <div class="tt-row">
                      <span>U位负荷:</span>
                      <strong>{{ rack.usedU }} / {{ rack.totalU }} U ({{ Math.round((rack.usedU/rack.totalU)*100) }}%)</strong>
                    </div>
                  </div>
                </div>
              </template>

              <!-- 机柜卡片外观 -->
              <div class="rack-inner" @click="$emit('selectRack', rack)">
                <div class="rack-card-top">
                  <span class="rack-code font-mono">{{ rack.rackCode }}</span>
                  <span class="rack-status-dot"></span>
                </div>
                <div class="rack-card-temp font-mono" :style="{ color: getTempColor(rack.telemetry?.temperature) }">
                  {{ rack.telemetry?.temperature ? Number(rack.telemetry.temperature).toFixed(1) + '℃' : '--' }}
                </div>
                <div class="rack-card-meta font-mono">
                  <span class="power-tag">⚡ {{ rack.telemetry?.powerKw ? Number(rack.telemetry.powerKw).toFixed(1) + 'k' : '0k' }}</span>
                  <span class="u-tag">{{ rack.usedU }}/{{ rack.totalU }}U</span>
                </div>
                <!-- 底部小导轨插槽简图 -->
                <div class="rack-slots-bar">
                  <div
                    class="rack-slots-fill"
                    :style="{ width: `${Math.round((rack.usedU / rack.totalU) * 100)}%` }"
                  ></div>
                </div>
              </div>
            </el-tooltip>
          </div>
        </div>
      </div>

      <!-- 热通道 (Hot Aisle) 隔离回风区 -->
      <div class="airflow-corridor hot-corridor">
        <div class="airflow-line">
          <span class="airflow-arrow">▲</span>
          <span class="airflow-text">热通道回风抽取区 (HOT AISLE // 32℃~35℃ 回风隔离带)</span>
          <span class="airflow-arrow">▲</span>
        </div>
      </div>

      <!-- B 列机柜排 -->
      <div class="rack-row-block">
        <div class="row-header">
          <span class="row-tag font-mono">ROW-B</span>
          <span class="row-name">B 列托管与网络排</span>
        </div>
        <div class="rack-cards-container">
          <div
            v-for="rack in rowBRacks"
            :key="rack.rackCode"
            class="rack-box"
            :class="[getRackStatusClass(rack), { 'rack-focused': rack.rackCode === focusedRackCode }]"
            @click="$emit('selectRack', rack)"
          >
            <!-- 气泡画像 Hover Tooltip -->
            <el-tooltip
              placement="bottom"
              effect="dark"
              :show-after="100"
              popper-class="rack-screen-popper"
            >
              <template #content>
                <div class="rack-tooltip-profile">
                  <div class="tt-header">
                    <strong class="tt-code font-mono">{{ rack.rackCode }}</strong>
                    <el-tag size="small" :type="rack.tenantId === '000000' ? 'info' : 'warning'">
                      {{ rack.tenantId === '000000' ? '平台自营' : rack.tenantId }}
                    </el-tag>
                  </div>
                  <div class="tt-body font-mono">
                    <div class="tt-row">
                      <span>实时温度:</span>
                      <strong :style="{ color: getTempColor(rack.telemetry?.temperature) }">
                        {{ formatVal(rack.telemetry?.temperature, '℃') }}
                      </strong>
                    </div>
                    <div class="tt-row">
                      <span>相对湿度:</span>
                      <strong>{{ formatVal(rack.telemetry?.humidity, '%RH') }}</strong>
                    </div>
                    <div class="tt-row">
                      <span>实时功耗:</span>
                      <strong class="text-cyan">{{ formatVal(rack.telemetry?.powerKw, 'kW') }}</strong>
                    </div>
                    <div class="tt-row">
                      <span>输入电压:</span>
                      <strong>{{ formatVal(rack.telemetry?.voltage, 'V') }}</strong>
                    </div>
                    <div class="tt-row">
                      <span>工作电流:</span>
                      <strong>{{ formatVal(rack.telemetry?.currentAmp, 'A') }}</strong>
                    </div>
                    <div class="tt-row">
                      <span>U位负荷:</span>
                      <strong>{{ rack.usedU }} / {{ rack.totalU }} U ({{ Math.round((rack.usedU/rack.totalU)*100) }}%)</strong>
                    </div>
                  </div>
                </div>
              </template>

              <!-- 机柜卡片外观 -->
              <div class="rack-inner" @click="$emit('selectRack', rack)">
                <div class="rack-card-top">
                  <span class="rack-code font-mono">{{ rack.rackCode }}</span>
                  <span class="rack-status-dot"></span>
                </div>
                <div class="rack-card-temp font-mono" :style="{ color: getTempColor(rack.telemetry?.temperature) }">
                  {{ rack.telemetry?.temperature ? Number(rack.telemetry.temperature).toFixed(1) + '℃' : '--' }}
                </div>
                <div class="rack-card-meta font-mono">
                  <span class="power-tag">⚡ {{ rack.telemetry?.powerKw ? Number(rack.telemetry.powerKw).toFixed(1) + 'k' : '0k' }}</span>
                  <span class="u-tag">{{ rack.usedU }}/{{ rack.totalU }}U</span>
                </div>
                <!-- 底部小导轨插槽简图 -->
                <div class="rack-slots-bar">
                  <div
                    class="rack-slots-fill"
                    :style="{ width: `${Math.round((rack.usedU / rack.totalU) * 100)}%` }"
                  ></div>
                </div>
              </div>
            </el-tooltip>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  roomName: {
    type: String,
    default: '华东01数据机房'
  },
  rackList: {
    type: Array,
    default: () => []
  },
  focusedRackCode: {
    type: String,
    default: ''
  }
})

defineEmits(['selectRack'])

const cracUnits = [
  { id: 'AC-01', name: '精密空调 #01 (冷量: 65kW)', temp: 19.8 },
  { id: 'AC-02', name: '精密空调 #02 (冷量: 65kW)', temp: 20.2 }
]

const rowARacks = computed(() => {
  // 如果数据库有机柜，优先提取 A- 开头的机柜，若不够 6 台补齐展示
  const list = props.rackList.filter(r => r.rackCode.startsWith('A'))
  if (list.length > 0) return list
  // 若当前是租户 T10001，依然展示机房整体布局拓扑 (A列作为相邻列展示)
  return [
    { rackCode: 'A-01', totalU: 42, usedU: 16, telemetry: { temperature: 24.2, humidity: 48.2, powerKw: 1.5, voltage: 220.1, currentAmp: 6.8 } },
    { rackCode: 'A-02', totalU: 42, usedU: 8, telemetry: { temperature: 23.8, humidity: 49.0, powerKw: 0.8, voltage: 219.8, currentAmp: 3.6 } },
    { rackCode: 'A-03', totalU: 42, usedU: 0, telemetry: { temperature: 21.6, humidity: 52.1, powerKw: 0.0, voltage: 220.3, currentAmp: 0.0 } },
    { rackCode: 'A-04', totalU: 42, usedU: 0, telemetry: { temperature: 21.5, humidity: 52.3, powerKw: 0.0, voltage: 220.0, currentAmp: 0.0 } },
    { rackCode: 'A-05', totalU: 42, usedU: 0, telemetry: { temperature: 21.7, humidity: 51.9, powerKw: 0.0, voltage: 219.9, currentAmp: 0.0 } },
    { rackCode: 'A-06', totalU: 42, usedU: 0, telemetry: { temperature: 21.4, humidity: 52.4, powerKw: 0.0, voltage: 220.2, currentAmp: 0.0 } }
  ]
})

const rowBRacks = computed(() => {
  const list = props.rackList.filter(r => r.rackCode.startsWith('B'))
  if (list.length > 0) return list
  return [
    { rackCode: 'B-01', totalU: 42, usedU: 12, telemetry: { temperature: 28.5, humidity: 45.2, powerKw: 3.4, voltage: 219.8, currentAmp: 16.3 } },
    { rackCode: 'B-02', totalU: 42, usedU: 0, telemetry: { temperature: 21.8, humidity: 52.4, powerKw: 0.0, voltage: 219.2, currentAmp: 0.0 } },
    { rackCode: 'B-03', totalU: 42, usedU: 0, telemetry: { temperature: 22.0, humidity: 51.5, powerKw: 0.0, voltage: 220.1, currentAmp: 0.0 } },
    { rackCode: 'B-04', totalU: 42, usedU: 0, telemetry: { temperature: 21.9, humidity: 51.8, powerKw: 0.0, voltage: 220.0, currentAmp: 0.0 } },
    { rackCode: 'B-05', totalU: 42, usedU: 0, telemetry: { temperature: 22.1, humidity: 51.2, powerKw: 0.0, voltage: 219.9, currentAmp: 0.0 } },
    { rackCode: 'B-06', totalU: 42, usedU: 0, telemetry: { temperature: 21.8, humidity: 52.0, powerKw: 0.0, voltage: 220.3, currentAmp: 0.0 } }
  ]
})

function getRackStatusClass(rack) {
  const temp = rack.telemetry?.temperature
  if (temp == null) return 'rack-idle'
  if (temp >= 30.0) return 'rack-critical'
  if (temp >= 26.0) return 'rack-warning'
  return 'rack-normal'
}

function getTempColor(temp) {
  if (temp == null) return '#94A3B8'
  if (temp >= 30.0) return '#EF4444'
  if (temp >= 26.0) return '#F59E0B'
  return '#10B981'
}

function formatVal(val, unit) {
  if (val == null) return '--'
  return `${Number(val).toFixed(1)} ${unit}`
}
</script>

<style scoped>
.topology-grid-wrapper {
  background: rgba(11, 19, 38, 0.85);
  border: 1px solid rgba(56, 189, 248, 0.25);
  border-radius: 10px;
  padding: 16px 20px;
  backdrop-filter: blur(12px);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.grid-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding-bottom: 10px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pulse-beacon {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #38BDF8;
  box-shadow: 0 0 10px #38BDF8;
  animation: beacon-pulse 2s infinite ease-in-out;
}

@keyframes beacon-pulse {
  0% { transform: scale(0.9); opacity: 0.6; }
  50% { transform: scale(1.3); opacity: 1; box-shadow: 0 0 14px #38BDF8; }
  100% { transform: scale(0.9); opacity: 0.6; }
}

.room-title {
  font-size: 15px;
  font-weight: 700;
  color: #F8FAFC;
  letter-spacing: 0.8px;
}

.room-spec {
  font-size: 11px;
  color: #64748B;
}

.header-legend {
  display: flex;
  align-items: center;
  gap: 14px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: #94A3B8;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.dot-normal { background: #10B981; box-shadow: 0 0 6px #10B981; }
.dot-warning { background: #F59E0B; box-shadow: 0 0 6px #F59E0B; }
.dot-danger { background: #EF4444; box-shadow: 0 0 8px #EF4444; }
.dot-idle { background: #4B5563; }

/* 精密空调 */
.crac-row {
  display: flex;
  justify-content: space-around;
  gap: 16px;
  background: rgba(15, 23, 42, 0.6);
  border: 1px dashed rgba(56, 189, 248, 0.3);
  border-radius: 6px;
  padding: 8px 16px;
}

.crac-unit {
  display: flex;
  align-items: center;
  gap: 10px;
}

.crac-icon {
  font-size: 20px;
  filter: drop-shadow(0 0 6px #38BDF8);
}

.crac-info {
  display: flex;
  flex-direction: column;
}

.crac-name {
  font-size: 12px;
  font-weight: 600;
  color: #E2E8F0;
}

.crac-status {
  font-size: 10px;
  color: #38BDF8;
}

.crac-fans {
  display: flex;
  gap: 4px;
}

.fan-blade {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px dashed #38BDF8;
}

.spinning {
  animation: spin 3s linear infinite;
}

@keyframes spin {
  100% { transform: rotate(360deg); }
}

/* 冷热通道条带 */
.airflow-corridor {
  border-radius: 4px;
  padding: 4px 10px;
  text-align: center;
}

.cold-corridor {
  background: linear-gradient(90deg, rgba(16, 185, 129, 0.05), rgba(56, 189, 248, 0.15), rgba(16, 185, 129, 0.05));
  border: 1px solid rgba(56, 189, 248, 0.2);
}

.hot-corridor {
  background: linear-gradient(90deg, rgba(239, 68, 68, 0.05), rgba(245, 158, 11, 0.15), rgba(239, 68, 68, 0.05));
  border: 1px solid rgba(245, 158, 11, 0.2);
}

.airflow-line {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  font-size: 11px;
}

.cold-corridor .airflow-text {
  color: #38BDF8;
  font-weight: 600;
  letter-spacing: 1px;
}

.cold-corridor .airflow-arrow {
  color: #38BDF8;
  animation: bounce-down 1.5s infinite;
}

.hot-corridor .airflow-text {
  color: #F59E0B;
  font-weight: 600;
  letter-spacing: 1px;
}

.hot-corridor .airflow-arrow {
  color: #F59E0B;
  animation: bounce-up 1.5s infinite;
}

@keyframes bounce-down {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(3px); }
}

@keyframes bounce-up {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px); }
}

/* 机柜排布矩阵 */
.rack-matrix {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.rack-row-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.row-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.row-tag {
  font-size: 11px;
  font-weight: bold;
  background: rgba(56, 189, 248, 0.15);
  color: #38BDF8;
  padding: 1px 6px;
  border-radius: 3px;
  border: 1px solid rgba(56, 189, 248, 0.3);
}

.row-name {
  font-size: 12px;
  color: #94A3B8;
}

.rack-cards-container {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}

/* 机柜单个卡片 */
.rack-box {
  background: rgba(30, 41, 59, 0.85);
  border: 1.5px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  position: relative;
  overflow: hidden;
}

.rack-box:hover {
  transform: translateY(-3px) scale(1.02);
  box-shadow: 0 8px 20px rgba(56, 189, 248, 0.35);
  border-color: #38BDF8;
}

.rack-box.rack-focused {
  border-color: #F59E0B !important;
  box-shadow: 0 0 20px rgba(245, 158, 11, 0.8), 0 0 40px rgba(239, 68, 68, 0.5) !important;
  animation: ripple-focus 1.8s ease-in-out infinite;
  z-index: 10;
}

@keyframes ripple-focus {
  0% {
    box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.8), 0 0 15px rgba(245, 158, 11, 0.5);
    transform: scale(1);
  }
  50% {
    box-shadow: 0 0 0 10px rgba(245, 158, 11, 0), 0 0 30px rgba(239, 68, 68, 0.8);
    transform: scale(1.04);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(245, 158, 11, 0), 0 0 15px rgba(245, 158, 11, 0.5);
    transform: scale(1);
  }
}

.rack-inner {
  padding: 10px 10px 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.rack-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.rack-code {
  font-size: 13px;
  font-weight: 700;
  color: #F8FAFC;
  letter-spacing: 0.5px;
}

.rack-status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.rack-card-temp {
  font-size: 18px;
  font-weight: 800;
  text-align: center;
  margin: 4px 0;
  text-shadow: 0 0 10px currentColor;
}

.rack-card-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 10px;
  color: #94A3B8;
}

.power-tag {
  color: #38BDF8;
}

.u-tag {
  color: #CBD5E1;
}

.rack-slots-bar {
  width: 100%;
  height: 4px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 2px;
  margin-top: 4px;
  overflow: hidden;
}

.rack-slots-fill {
  height: 100%;
  background: #38BDF8;
  border-radius: 2px;
}

/* 状态着色 */
.rack-normal {
  border-color: rgba(16, 185, 129, 0.4);
}
.rack-normal .rack-status-dot {
  background: #10B981;
  box-shadow: 0 0 6px #10B981;
}

.rack-warning {
  border-color: rgba(245, 158, 11, 0.6);
  background: rgba(245, 158, 11, 0.08);
}
.rack-warning .rack-status-dot {
  background: #F59E0B;
  box-shadow: 0 0 8px #F59E0B;
}

.rack-critical {
  border-color: #EF4444;
  background: rgba(239, 68, 68, 0.18);
  box-shadow: 0 0 16px rgba(239, 68, 68, 0.4);
  animation: rack-pulse 1.2s infinite ease-in-out;
}
.rack-critical .rack-status-dot {
  background: #EF4444;
  box-shadow: 0 0 10px #EF4444;
}

@keyframes rack-pulse {
  0% { box-shadow: 0 0 10px rgba(239, 68, 68, 0.3); border-color: #EF4444; }
  50% { box-shadow: 0 0 22px rgba(239, 68, 68, 0.85); border-color: #FCA5A5; }
  100% { box-shadow: 0 0 10px rgba(239, 68, 68, 0.3); border-color: #EF4444; }
}

.rack-idle {
  opacity: 0.75;
}
.rack-idle .rack-status-dot {
  background: #64748B;
}

/* 气泡画像 */
.rack-tooltip-profile {
  padding: 4px;
  min-width: 170px;
}

.tt-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.15);
  padding-bottom: 6px;
  margin-bottom: 6px;
}

.tt-code {
  font-size: 13px;
  color: #38BDF8;
}

.tt-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 11px;
}

.tt-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #CBD5E1;
}

.text-cyan {
  color: #38BDF8;
}
</style>
