<template>
  <div class="telemetry-timeline-chart-wrap">
    <!-- 头部控制栏：第一行标题与时间跨度 -->
    <div class="timeline-header-row">
      <div class="title-box">
        <span class="icon">📈</span>
        <span class="title">动环多轴时序回溯折线图</span>
        <span class="sub-tip" v-if="timelineData?.bucketStepSeconds">
          ({{ timelineData.bucketStepSeconds }}s步长 / {{ dataPointCount }}采样点)
        </span>
      </div>

      <!-- 时间跨度胶囊切换 -->
      <div class="time-range-capsules">
        <button
          v-for="item in timeRanges"
          :key="item.value"
          class="capsule-btn"
          :class="{ active: currentTimeRange === item.value }"
          @click="changeTimeRange(item.value)"
        >
          {{ item.label }}
        </button>
      </div>
    </div>

    <!-- 头部控制栏：第二行物理量分组标签 -->
    <div class="metric-filter-row">
      <div class="metric-tabs">
        <button
          class="tab-btn"
          :class="{ active: metricMode === 'tempHum' }"
          @click="changeMetricMode('tempHum')"
        >
          🔥 温湿度折线 (℃ / %)
        </button>
        <button
          class="tab-btn"
          :class="{ active: metricMode === 'power' }"
          @click="changeMetricMode('power')"
        >
          ⚡ 电力负荷折线 (kW / A)
        </button>
        <button
          class="tab-btn"
          :class="{ active: metricMode === 'all' }"
          @click="changeMetricMode('all')"
        >
          📊 综合多轴全览
        </button>
      </div>
    </div>

    <!-- 图表渲染区域 -->
    <div class="chart-container" v-loading="loading" element-loading-background="rgba(15, 23, 42, 0.7)">
      <div v-if="!loading && (!timelineData || !timelineData.timestamps || timelineData.timestamps.length === 0)" class="chart-empty">
        <div class="empty-icon">📊</div>
        <div class="empty-text">该时间范围内暂无动环遥测快照数据</div>
      </div>
      <div ref="chartRef" class="chart-canvas" :style="{ visibility: (!loading && timelineData?.timestamps?.length > 0) ? 'visible' : 'hidden' }"></div>
    </div>

    <!-- 底部遥测特征统计概览 -->
    <div v-if="timelineData && timelineData.timestamps && timelineData.timestamps.length > 0" class="timeline-summary-bar">
      <div class="stat-pill">
        <span class="lbl">最高温度:</span>
        <span class="val danger">{{ maxTemp }} ℃</span>
      </div>
      <div class="stat-pill">
        <span class="lbl">平均温度:</span>
        <span class="val">{{ avgTemp }} ℃</span>
      </div>
      <div class="stat-pill">
        <span class="lbl">峰值功率:</span>
        <span class="val warning">{{ maxPower }} kW</span>
      </div>
      <div class="stat-pill">
        <span class="lbl">告警时段:</span>
        <span class="val" :class="alarmCount > 0 ? 'danger' : 'success'">{{ alarmCount }} 次</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getTelemetryTimeline } from '@/api/telemetry'

const props = defineProps({
  rackCode: {
    type: String,
    required: true
  },
  selectedAlarm: {
    type: Object,
    default: null
  }
})

const chartRef = ref(null)
let chartInstance = null
let resizeObserver = null

const loading = ref(false)
const currentTimeRange = ref('24h')
const metricMode = ref('tempHum') // 默认清晰呈现最核心的温湿度折线
const timelineData = ref(null)

const timeRanges = [
  { label: '1小时', value: '1h' },
  { label: '6小时', value: '6h' },
  { label: '24小时', value: '24h' },
  { label: '7天', value: '7d' }
]

const dataPointCount = computed(() => timelineData.value?.timestamps?.length || 0)

const maxTemp = computed(() => {
  const arr = timelineData.value?.series?.temperatureMax || timelineData.value?.series?.temperature || []
  if (!arr.length) return '--'
  const valid = arr.filter(v => v !== null && v !== undefined)
  return valid.length ? Math.max(...valid).toFixed(1) : '--'
})

const avgTemp = computed(() => {
  const arr = timelineData.value?.series?.temperature || []
  if (!arr.length) return '--'
  const valid = arr.filter(v => v !== null && v !== undefined)
  if (!valid.length) return '--'
  const sum = valid.reduce((a, b) => a + b, 0)
  return (sum / valid.length).toFixed(1)
})

