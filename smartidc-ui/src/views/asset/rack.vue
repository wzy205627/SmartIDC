<template>
  <div class="rack-container">
    <el-card shadow="never">
      <!-- 顶部多维筛选栏 -->
      <div class="filter-bar">
        <el-form :inline="true" :model="queryParams" class="demo-form-inline">
          <el-form-item label="机柜编号">
            <el-input
              v-model="queryParams.rackCode"
              placeholder="如: A-01 / B-02"
              clearable
              style="width: 170px"
              @keyup.enter="fetchData"
            />
          </el-form-item>
          <el-form-item label="所属区域">
            <el-input
              v-model="queryParams.roomName"
              placeholder="如: 华东 / B区 / 华东01-A区"
              clearable
              style="width: 200px"
              @keyup.enter="fetchData"
            />
          </el-form-item>
          <el-form-item label="资产状态">
            <el-select
              v-model="queryParams.status"
              placeholder="全部状态"
              clearable
              style="width: 140px"
              @change="fetchData"
            >
              <el-option label="全部状态" :value="null" />
              <el-option label="空闲可用" :value="0" />
              <el-option label="托管使用中" :value="1" />
              <el-option label="维保锁定" :value="2" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="fetchData">查询机柜</el-button>
            <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 机架资产表格 -->
      <el-table :data="rackList" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="rackId" label="ID" width="70" align="center" />
        <el-table-column prop="rackCode" label="机柜编号" width="130" align="center">
          <template #default="{ row }">
            <el-tag effect="dark" type="primary" class="rack-badge">{{ row.rackCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="roomName" label="所属机房区域" min-width="150" />
        <el-table-column prop="tenantId" label="租户归属" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.tenantId === '000000' ? 'info' : 'warning'">
              {{ row.tenantId === '000000' ? '平台自营' : row.tenantId }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="U位占用 (已用 / 总额)" min-width="200">
          <template #default="{ row }">
            <div class="u-progress-wrapper">
              <el-progress
                :percentage="Math.round((row.usedU / row.totalU) * 100)"
                :status="getUProgressStatus(row.usedU, row.totalU)"
              />
              <span class="u-desc">{{ row.usedU }}U / {{ row.totalU }}U</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="powerRating" label="额定容量" width="120" align="right">
          <template #default="{ row }">
            <span>{{ Number(row.powerRating).toFixed(2) }} kVA</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="资产状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">托管使用中</el-tag>
            <el-tag v-else-if="row.status === 0" type="info">空闲可用</el-tag>
            <el-tag v-else type="danger">维保锁定</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" align="center" />
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleViewDetail(row)">
              U位画像
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="未检索到匹配的机柜资产，请检查机柜编号或所属区域" :image-size="80" />
        </template>
      </el-table>
    </el-card>

    <!-- 42U 可视化机柜详情抽屉 -->
    <el-drawer
      v-model="detailVisible"
      :title="`机柜 42U 物理插槽全景仿真 [${currentRack?.rackCode || ''}]`"
      size="720px"
      destroy-on-close
      class="rack-detail-drawer"
    >
      <div v-if="currentRack" class="drawer-content">
        <CabinetRackView :rack="currentRack" @refresh="onRackUpdated" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { listRacks } from '@/api/rack'
import { ElMessage } from 'element-plus'
import CabinetRackView from '@/components/CabinetRackView.vue'

const loading = ref(false)
const rackList = ref([])
const queryParams = ref({
  rackCode: '',
  roomName: '',
  status: null
})

const detailVisible = ref(false)
const currentRack = ref(null)

async function fetchData() {
  loading.value = true
  try {
    const data = await listRacks(queryParams.value)
    rackList.value = data || []
  } catch (err) {
    ElMessage.error('获取机架列表失败，请确认后端 8080 是否运行正常')
  } finally {
    loading.value = false
  }
}

async function onRackUpdated() {
  await fetchData()
  if (currentRack.value) {
    const updated = rackList.value.find(r => r.rackId === currentRack.value.rackId)
    if (updated) {
      currentRack.value = updated
    }
  }
}

function resetQuery() {
  queryParams.value.rackCode = ''
  queryParams.value.roomName = ''
  queryParams.value.status = null
  fetchData()
}

function getUProgressStatus(used, total) {
  const rate = used / total
  if (rate >= 0.8) return 'exception'
  if (rate >= 0.5) return 'warning'
  return 'success'
}

function handleViewDetail(row) {
  currentRack.value = row
  detailVisible.value = true
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.rack-container {
  padding-bottom: 20px;
}

.filter-bar {
  margin-bottom: 16px;
}

.rack-badge {
  font-weight: bold;
  letter-spacing: 1px;
}

.u-progress-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
}

.u-desc {
  font-size: 12px;
  color: #909399;
  min-width: 65px;
}

.mt-20 {
  margin-top: 20px;
}

/* 抽屉全景视图样式：彻底消除最外层滚动条 */
:deep(.rack-detail-drawer) {
  background: #0b0f19;
}

:deep(.rack-detail-drawer .el-drawer__header) {
  margin-bottom: 0;
  padding: 14px 20px;
  background: #0f172a;
  border-bottom: 1px solid #1e293b;
  color: #f8fafc;
}

:deep(.rack-detail-drawer .el-drawer__title) {
  color: #f8fafc;
  font-weight: 600;
  font-size: 15px;
  letter-spacing: 0.5px;
}

:deep(.rack-detail-drawer .el-drawer__close-btn) {
  color: #94a3b8;
  font-size: 18px;
}

:deep(.rack-detail-drawer .el-drawer__close-btn:hover) {
  color: #38bdf8;
}

:deep(.rack-detail-drawer .el-drawer__body) {
  padding: 0 !important;
  overflow: hidden !important; /* 彻底去除右侧最外层滑栏 */
  height: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.drawer-content {
  flex: 1;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>
