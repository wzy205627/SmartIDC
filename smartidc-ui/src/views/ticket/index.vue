<template>
  <div class="ticket-center-container">
    <!-- 1. 顶部 5 大态势看板卡片 -->
    <div class="stats-overview-grid">
      <div class="stat-card total">
        <div class="stat-left">
          <div class="lbl">工单总数</div>
          <div class="val">{{ stats.totalCount }}</div>
        </div>
        <div class="stat-icon">📋</div>
      </div>

      <div class="stat-card pending">
        <div class="stat-left">
          <div class="lbl">待分配工单</div>
          <div class="val">{{ stats.createdCount }}</div>
        </div>
        <div class="stat-icon">⏳</div>
      </div>

      <div class="stat-card processing">
        <div class="stat-left">
          <div class="lbl">现场排障中</div>
          <div class="val">{{ stats.processingCount + stats.assignedCount }}</div>
        </div>
        <div class="stat-icon">🚀</div>
      </div>

      <div class="stat-card review">
        <div class="stat-left">
          <div class="lbl">待消警复核</div>
          <div class="val">{{ stats.resolvedCount }}</div>
        </div>
        <div class="stat-icon">🔍</div>
      </div>

      <div class="stat-card completed">
        <div class="stat-left">
          <div class="lbl">已办结归档</div>
          <div class="val">{{ stats.completedCount }}</div>
        </div>
        <div class="stat-icon">✅</div>
      </div>
    </div>

    <!-- 2. 工单台账卡片与过滤控制栏 -->
    <el-card class="ticket-table-card" shadow="never">
      <div class="table-toolbar">
        <!-- 状态分类 Tabs -->
        <el-radio-group v-model="filterStatus" size="default" @change="handleFilterChange">
          <el-radio-button :label="null">全部工单</el-radio-button>
          <el-radio-button :label="0">待分配</el-radio-button>
          <el-radio-button :label="1">已指派</el-radio-button>
          <el-radio-button :label="2">排障中</el-radio-button>
          <el-radio-button :label="6">待复核</el-radio-button>
          <el-radio-button :label="7">已办结</el-radio-button>
        </el-radio-group>

        <!-- 搜索与类型过滤 -->
        <div class="toolbar-right">
          <el-select
            v-model="filterType"
            placeholder="工单类型"
            clearable
            style="width: 140px;"
            @change="handleFilterChange"
          >
            <el-option label="故障排障" value="ALARM_REPAIR" />
            <el-option label="常规巡检" value="ROUTINE_CHECK" />
            <el-option label="资产移机" value="ASSET_MOVE" />
          </el-select>

          <el-input
            v-model="keyword"
            placeholder="搜索工单号/标题/机柜..."
            clearable
            style="width: 220px;"
            @keyup.enter="handleFilterChange"
            @clear="handleFilterChange"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>

          <el-button type="primary" plain @click="loadData">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </div>

      <!-- 3. 工单表格 -->
      <el-table
        :data="ticketList"
        v-loading="loading"
        style="width: 100%; margin-top: 16px;"
        stripe
        class="ticket-table"
      >
        <el-table-column label="工单流水号" prop="ticketNo" width="160">
          <template #default="{ row }">
            <span class="ticket-no-link" @click="openDrawer(row)">{{ row.ticketNo }}</span>
          </template>
        </el-table-column>

        <el-table-column label="工单标题" prop="title" min-width="220" show-overflow-tooltip />

        <el-table-column label="类型" prop="ticketType" width="110">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ getTicketTypeName(row.ticketType) }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="故障机柜" prop="rackCode" width="110">
          <template #default="{ row }">
            <span class="rack-badge">{{ row.rackCode || '--' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="关联告警采样" width="150">
          <template #default="{ row }">
            <span v-if="row.alarmMetricValue" class="metric-val-text">
              <span class="dot" :class="row.alarmLevel === 'CRITICAL' ? 'crit' : 'warn'"></span>
              {{ row.alarmMetricValue }}
            </span>
            <span v-else class="text-gray">--</span>
          </template>
        </el-table-column>

        <el-table-column label="指派工程师" prop="operatorName" width="110">
          <template #default="{ row }">
            <strong>{{ row.operatorName || '未分配' }}</strong>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" effect="dark" size="small">
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" prop="createTime" width="170" />

        <el-table-column label="快捷操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 1"
              size="small"
              type="primary"
              link
              @click="quickStart(row)"
            >
              接单
            </el-button>
            <el-button
              v-else-if="row.status === 2"
              size="small"
              type="success"
              link
              @click="openDrawer(row)"
            >
              处置
            </el-button>
            <el-button
              v-else-if="row.status === 6"
              size="small"
              type="danger"
              link
              @click="openDrawer(row)"
            >
              复核办结
            </el-button>

            <el-button size="small" type="primary" link @click="openDrawer(row)">
              详情画像 ➔
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 4. 分页器 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 5. 工单全生命周期处置画像抽屉 -->
    <TicketProcessDrawer
      v-model="drawerVisible"
      :ticket="activeTicket"
      @ticket-updated="onTicketUpdated"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getWorkTicketPage, getWorkTicketStats, startWorkTicket } from '@/api/ticket'
import TicketProcessDrawer from './components/TicketProcessDrawer.vue'

const loading = ref(false)
const ticketList = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const filterStatus = ref(null)
const filterType = ref(null)
const keyword = ref('')

const stats = ref({
  totalCount: 0,
  createdCount: 0,
  assignedCount: 0,
  processingCount: 0,
  resolvedCount: 0,
  completedCount: 0,
  todayCount: 0
})

const drawerVisible = ref(false)
const activeTicket = ref(null)

onMounted(() => {
  loadStats()
  loadData()
})

async function loadStats() {
  try {
    const res = await getWorkTicketStats()
    const data = res?.data !== undefined ? res.data : res
    if (data) stats.value = data
  } catch (err) {
    console.error('加载工单统计失败:', err)
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getWorkTicketPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      status: filterStatus.value,
      ticketType: filterType.value,
      keyword: keyword.value
    })
    const data = res?.data !== undefined ? res.data : res
    if (data) {
      ticketList.value = data.records || []
      total.value = data.total || 0
    }
  } catch (err) {
    console.error('加载工单列表失败:', err)
  } finally {
    loading.value = false
  }
}

