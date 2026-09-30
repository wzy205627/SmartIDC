<template>
  <div class="trend-card">
    <div class="card-header">
      <span class="header-title">📈 机房总用电功耗趋势 (24h)</span>
      <span class="header-tag font-mono">峰值: {{ peakPower }} kW</span>
    </div>
    <div class="chart-container" ref="chartRef"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  currentPower: {
    type: Number,
    default: 25.4
  }
})

const chartRef = ref(null)
let chartInstance = null
const peakPower = ref(38.6)

function generate24hData(curPower) {
  const hours = []
  const values = []
  const now = new Date()
  const currentHour = now.getHours()

  for (let i = 23; i >= 0; i--) {
    const h = (currentHour - i + 24) % 24
    hours.push(`${h < 10 ? '0' + h : h}:00`)
    
    // 模拟 IDC 负荷昼夜周期波动曲线
    let base = 20.0
    if (h >= 9 && h <= 18) {
      base = 28.0 + Math.sin(h / 3) * 6
    } else if (h >= 19 && h <= 23) {
      base = 24.0 + Math.cos(h / 2) * 4
    } else {
      base = 16.0 + Math.random() * 3
    }
    // 当前小时对齐实时功率
    if (i === 0 && curPower > 0) {
      base = curPower
    }
    values.push(Number(base.toFixed(2)))
  }

  peakPower.value = Math.max(...values, curPower).toFixed(1)
  return { hours, values }
}

function initChart() {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value)
  renderChart()
}

function renderChart() {
  if (!chartInstance) return

  const { hours, values } = generate24hData(props.currentPower)

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(15, 23, 42, 0.9)',
      borderColor: 'rgba(56, 189, 248, 0.4)',
      textStyle: {
        color: '#F8FAFC',
        fontSize: 12
      },
      formatter: function (params) {
        const item = params[0]
        return `<span style="color:#94A3B8">${item.axisValue}</span><br/><strong style="color:#38BDF8">${item.seriesName}: ${item.value} kW</strong>`
      }
    },
    grid: {
      left: '12%',
      right: '6%',
      top: '15%',
      bottom: '18%'
    },
    xAxis: {
      type: 'category',
      data: hours,
      boundaryGap: false,
      axisLine: {
        lineStyle: { color: 'rgba(255, 255, 255, 0.15)' }
      },
      axisLabel: {
        color: '#94A3B8',
        fontSize: 10,
        interval: 5
      }
    },
    yAxis: {
      type: 'value',
      name: 'kW',
      nameTextStyle: {
        color: '#64748B',
        fontSize: 10
      },
      splitLine: {
        lineStyle: {
          color: 'rgba(255, 255, 255, 0.06)',
          type: 'dashed'
        }
      },
      axisLabel: {
        color: '#94A3B8',
        fontSize: 10
      }
    },
    series: [
      {
        name: '机房总功率',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: values,
        lineStyle: {
          color: '#38BDF8',
          width: 2.5
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(56, 189, 248, 0.45)' },
            { offset: 1, color: 'rgba(56, 189, 248, 0.02)' }
          ])
        }
      }
    ]
  }

  chartInstance.setOption(option)
}

function handleResize() {
  if (chartInstance) {
    chartInstance.resize()
  }
}

watch(
  () => props.currentPower,
  () => {
    renderChart()
  }
)

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<style scoped>
.trend-card {
  background: rgba(15, 23, 42, 0.75);
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-radius: 8px;
  padding: 12px 14px;
  backdrop-filter: blur(10px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.header-title {
  font-size: 13px;
  font-weight: 600;
  color: #E2E8F0;
  letter-spacing: 0.5px;
}

.header-tag {
  font-size: 11px;
  color: #38BDF8;
  background: rgba(56, 189, 248, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid rgba(56, 189, 248, 0.2);
}

.chart-container {
  width: 100%;
  height: 155px;
}
</style>
