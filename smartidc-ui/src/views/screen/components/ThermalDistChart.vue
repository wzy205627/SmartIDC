<template>
  <div class="thermal-card">
    <div class="card-header">
      <span class="header-title">🌡️ 机架动环温区分布</span>
      <span class="header-sub font-mono">共 {{ totalRacks }} 台机柜</span>
    </div>
    <div class="chart-container" ref="chartRef"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, computed } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  coldCount: {
    type: Number,
    default: 5
  },
  warmCount: {
    type: Number,
    default: 2
  },
  hotCount: {
    type: Number,
    default: 1
  }
})

const chartRef = ref(null)
let chartInstance = null

const totalRacks = computed(() => {
  return props.coldCount + props.warmCount + props.hotCount
})

function initChart() {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value)
  renderChart()
}

function renderChart() {
  if (!chartInstance) return

  const data = [
    { value: props.coldCount, name: '正常冷区 (<26℃)', itemStyle: { color: '#10B981' } },
    { value: props.warmCount, name: '轻度温升 (26-30℃)', itemStyle: { color: '#F59E0B' } },
    { value: props.hotCount, name: '高温热岛 (≥30℃)', itemStyle: { color: '#EF4444' } }
  ]

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(15, 23, 42, 0.95)',
      borderColor: 'rgba(56, 189, 248, 0.4)',
      textStyle: { color: '#F8FAFC', fontSize: 12 },
      formatter: '{b}: <strong style="color:#38BDF8">{c} 台 ({d}%)</strong>'
    },
    legend: {
      orient: 'vertical',
      right: '2%',
      top: 'middle',
      textStyle: {
        color: '#94A3B8',
        fontSize: 11
      },
      itemWidth: 10,
      itemHeight: 10,
      itemGap: 8
    },
    series: [
      {
        name: '温区分布',
        type: 'pie',
        radius: ['52%', '78%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 4,
          borderColor: '#0F172A',
          borderWidth: 2
        },
        label: {
          show: false
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 12,
            fontWeight: 'bold',
            color: '#F8FAFC',
            formatter: '{c}台'
          }
        },
        labelLine: {
          show: false
        },
        data: data
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
  () => [props.coldCount, props.warmCount, props.hotCount],
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
.thermal-card {
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
  margin-bottom: 2px;
}

.header-title {
  font-size: 13px;
  font-weight: 600;
  color: #E2E8F0;
  letter-spacing: 0.5px;
}

.header-sub {
  font-size: 11px;
  color: #64748B;
}

.chart-container {
  width: 100%;
  height: 145px;
}
</style>