const maxPower = computed(() => {
  const arr = timelineData.value?.series?.powerKw || []
  if (!arr.length) return '--'
  const valid = arr.filter(v => v !== null && v !== undefined)
  return valid.length ? Math.max(...valid).toFixed(2) : '--'
})

const alarmCount = computed(() => timelineData.value?.alarmIntervals?.length || 0)

/**
 * 加载时序降采样数据
 */
async function loadData() {
  if (!props.rackCode) return
  loading.value = true
  try {
    const res = await getTelemetryTimeline(props.rackCode, { timeRange: currentTimeRange.value })
    const data = res?.data !== undefined ? res.data : res
    timelineData.value = data || null
    nextTick(() => {
      renderChart()
      if (props.selectedAlarm) {
        focusOnAlarm(props.selectedAlarm)
      }
    })
  } catch (err) {
    console.error('[TelemetryTimeline] 加载动环时序失败:', err)
  } finally {
    loading.value = false
  }
}

/**
 * 切换物理量模式
 */
function changeMetricMode(mode) {
  if (metricMode.value === mode) return
  metricMode.value = mode
  renderChart()
}

/**
 * 切换时间范围
 */
function changeTimeRange(range) {
  if (currentTimeRange.value === range) return
  currentTimeRange.value = range
  loadData()
}

/**
 * 渲染 ECharts 纯折线图
 */
