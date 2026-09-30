<template>
  <el-drawer
    v-model="visible"
    :title="`🛠️ 运维排障工单画像 [${ticket?.ticketNo || ''}]`"
    size="580px"
    direction="rtl"
    class="ticket-process-drawer"
    :z-index="2500"
    :destroy-on-close="false"
  >
    <div v-if="ticket" class="drawer-body">
      <!-- 1. 顶部工单状态横幅 -->
      <div class="ticket-hero-banner">
        <div class="hero-top">
          <div class="ticket-no-badge">{{ ticket.ticketNo }}</div>
          <el-tag :type="getStatusTagType(ticket.status)" effect="dark" size="small">
            {{ ticket.statusLabel }}
          </el-tag>
        </div>
        <div class="ticket-title-text">{{ ticket.title }}</div>
        <div class="ticket-meta-row">
          <span>类型：{{ getTicketTypeName(ticket.ticketType) }}</span>
          <span>指派：<strong>{{ ticket.operatorName || '未分配' }}</strong></span>
          <span>创建：{{ ticket.createTime }}</span>
        </div>
      </div>

      <!-- 2. 工单生命周期流转步骤条 -->
      <div class="section-card">
        <div class="section-header">
          <span class="icon">🔄</span>
          <span class="title">工单全生命周期状态流</span>
        </div>
        <el-steps :active="getStepActive(ticket.status)" finish-status="success" align-center size="small" class="ticket-steps">
          <el-step title="待分配" description="生成工单" />
          <el-step title="已指派" :description="ticket.operatorName || '指派人员'" />
          <el-step title="排障中" description="现场排查" />
          <el-step title="待复核" description="提交记录" />
          <el-step title="已办结" description="闭环消警" />
        </el-steps>
      </div>

      <!-- 3. 关联动环告警与机柜物理空间上下文 -->
      <div class="section-card">
        <div class="section-header">
          <span class="icon">📍</span>
          <span class="title">故障机柜与微环境上下文</span>
        </div>
        <div class="context-grid">
          <div class="context-item">
            <span class="lbl">故障机柜</span>
            <span class="val highlight">{{ ticket.rackCode || '--' }}</span>
          </div>
          <div class="context-item">
            <span class="lbl">机房区域</span>
            <span class="val">{{ ticket.roomName || '华东01-A区' }}</span>
          </div>
          <div class="context-item">
            <span class="lbl">关联告警</span>
            <span class="val">{{ ticket.alarmId ? `#${ticket.alarmId}` : '人工发起' }}</span>
          </div>
          <div class="context-item">
            <span class="lbl">越限采样</span>
            <span class="val danger">{{ ticket.alarmMetricValue || '--' }}</span>
          </div>
        </div>
      </div>

      <!-- 4. 推荐应急 SOP 排障指引 -->
      <div class="section-card sop-card">
        <div class="section-header">
          <span class="icon">📋</span>
          <span class="title">推荐应急排障 SOP 协同指导</span>
          <span class="sop-tag">标准处置规程</span>
        </div>
        <div class="sop-content">
          <pre>{{ ticket.sopGuide || '1. 检查通道密闭门；\n2. 核查冷热通道温差；\n3. 监测在架服务器实时温度。' }}</pre>
        </div>
      </div>

      <!-- 5. 现场处置日志与排障反馈 -->
      <div class="section-card">
        <div class="section-header">
          <span class="icon">📝</span>
          <span class="title">现场排障处置反馈记录</span>
        </div>
        <div v-if="ticket.processNotes" class="process-notes-box">
          {{ ticket.processNotes }}
        </div>
        <div v-else class="empty-notes">
          暂无现场处置记录，待运维工程师接单后填写。
        </div>

        <!-- 排障中状态下提供填写处置记录表单 -->
        <div v-if="ticket.status === 2" class="resolve-form-box">
          <el-input
            v-model="resolveNotesInput"
            type="textarea"
            :rows="3"
            placeholder="请详细录入现场排障处置过程（如：已复位 A-03 顶部风机支路，冷通道温度回落至 23.5℃）..."
          />
        </div>
      </div>

      <!-- 6. 底部流程推进操作栏 -->
      <div class="action-footer-bar">
        <!-- 待分配状态：指派处理人 -->
        <template v-if="ticket.status === 0">
          <el-select v-model="selectedOperator" size="default" style="width: 180px;" placeholder="选择指派工程师">
            <el-option label="张工 (现场值班)" value="张工" />
            <el-option label="李工 (暖通专家)" value="李工" />
            <el-option label="王工 (网络架构)" value="王工" />
          </el-select>
          <el-button type="primary" :loading="actionLoading" @click="handleAssign">
            立即指派处理
          </el-button>
        </template>

        <!-- 已指派状态：工程师接单开工 -->
        <template v-else-if="ticket.status === 1">
          <span class="action-tip">已下派至【{{ ticket.operatorName }}】，请工程师点击接单开工</span>
          <el-button type="primary" :loading="actionLoading" @click="handleStart">
            🚀 现场接单排障
          </el-button>
        </template>

        <!-- 排障中状态：提交排障处置记录 -->
        <template v-else-if="ticket.status === 2">
          <el-button type="success" :loading="actionLoading" @click="handleResolve">
            ✅ 提交排障记录并申请复核
          </el-button>
        </template>

        <!-- 待复核状态：主管复核办结并闭环消警 -->
        <template v-else-if="ticket.status === 6">
          <span class="action-tip">工程师已完成现场排障，请主管复核结案</span>
          <el-button type="danger" :loading="actionLoading" @click="handleComplete">
            🔒 确认复核并闭环消警
          </el-button>
        </template>

        <!-- 已办结状态 -->
        <template v-else-if="ticket.status === 7">
          <el-tag type="success" size="large" effect="dark">
            ✅ 工单已结案归档 (办结时间: {{ ticket.finishTime || '刚刚' }})
          </el-tag>
        </template>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assignWorkTicket, startWorkTicket, resolveWorkTicket, completeWorkTicket } from '@/api/ticket'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  ticket: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'ticketUpdated'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const actionLoading = ref(false)
