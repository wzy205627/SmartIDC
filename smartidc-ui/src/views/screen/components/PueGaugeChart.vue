<template>
  <div class="pue-card">
    <div class="card-header">
      <span class="header-title">⚡ 实时能效比 (PUE 仪表盘)</span>
      <el-tag size="small" :type="pueTagType" effect="dark">{{ pueGradeText }}</el-tag>
    </div>
    <div class="chart-container" ref="chartRef"></div>
    <div class="pue-footer-metrics">
      <div class="metric-block">
        <span class="m-label">IT 设备总功耗</span>
        <span class="m-val font-mono">{{ itPower.toFixed(2) }} <small>kW</small></span>
      </div>
      <div class="metric-divider"></div>
      <div class="metric-block">
        <span class="m-label">动环公摊负荷</span>
        <span class="m-val font-mono">{{ coolingPower.toFixed(2) }} <small>kW</small></span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, computed } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  pueValue: {
    type: Number,
    default: 1.28
  },
  itPower: {
    type: Number,
    default: 24.5
  },
  coolingPower: {
    type: Number,
    default: 6.8
  }
})

const chartRef = ref(null)
let chartInstance = null

const pueTagType = computed(() => {
  if (props.pueValue <= 1.30) return 'success'
  if (props.pueValue <= 1.50) return 'warning'
  return 'danger'
})

const pueGradeText = computed(() => {
  if (props.pueValue <= 1.30) return '能效优异 (Tier IV)'
  if (props.pueValue <= 1.50) return '能效达标'
  return '能效偏低'
})

function initChart() {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value)
  updateChart()
}

function updateChart() {
  if (!chartInstance) return

  const option = {
    backgroundColor: 'transparent',
    series: [
      {
        type: 'gauge',
        startAngle: 200,
        endAngle: -20,
        min: 1.0,
        max: 2.0,
        splitNumber: 5,
        radius: '95%',
        center: ['50%', '58%'],
        axisLine: {
          lineStyle: {
            width: 12,
            color: [
              [0.3, '#10B981'], // 1.0 ~ 1.3 极佳
              [0.5, '#F59E0B'], // 1.3 ~ 1.5 预警
              [1.0, '#EF4444']  // 1.5 ~ 2.0 偏高
            ]
          }
        },
        pointer: {
          icon: 'triangle',
          length: '55%',
          width: 6,
          offsetCenter: [0, '5%'],
          itemStyle: {
            color: '#38BDF8',
            shadowColor: 'rgba(56, 189, 248, 0.8)',
            shadowBlur: 8
          }
        },
        axisTick: {
          distance: -14,
          length: 5,
          lineStyle: {
            color: '#94A3B8',
            width: 1
          }
        },
        splitLine: {
          distance: -18,
          length: 10,
          lineStyle: {
            color: '#CBD5E1',
            width: 2
          }
        },
        axisLabel: {
          distance: -20,
          color: '#94A3B8',
          fontSize: 10,
          formatter: function (value) {
            return value.toFixed(1)
          }
        },
        detail: {
          valueAnimation: true,
          formatter: '{value}',
          color: '#F8FAFC',
          fontSize: 22,
          fontWeight: 'bold',
          offsetCenter: [0, '40%']
        },
        data: [
          {
            value: Number(props.pueValue.toFixed(2)),
            name: 'PUE'
          }
        ],
        title: {
          offsetCenter: [0, '70%'],
          color: '#64748B',
          fontSize: 11
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
  () => props.pueValue,
  () => {
    updateChart()
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
.pue-card {
  background: rgba(15, 23, 42, 0.75);
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-radius: 8px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  backdrop-filter: blur(10px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2px;
}

.header-title {
  font-size: 13px;
  font-weight: 600;
  color: #E2E8F0;
  letter-spacing: 0.5px;
}

.chart-container {
  width: 100%;
  height: 170px;
}

.pue-footer-metrics {
  display: flex;
  justify-content: space-around;
  align-items: center;
  background: rgba(30, 41, 59, 0.6);
  border-radius: 6px;
  padding: 8px 10px;
  margin-top: -6px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.metric-block {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.m-label {
  font-size: 11px;
  color: #94A3B8;
  margin-bottom: 2px;
}

.m-val {
  font-size: 14px;
  font-weight: 700;
  color: #38BDF8;
}

.m-val small {
  font-size: 10px;
  color: #64748B;
  font-weight: normal;
}

.metric-divider {
  width: 1px;
  height: 24px;
  background: rgba(255, 255, 255, 0.1);
}
</style>