function renderChart() {
  if (!chartRef.value) return
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }

  if (!timelineData.value || !timelineData.value.timestamps || timelineData.value.timestamps.length === 0) {
    chartInstance.clear()
    return
  }

  const timestamps = timelineData.value.timestamps
  const seriesData = timelineData.value.series || {}
  const alarmIntervals = timelineData.value.alarmIntervals || []

  // 构建 MarkArea 高亮越限告警区间
  const markAreaData = []
  if (alarmIntervals.length > 0) {
    alarmIntervals.forEach(interval => {
      if (!interval.startTime) return
      const isCritical = interval.alarmLevel === 'CRITICAL'
      const startX = interval.startTime
      const endX = interval.endTime || (timestamps.length > 0 ? timestamps[timestamps.length - 1] : startX)
      const labelText = isCritical
        ? `🚨 严重告警 (${interval.metricValue || '越限'})`
        : `⚠️ 预警关注 (${interval.metricValue || '越限'})`

      markAreaData.push([
        {
          name: labelText,
          xAxis: startX,
          itemStyle: {
            color: isCritical ? 'rgba(244, 63, 94, 0.18)' : 'rgba(245, 158, 11, 0.15)'
          },
          label: {
            position: 'insideTop',
            distance: 8,
            color: isCritical ? '#f43f5e' : '#fbbf24',
            fontSize: 11,
            fontWeight: 600
          }
        },
        {
          xAxis: endX
        }
      ])
    })
  }

  // 纯折线图 Series 构建 (无阴影面积，纯净线条)
  const series = []

  // 1. 温湿度组
  if (metricMode.value === 'tempHum' || metricMode.value === 'all') {
    series.push({
      name: '进风温度 (℃)',
      type: 'line',
      yAxisIndex: 0,
      data: seriesData.temperature || [],
      smooth: 0.25,
      showSymbol: false,
      symbol: 'circle',
      symbolSize: 6,
      itemStyle: { color: '#f43f5e' },
      lineStyle: { width: 2.2, color: '#f43f5e' },
      markPoint: {
        symbol: 'pin',
        symbolSize: 40,
        data: [
          { type: 'max', name: '最高温', itemStyle: { color: '#f43f5e' } },
          { type: 'min', name: '最低温', itemStyle: { color: '#0ea5e9' } }
        ],
        label: {
          fontSize: 10,
          formatter: (p) => `${p.value}℃`
        }
      },
      markLine: {
        symbol: 'none',
        silent: true,
        data: [
          {
            yAxis: 26.0,
            lineStyle: { type: 'dashed', color: '#10b981', width: 1.2 },
            label: { formatter: '26℃ 安全基线', position: 'insideEndTop', color: '#10b981', fontSize: 10 }
          },
          {
            yAxis: 35.0,
            lineStyle: { type: 'dashed', color: '#f43f5e', width: 1.5 },
            label: { formatter: '35℃ 严重超温', position: 'insideEndTop', color: '#f43f5e', fontSize: 10 }
          }
        ]
      },
      markArea: markAreaData.length > 0 ? { data: markAreaData } : undefined
    })

    series.push({
      name: '相对湿度 (%RH)',
      type: 'line',
      yAxisIndex: 0,
      data: seriesData.humidity || [],
      smooth: 0.25,
      showSymbol: false,
      symbol: 'circle',
      symbolSize: 6,
      itemStyle: { color: '#0ea5e9' },
      lineStyle: { width: 2, color: '#0ea5e9' }
    })
  }

  // 2. 电力负荷组
  if (metricMode.value === 'power' || metricMode.value === 'all') {
    const powerYIndex = metricMode.value === 'all' ? 1 : 0
    series.push({
      name: '总负荷功率 (kW)',
      type: 'line',
      yAxisIndex: powerYIndex,
      data: seriesData.powerKw || [],
      smooth: 0.25,
      showSymbol: false,
      symbol: 'circle',
      symbolSize: 6,
      itemStyle: { color: '#10b981' },
      lineStyle: { width: 2.2, color: '#10b981' },
      markArea: (metricMode.value === 'power' && markAreaData.length > 0) ? { data: markAreaData } : undefined
    })

    series.push({
      name: '工作电流 (A)',
      type: 'line',
      yAxisIndex: powerYIndex,
      data: seriesData.currentAmp || [],
      smooth: 0.25,
      showSymbol: false,
      symbol: 'circle',
      symbolSize: 6,
      itemStyle: { color: '#8b5cf6' },
      lineStyle: { width: 1.8, type: 'dashed', color: '#8b5cf6' }
    })
  }

  // 坐标轴与布局配置 (彻底消除重叠)
  const isDualAxis = metricMode.value === 'all'

  const yAxis = [
    {
      type: 'value',
      name: metricMode.value === 'power' ? 'kW / A' : '℃ / %',
      nameTextStyle: { color: '#94a3b8', fontSize: 10, align: 'left', padding: [0, 0, 0, -8] },
      position: 'left',
      min: (value) => Math.max(0, Math.floor(Math.min(15, value.min - 2))),
      max: (value) => Math.ceil(Math.max(metricMode.value === 'power' ? 8 : 45, value.max + 2)),
      axisLine: { show: true, lineStyle: { color: 'rgba(255, 255, 255, 0.2)' } },
      axisLabel: { color: '#94a3b8', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.05)' } }
    }
  ]

  if (isDualAxis) {
    yAxis.push({
      type: 'value',
      name: 'kW / A',
      nameTextStyle: { color: '#94a3b8', fontSize: 10, align: 'right', padding: [0, -8, 0, 0] },
      position: 'right',
      min: 0,
      max: (value) => Math.ceil(Math.max(8, value.max * 1.15)),
      axisLine: { show: true, lineStyle: { color: 'rgba(255, 255, 255, 0.2)' } },
      axisLabel: { color: '#94a3b8', fontSize: 10 },
      splitLine: { show: false }
    })
  }

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(15, 23, 42, 0.95)',
      borderColor: 'rgba(56, 189, 248, 0.35)',
      textStyle: { color: '#f8fafc', fontSize: 12 },
      axisPointer: {
        type: 'cross',
        crossStyle: { color: 'rgba(56, 189, 248, 0.4)' },
        lineStyle: { color: 'rgba(56, 189, 248, 0.3)', type: 'dashed' }
      }
    },
    legend: {
      top: 4,
      left: 'center',
      textStyle: { color: '#cbd5e1', fontSize: 11 },
      itemWidth: 16,
      itemHeight: 8,
      itemGap: 14
    },
    grid: {
      top: 52,
      left: 42,
      right: isDualAxis ? 42 : 24,
      bottom: 42,
      containLabel: false
    },
    xAxis: {
      type: 'category',
      data: timestamps,
      boundaryGap: false,
      axisLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.15)' } },
      axisLabel: {
        color: '#94a3b8',
        fontSize: 10,
        formatter: (value) => {
          if (!value) return ''
          return value.length >= 16 ? value.substring(5, 16) : value
        }
      },
      splitLine: {
        show: true,
        lineStyle: { color: 'rgba(255, 255, 255, 0.05)', type: 'dashed' }
      }
    },
    yAxis,
    dataZoom: [
      {
        type: 'inside',
        start: 0,
        end: 100
      },
      {
        type: 'slider',
        bottom: 4,
        height: 16,
        start: 0,
        end: 100,
        borderColor: 'rgba(255, 255, 255, 0.12)',
        backgroundColor: 'rgba(15, 23, 42, 0.7)',
        fillerColor: 'rgba(6, 182, 212, 0.25)',
        handleSize: '120%',
        handleStyle: {
          color: '#06b6d4',
          borderColor: '#38bdf8'
        },
        textStyle: { color: '#64748b', fontSize: 9 }
      }
    ],
    series
  }

  chartInstance.setOption(option, true)
}