function handleFilterChange() {
  pageNum.value = 1
  loadData()
}

function openDrawer(row) {
  activeTicket.value = { ...row }
  drawerVisible.value = true
}

function onTicketUpdated(updated) {
  if (updated) {
    activeTicket.value = updated
  }
  loadStats()
  loadData()
}

async function quickStart(row) {
  try {
    const res = await startWorkTicket(row.ticketId)
    ElMessage.success(`工单【${row.ticketNo}】已接单，进入现场排障中！`)
    loadStats()
    loadData()
  } catch (err) {
    ElMessage.error('接单操作失败')
  }
}

function getStatusTagType(status) {
  switch (status) {
    case 0: return 'info'
    case 1: return 'warning'
    case 2: return 'primary'
    case 6: return 'danger'
    case 7: return 'success'
    default: return ''
  }
}

function getTicketTypeName(type) {
  switch (type) {
    case 'ALARM_REPAIR': return '故障排障'
    case 'ROUTINE_CHECK': return '常规巡检'
    case 'ASSET_MOVE': return '资产移机'
    default: return type || '运维工单'
  }
}
</script>

<style scoped>
.ticket-center-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stats-overview-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
}

.stat-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.stat-card .lbl {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 6px;
}

.stat-card .val {
  font-size: 24px;
  font-weight: bold;
  color: #1e293b;
}

.stat-card .stat-icon {
  font-size: 30px;
  opacity: 0.85;
}

.stat-card.pending {
  border-left: 4px solid #f59e0b;
}

.stat-card.processing {
  border-left: 4px solid #0284c7;
}

.stat-card.review {
  border-left: 4px solid #ef4444;
}

.stat-card.completed {
  border-left: 4px solid #10b981;
}

.ticket-table-card {
  border-radius: 8px;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ticket-no-link {
  font-family: monospace;
  font-weight: bold;
  color: #0284c7;
  cursor: pointer;
}

.ticket-no-link:hover {
  text-decoration: underline;
}

.rack-badge {
  font-family: monospace;
  font-weight: 600;
  color: #0f172a;
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
}

.metric-val-text {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #ef4444;
}

.metric-val-text .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.metric-val-text .dot.crit {
  background: #ef4444;
  box-shadow: 0 0 6px rgba(239, 68, 68, 0.6);
}

.metric-val-text .dot.warn {
  background: #f59e0b;
}

.text-gray {
  color: #94a3b8;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