const selectedOperator = ref('张工')
const resolveNotesInput = ref('')

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

function getStepActive(status) {
  switch (status) {
    case 0: return 0
    case 1: return 1
    case 2: return 2
    case 6: return 3
    case 7: return 5
    default: return 1
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

// 指派工程师
async function handleAssign() {
  if (!props.ticket?.ticketId) return
  actionLoading.value = true
  try {
    const res = await assignWorkTicket(props.ticket.ticketId, {
      operatorName: selectedOperator.value,
      operatorId: 101
    })
    ElMessage.success(`已成功指派给【${selectedOperator.value}】`)
    emit('ticketUpdated', res?.data || res)
  } catch (err) {
    ElMessage.error('指派失败')
  } finally {
    actionLoading.value = false
  }
}

// 接单排障
async function handleStart() {
  if (!props.ticket?.ticketId) return
  actionLoading.value = true
  try {
    const res = await startWorkTicket(props.ticket.ticketId)
    ElMessage.success('已接单，工单状态流转为【现场排障中】')
    emit('ticketUpdated', res?.data || res)
  } catch (err) {
    ElMessage.error('接单失败')
  } finally {
    actionLoading.value = false
  }
}

// 提交处置记录并申请复核
async function handleResolve() {
  if (!props.ticket?.ticketId) return
  if (!resolveNotesInput.value.trim()) {
    ElMessage.warning('请填写现场排障处置反馈记录')
    return
  }
  actionLoading.value = true
  try {
    const res = await resolveWorkTicket(props.ticket.ticketId, {
      processNotes: resolveNotesInput.value.trim()
    })
    ElMessage.success('现场排障记录已提交，工单流转为【待消警复核】')
    emit('ticketUpdated', res?.data || res)
  } catch (err) {
    ElMessage.error('提交处置记录失败')
  } finally {
    actionLoading.value = false
  }
}

// 主管复核办结并闭环消警
async function handleComplete() {
  if (!props.ticket?.ticketId) return
  try {
    await ElMessageBox.confirm(
      '确认现场故障已彻底排除并结案归档？该操作将同步自动消除关联的机柜活动告警！',
      '复核办结与闭环消警确认',
      {
        confirmButtonText: '确认结案并消警',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    actionLoading.value = true
    const res = await completeWorkTicket(props.ticket.ticketId, true)
    ElMessage.success('工单已办结归档，关联活动告警已成功闭环消除！')
    emit('ticketUpdated', res?.data || res)
  } catch (err) {
    // 取消
  } finally {
    actionLoading.value = false
  }
}
</script>

<style scoped>
.drawer-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  color: #f1f5f9;
}

.ticket-hero-banner {
  background: linear-gradient(135deg, rgba(30, 41, 59, 0.9) 0%, rgba(15, 23, 42, 0.95) 100%);
  border: 1px solid rgba(56, 189, 248, 0.25);
  border-radius: 8px;
  padding: 14px 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
}

.hero-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.ticket-no-badge {
  font-family: monospace;
  font-size: 13px;
  font-weight: bold;
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.12);
  padding: 2px 8px;
  border-radius: 4px;
  border: 1px solid rgba(56, 189, 248, 0.3);
}

.ticket-title-text {
  font-size: 15px;
  font-weight: bold;
  color: #ffffff;
  margin-bottom: 8px;
}

.ticket-meta-row {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #94a3b8;
}

.ticket-meta-row strong {
  color: #e2e8f0;
}

.section-card {
  background: rgba(30, 41, 59, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 12px 14px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}

.section-header .icon {
  font-size: 15px;
}

.section-header .title {
  font-size: 13px;
  font-weight: 600;
  color: #e2e8f0;
}

.section-header .sop-tag {
  margin-left: auto;
  font-size: 10px;
  color: #10b981;
  background: rgba(16, 185, 129, 0.15);
  padding: 1px 6px;
  border-radius: 3px;
}

.ticket-steps :deep(.el-step__title) {
  font-size: 12px;
  color: #94a3b8;
}

.ticket-steps :deep(.el-step__description) {
  font-size: 10px;
  color: #64748b;
}

.context-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.context-item {
  display: flex;
  flex-direction: column;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.05);
  padding: 8px 10px;
  border-radius: 6px;
}

.context-item .lbl {
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 4px;
}

.context-item .val {
  font-size: 13px;
  font-weight: bold;
  color: #e2e8f0;
}

.context-item .val.highlight {
  color: #38bdf8;
}

.context-item .val.danger {
  color: #f43f5e;
}

.sop-card {
  border-left: 3px solid #10b981;
}

.sop-content pre {
  margin: 0;
  font-family: inherit;
  font-size: 12px;
  color: #cbd5e1;
  line-height: 1.6;
  white-space: pre-wrap;
  background: rgba(15, 23, 42, 0.6);
  padding: 10px;
  border-radius: 6px;
}

.process-notes-box {
  font-size: 12px;
  line-height: 1.6;
  color: #34d399;
  background: rgba(16, 185, 129, 0.1);
  border: 1px dashed rgba(16, 185, 129, 0.3);
  padding: 10px;
  border-radius: 6px;
}

.empty-notes {
  font-size: 12px;
  color: #64748b;
  font-style: italic;
  padding: 6px 0;
}

.resolve-form-box {
  margin-top: 10px;
}

.action-footer-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.action-tip {
  font-size: 12px;
  color: #94a3b8;
}
</style>
