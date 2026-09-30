<template>
  <view class="gauge-container">
    <view class="svg-wrapper">
      <svg class="gauge-svg" viewBox="0 0 120 120">
        <defs>
          <!-- 渐变底色定义 -->
          <linearGradient :id="`grad-${meterId}`" x1="0%" y1="100%" x2="100%" y2="0%">
            <stop offset="0%" :stop-color="gradientStart" />
            <stop offset="100%" :stop-color="gradientEnd" />
          </linearGradient>
        </defs>

        <!-- 底层背景灰色圆弧 (240度) -->
        <circle
          class="gauge-bg"
          cx="60"
          cy="60"
          r="48"
          fill="none"
          stroke="rgba(51, 65, 85, 0.4)"
          stroke-width="8"
          stroke-linecap="round"
          stroke-dasharray="201 100"
          transform="rotate(150 60 60)"
        />

        <!-- 动态数据有色进度圆弧 -->
        <circle
          v-if="status !== 'OFFLINE' && numericValue !== null"
          class="gauge-progress"
          cx="60"
          cy="60"
          r="48"
          fill="none"
          :stroke="`url(#grad-${meterId})`"
          stroke-width="8"
          stroke-linecap="round"
          :stroke-dasharray="`${strokeLength} 300`"
          transform="rotate(150 60 60)"
        />
      </svg>

      <!-- 中心数值与标题 -->
      <view class="gauge-center">
        <text v-if="status === 'OFFLINE' || numericValue === null" class="gauge-val offline-text">--</text>
        <view v-else class="gauge-val-row">
          <text class="gauge-val" :style="{ color: gradientEnd }">{{ displayValue }}</text>
          <text class="gauge-unit">{{ unit }}</text>
        </view>
        <text class="gauge-title">{{ title }}</text>
        <text v-if="status === 'OFFLINE'" class="status-sub offline-sub">离线哨兵</text>
        <text v-else class="status-sub" :style="{ color: gradientEnd }">{{ levelText }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = withDefaults(
  defineProps<{
    value?: number | string | null;
    min?: number;
    max?: number;
    unit?: string;
    title?: string;
    theme?: 'temp' | 'humidity' | 'power';
    status?: string;
  }>(),
  {
    value: 0,
    min: 0,
    max: 50,
    unit: '℃',
    title: '动环指标',
    theme: 'temp',
    status: 'ONLINE'
  }
);

const meterId = computed(() => `${props.theme}-${Math.floor(Math.random() * 10000)}`);

const numericValue = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') return null;
  const num = Number(props.value);
  return isNaN(num) ? null : num;
});

const displayValue = computed(() => {
  if (numericValue.value === null) return '--';
  return numericValue.value.toFixed(1);
});

// 计算 240 度弧长所占进度 (总周长约为 2 * PI * 48 * (240/360) ≈ 201)
const strokeLength = computed(() => {
  if (numericValue.value === null) return 0;
  const clamped = Math.min(props.max, Math.max(props.min, numericValue.value));
  const ratio = (clamped - props.min) / (props.max - props.min);
  return (ratio * 201).toFixed(1);
});

// 动态颜色方案
const levelText = computed(() => {
  if (props.theme === 'temp') {
    const val = numericValue.value ?? 0;
    if (val >= 32) return '超温高危';
    if (val >= 28) return '微温预警';
    return '状态优良';
  }
  if (props.theme === 'humidity') {
    const val = numericValue.value ?? 0;
    if (val < 30) return '干燥预警';
    if (val > 70) return '高湿预警';
    return '湿度舒适';
  }
  return '运行正常';
});

const gradientStart = computed(() => {
  if (props.theme === 'temp') {
    const val = numericValue.value ?? 0;
    if (val >= 32) return '#f97316';
    if (val >= 28) return '#eab308';
    return '#06b6d4';
  }
  if (props.theme === 'humidity') {
    const val = numericValue.value ?? 0;
    if (val < 30 || val > 70) return '#f59e0b';
    return '#3b82f6';
  }
  return '#10b981';
});

const gradientEnd = computed(() => {
  if (props.theme === 'temp') {
    const val = numericValue.value ?? 0;
    if (val >= 32) return '#ef4444';
    if (val >= 28) return '#f59e0b';
    return '#10b981';
  }
  if (props.theme === 'humidity') {
    const val = numericValue.value ?? 0;
    if (val < 30 || val > 70) return '#ef4444';
    return '#06b6d4';
  }
  return '#34d399';
});
</script>

<style scoped>
.gauge-container {
  display: flex;
  align-items: center;
  justify-content: center;
}

.svg-wrapper {
  position: relative;
  width: 240rpx;
  height: 240rpx;
}

.gauge-svg {
  width: 100%;
  height: 100%;
  transform-origin: center;
}

.gauge-progress {
  transition: stroke-dasharray 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}

.gauge-center {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-top: 16rpx;
}

.gauge-val-row {
  display: flex;
  align-items: baseline;
}

.gauge-val {
  font-size: 40rpx;
  font-weight: 700;
  line-height: 1;
}

.offline-text {
  color: #64748b;
  font-size: 44rpx;
}

.gauge-unit {
  font-size: 20rpx;
  color: #94a3b8;
  margin-left: 4rpx;
}

.gauge-title {
  font-size: 20rpx;
  color: #94a3b8;
  margin-top: 6rpx;
}

.status-sub {
  font-size: 18rpx;
  font-weight: 600;
  margin-top: 2rpx;
}

.offline-sub {
  color: #eab308;
}
</style>