/**
 * 聚焦定位到指定告警的时刻窗口
 */
function focusOnAlarm(alarm) {
  if (!alarm || !alarm.triggerTime || !chartInstance || !timelineData.value?.timestamps) return
  const timestamps = timelineData.value.timestamps
  if (!timestamps.length) return

  const targetTime = alarm.triggerTime
  let bestIdx = 0
  let minDiff = Infinity

  const targetDate = new Date(targetTime.replace(' ', 'T')).getTime()
  timestamps.forEach((ts, idx) => {
    const d = new Date(ts.replace(' ', 'T')).getTime()
    const diff = Math.abs(d - targetDate)
    if (diff < minDiff) {
      minDiff = diff
      bestIdx = idx
    }
  })

  const total = timestamps.length
  const halfWindow = Math.max(5, Math.floor(total * 0.15))
  const startIdx = Math.max(0, bestIdx - halfWindow)
  const endIdx = Math.min(total - 1, bestIdx + halfWindow)

  const startPercent = Math.floor((startIdx / total) * 100)
  const endPercent = Math.ceil((endIdx / total) * 100)

  chartInstance.dispatchAction({
    type: 'dataZoom',
    start: startPercent,
    end: endPercent
  })
}

watch(() => props.rackCode, () => {
  loadData()
})

watch(() => props.selectedAlarm, (newAlarm) => {
  if (newAlarm) {
    focusOnAlarm(newAlarm)
  }
})

onMounted(() => {
  loadData()
  if (chartRef.value) {
    resizeObserver = new ResizeObserver(() => {
      chartInstance?.resize()
    })
    resizeObserver.observe(chartRef.value)
  }
  window.addEventListener('resize', handleWindowResize)
})

function handleWindowResize() {
  chartInstance?.resize()
}

onBeforeUnmount(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  window.removeEventListener('resize', handleWindowResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<style scoped>
.telemetry-timeline-chart-wrap {
  display: flex;
  flex-direction: column;
  background: rgba(15, 23, 42, 0.75);
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-radius: 8px;
  padding: 12px 14px 10px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.35);
  margin-bottom: 16px;
}

.timeline-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.title-box {
  display: flex;
  align-items: center;
  gap: 6px;
}

.title-box .icon {
  font-size: 15px;
}

.title-box .title {
  font-size: 13px;
  font-weight: 600;
  color: #f1f5f9;
  letter-spacing: 0.5px;
}

.title-box .sub-tip {
  font-size: 11px;
  color: #94a3b8;
}

.time-range-capsules {
  display: flex;
  background: rgba(30, 41, 59, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 4px;
  padding: 2px;
  gap: 2px;
}

.capsule-btn {
  background: transparent;
  border: none;
  color: #94a3b8;
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 3px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.capsule-btn:hover {
  color: #38bdf8;
}

.capsule-btn.active {
  background: #0ea5e9;
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(14, 165, 233, 0.4);
}

.metric-filter-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.metric-tabs {
  display: flex;
  gap: 6px;
  width: 100%;
}

.tab-btn {
  flex: 1;
  background: rgba(30, 41, 59, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  color: #94a3b8;
  font-size: 11px;
  padding: 4px 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: center;
}

.tab-btn:hover {
  border-color: rgba(56, 189, 248, 0.4);
  color: #e2e8f0;
}

.tab-btn.active {
  background: rgba(14, 165, 233, 0.18);
  border-color: #0ea5e9;
  color: #38bdf8;
  font-weight: 600;
}

.chart-container {
  position: relative;
  width: 100%;
  height: 250px;
}

.chart-canvas {
  width: 100%;
  height: 100%;
}

.chart-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #64748b;
  gap: 6px;
}

.chart-empty .empty-icon {
  font-size: 28px;
}

.chart-empty .empty-text {
  font-size: 12px;
}

.timeline-summary-bar {
  display: flex;
  align-items: center;
  justify-content: space-around;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.stat-pill {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
}

.stat-pill .lbl {
  color: #94a3b8;
}

.stat-pill .val {
  font-weight: 600;
  color: #e2e8f0;
}

.stat-pill .val.danger {
  color: #f43f5e;
}

.stat-pill .val.warning {
  color: #fbbf24;
}

.stat-pill .val.success {
  color: #10b981;
}
</style>
