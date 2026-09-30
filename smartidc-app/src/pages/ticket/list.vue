<template>
  <view class="ticket-page-container">
    <!-- 顶部状态过滤选项卡 -->
    <view class="filter-tabs">
      <view
        v-for="tab in filterTabs"
        :key="tab.label"
        class="filter-tab-item"
        :class="{ active: currentStatus === tab.status }"
        @tap="handleSelectTab(tab.status)"
      >
        <text class="tab-text">{{ tab.label }}</text>
      </view>
    </view>

    <!-- 加载中状态 -->
    <view v-if="loading && ticketList.length === 0" class="loading-wrap">
      <view class="spinner"></view>
      <text class="loading-label">正在同步现场工单列表...</text>
    </view>

    <!-- 空列表展示 -->
    <view v-else-if="ticketList.length === 0" class="empty-state">
      <text class="empty-icon">📂</text>
      <text class="empty-title">当前暂无匹配工单</text>
      <text class="empty-desc">管辖机房暂无此状态的巡检或现场消警任务</text>
    </view>

    <!-- 工单列表卡片 -->
    <view v-else class="ticket-list">
      <view
        v-for="item in ticketList"
        :key="item.ticketId"
        class="ticket-item-card"
        @tap="goToDetail(item.ticketId)"
      >
        <view class="ticket-card-header">
          <view class="id-type-group">
            <text class="ticket-id-badge">#{{ item.ticketId }}</text>
            <text class="ticket-type-label">{{ formatTicketType(item.ticketType) }}</text>
          </view>
          <view class="status-pill" :class="getStatusPillClass(item.status)">
            {{ item.statusText || formatStatus(item.status) }}
          </view>
        </view>

        <text class="ticket-title">{{ item.title }}</text>

        <view class="ticket-card-footer">
          <view class="footer-left">
            <text class="operator-tag">👨‍🔧 {{ item.operatorName || '系统待分配' }}</text>
          </view>
          <view class="footer-right">
            <text class="time-text">{{ formatTime(item.createTime) }}</text>
            <text class="arrow-icon">›</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app';
import { getTicketListApi } from '@/api/ticket';
import type { MobileWorkTicketItemVO } from '@/api/rack';

const currentStatus = ref<number | undefined>(undefined);
const loading = ref(false);
const ticketList = ref<MobileWorkTicketItemVO[]>([]);

const filterTabs = [
  { label: '全部', status: undefined },
  { label: '待接单', status: 1 },
  { label: '排障中', status: 2 },
  { label: '挂起中', status: 3 },
  { label: '待复核', status: 6 },
  { label: '已办结', status: 7 }
];

onShow(() => {
  loadTickets();
});

onPullDownRefresh(async () => {
  await loadTickets();
  uni.stopPullDownRefresh();
});

