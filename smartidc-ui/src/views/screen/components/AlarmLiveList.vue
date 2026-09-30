<template>
  <div class="alarm-live-card">
    <div class="card-header">
      <div class="header-left">
        <span class="pulse-dot" :class="{ 'pulse-active': activeAlarms.length > 0 }"></span>
        <span class="header-title">🚨 实时越限告警情报速报</span>
      </div>
      <el-badge :value="activeAlarms.length" :hidden="activeAlarms.length === 0" type="danger" />
    </div>

    <div class="alarm-list-scroll">
      <div v-if="activeAlarms.length === 0" class="alarm-empty">
        <div class="shield-icon">🛡️</div>
        <div class="empty-title">当前无任何越限告警</div>
        <div class="empty-desc">全域机架动环温湿度与电力负载均处于绿线阈值内</div>
      </div>

      <div
        v-for="item in activeAlarms"
        :key="item.alarmId"
        class="alarm-item"
        :class="{ 'alarm-item-crit': item.alarmLevel === 'CRITICAL' }"
        @click="handleClickAlarm(item)"
      >
        <div class="item-top">
          <span class="level-badge" :class="item.alarmLevel.toLowerCase()">
            {{ item.alarmLevel === 'CRITICAL' ? '严重高危' : '越限警告' }}
          </span>
          <span class="rack-target font-mono">机柜 {{ item.rackCode }}</span>
          <span class="alarm-type-text">{{ item.alarmType }}</span>
        </div>
        <div class="item-body">
          <div class="metric-line">
            <span class="label">触发数值:</span>
            <strong class="val text-danger">{{ item.metricValue }}</strong>
          </div>
          <div class="time-line font-mono">
            {{ formatTime(item.triggerTime) }}
          </div>
        </div>
        <div class="item-footer" v-if="item.rcaSummary">
          <span class="rca-label">SOP诊断:</span>
          <span class="rca-content" :title="item.rcaSummary">{{ item.rcaSummary }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  activeAlarms: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['selectRack', 'focusAlarm'])

function handleClickAlarm(item) {
  emit('selectRack', item.rackCode)
  emit('focusAlarm', item)
}

function formatTime(isoStr) {
  if (!isoStr) return '刚刚'
  const date = new Date(isoStr)
  if (isNaN(date.getTime())) {
    return isoStr.substring(11, 19) || isoStr
  }
  return date.toTimeString().substring(0, 8)
}
</script>

<style scoped>
.alarm-live-card {
  background: rgba(15, 23, 42, 0.75);
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-radius: 8px;
  padding: 12px 14px;
  backdrop-filter: blur(10px);
  display: flex;
  flex-direction: column;
  height: 230px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-title {
  font-size: 13px;
  font-weight: 600;
  color: #E2E8F0;
  letter-spacing: 0.5px;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10B981;
  box-shadow: 0 0 6px #10B981;
}

.pulse-active {
  background: #EF4444;
  box-shadow: 0 0 8px #EF4444;
  animation: pulse-ring 1s infinite;
}

@keyframes pulse-ring {
  0% { transform: scale(0.95); opacity: 0.8; }
  50% { transform: scale(1.3); opacity: 1; }
  100% { transform: scale(0.95); opacity: 0.8; }
}

.alarm-list-scroll {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 4px;
}

.alarm-list-scroll::-webkit-scrollbar {
  width: 4px;
}
.alarm-list-scroll::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.15);
  border-radius: 2px;
}

.alarm-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #64748B;
  text-align: center;
  padding: 10px;
}

.shield-icon {
  font-size: 26px;
  margin-bottom: 4px;
}

.empty-title {
  font-size: 13px;
  color: #94A3B8;
  font-weight: 500;
  margin-bottom: 2px;
}

.empty-desc {
  font-size: 11px;
  color: #64748B;
  line-height: 1.4;
}

.alarm-item {
  background: rgba(30, 41, 59, 0.65);
  border-left: 3px solid #F59E0B;
  border-radius: 4px;
  padding: 8px 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.alarm-item:hover {
  background: rgba(51, 65, 85, 0.8);
  transform: translateX(2px);
}

.alarm-item-crit {
  border-left-color: #EF4444;
  background: rgba(239, 68, 68, 0.1);
  box-shadow: 0 0 8px rgba(239, 68, 68, 0.2);
}

.item-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.level-badge {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 3px;
  font-weight: bold;
}

.level-badge.critical {
  background: #EF4444;
  color: #FFFFFF;
}

.level-badge.warning {
  background: #F59E0B;
  color: #FFFFFF;
}

.rack-target {
  font-size: 12px;
  font-weight: bold;
  color: #F8FAFC;
}

.alarm-type-text {
  font-size: 11px;
  color: #94A3B8;
  margin-left: auto;
}

.item-body {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
}

.metric-line .label {
  color: #94A3B8;
  margin-right: 4px;
}

.metric-line .val {
  color: #F87171;
  font-size: 13px;
}

.time-line {
  color: #64748B;
}

.item-footer {
  margin-top: 4px;
  font-size: 11px;
  display: flex;
  gap: 4px;
  color: #CBD5E1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rca-label {
  color: #38BDF8;
  font-weight: 500;
  flex-shrink: 0;
}

.rca-content {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