async function loadTickets() {
  loading.value = true;
  try {
    const list = await getTicketListApi(currentStatus.value);
    ticketList.value = list || [];
  } catch (err: any) {
    uni.showToast({ title: err?.message || '拉取工单失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

function handleSelectTab(status?: number) {
  if (currentStatus.value === status) return;
  currentStatus.value = status;
  loadTickets();
}

function goToDetail(ticketId: number) {
  uni.navigateTo({
    url: `/pages/ticket/detail?ticketId=${ticketId}`
  });
}

function getStatusPillClass(status: number): string {
  switch (status) {
    case 0:
    case 1:
      return 'pill-pending';
    case 2:
      return 'pill-processing';
    case 3:
      return 'pill-suspended';
    case 6:
      return 'pill-review';
    case 7:
      return 'pill-done';
    default:
      return 'pill-pending';
  }
}

function formatStatus(status: number): string {
  switch (status) {
    case 0: return '待分配';
    case 1: return '已指派';
    case 2: return '排障中';
    case 3: return '审批挂起';
    case 4: return '已批准';
    case 5: return '已驳回';
    case 6: return '待消警复核';
    case 7: return '已办结';
    default: return '流转中';
  }
}

function formatTicketType(type?: string): string {
  if (type === 'ALARM_REPAIR') return '故障排障';
  if (type === 'ROUTINE_CHECK') return '常规巡检';
  if (type === 'ASSET_MOVE') return '资产移机';
  return type || '日常工单';
}

function formatTime(val?: string | null): string {
  if (!val) return '--';
  return val.replace('T', ' ').slice(0, 16);
}
</script>

<style scoped>
.ticket-page-container {
  min-height: 100vh;
  background: #0b1120;
  padding: 24rpx;
  padding-bottom: 60rpx;
}

/* 顶部滑动过滤条 */
.filter-tabs {
  display: flex;
  background: rgba(15, 23, 42, 0.85);
  border: 1px solid rgba(51, 65, 85, 0.6);
  border-radius: 16rpx;
  padding: 6rpx;
  margin-bottom: 24rpx;
  overflow-x: auto;
}

.filter-tab-item {
  flex: 1;
  min-width: 100rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12rpx;
  transition: all 0.2s ease;
}

.filter-tab-item.active {
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.35), rgba(2, 132, 199, 0.2));
  border: 1px solid rgba(56, 189, 248, 0.5);
}

.tab-text {
  font-size: 24rpx;
  color: #94a3b8;
  white-space: nowrap;
}

.filter-tab-item.active .tab-text {
  color: #38bdf8;
  font-weight: 700;
}

/* 列表与卡片 */
.ticket-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.ticket-item-card {
  background: rgba(15, 23, 42, 0.75);
  border: 1px solid rgba(51, 65, 85, 0.7);
  border-radius: 18rpx;
  padding: 26rpx;
  transition: border-color 0.2s;
}

.ticket-item-card:active {
  border-color: #38bdf8;
}

.ticket-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14rpx;
}

.id-type-group {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.ticket-id-badge {
  font-size: 22rpx;
  font-weight: 700;
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.15);
  border: 1px solid rgba(56, 189, 248, 0.3);
  padding: 2rpx 10rpx;
  border-radius: 6rpx;
}

.ticket-type-label {
  font-size: 22rpx;
  color: #cbd5e1;
}

.status-pill {
  font-size: 20rpx;
  font-weight: 600;
  padding: 4rpx 12rpx;
  border-radius: 10rpx;
}

.pill-pending {
  background: rgba(14, 165, 233, 0.2);
  color: #38bdf8;
  border: 1px solid rgba(14, 165, 233, 0.4);
}

.pill-processing {
  background: rgba(234, 179, 8, 0.2);
  color: #facc15;
  border: 1px solid rgba(234, 179, 8, 0.4);
}

.pill-suspended {
  background: rgba(239, 68, 68, 0.2);
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.4);
}

.pill-review {
  background: rgba(168, 85, 247, 0.2);
  color: #c084fc;
  border: 1px solid rgba(168, 85, 247, 0.4);
}

.pill-done {
  background: rgba(34, 197, 94, 0.2);
  color: #4ade80;
  border: 1px solid rgba(34, 197, 94, 0.4);
}

.ticket-title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #f8fafc;
  line-height: 1.4;
  margin-bottom: 20rpx;
}

.ticket-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid rgba(51, 65, 85, 0.4);
  padding-top: 16rpx;
}

.operator-tag {
  font-size: 22rpx;
  color: #94a3b8;
}

.footer-right {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.time-text {
  font-size: 20rpx;
  color: #64748b;
}

.arrow-icon {
  font-size: 32rpx;
  color: #64748b;
}

/* 加载与空状态 */
.loading-wrap,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 50vh;
  gap: 16rpx;
}

.spinner {
  width: 48rpx;
  height: 48rpx;
  border: 4rpx solid rgba(56, 189, 248, 0.2);
  border-top-color: #38bdf8;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.loading-label {
  font-size: 24rpx;
  color: #94a3b8;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-icon {
  font-size: 72rpx;
}

.empty-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #cbd5e1;
}

.empty-desc {
  font-size: 22rpx;
  color: #64748b;
}
</style>
