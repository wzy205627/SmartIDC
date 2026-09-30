<template>
  <view class="home-container">
    <!-- 顶部状态栏 -->
    <view class="status-bar">
      <view class="user-info">
        <view class="avatar">
          <text>{{ userInitial }}</text>
        </view>
        <view class="meta">
          <view class="name-row">
            <text class="user-name">{{ authStore.user?.nickName || '驻场工程师' }}</text>
            <text class="role-badge">{{ roleBadgeText }}</text>
          </view>
          <text class="tenant-text">当前租户: {{ currentTenantName }}</text>
        </view>
      </view>
      <view class="network-badge" :class="{ offline: !networkStore.isOnline }">
        <text class="network-dot"></text>
        <text class="network-label">{{ networkStore.isOnline ? networkStore.networkType.toUpperCase() : '弱网离线' }}</text>
      </view>
    </view>

    <!-- 离线提示条 -->
    <view v-if="!networkStore.isOnline" class="offline-alert">
      <text class="alert-icon">⚡</text>
      <text class="alert-msg">冷通道弱网保护中，已开启本地离线只读缓存模式</text>
    </view>

    <!-- 核心快捷操作区 -->
    <view class="action-grid">
      <view class="action-card primary" @tap="handleScanRack">
        <view class="action-icon-wrap">
          <text class="action-icon">📷</text>
        </view>
        <view class="action-texts">
          <text class="action-title">扫码巡检机架</text>
          <text class="action-desc">扫描机柜二维码直达微画像 (T5.2)</text>
        </view>
        <view class="action-arrow">
          <text>›</text>
        </view>
      </view>

      <!-- 扫码辅助工具栏: 手电筒补光 + 手动输入兜底 -->
      <view class="scan-assist-bar">
        <view class="assist-btn" :class="{ active: isFlashlightOn }" @tap="toggleFlashlight">
          <text class="assist-icon">{{ isFlashlightOn ? '🔦' : '💡' }}</text>
          <text class="assist-text">{{ isFlashlightOn ? '关闭补光' : '暗光补光手电' }}</text>
        </view>
        <view class="assist-btn" @tap="openManualModal">
          <text class="assist-icon">⌨️</text>
          <text class="assist-text">污损反光手动输入</text>
        </view>
      </view>

      <view class="action-card secondary" @tap="handleGoTickets">
        <view class="action-icon-wrap">
          <text class="action-icon">📋</text>
        </view>
        <view class="action-texts">
          <text class="action-title">待办消警工单</text>
          <text class="action-desc">现场故障复核与人机闭环 (T5.3)</text>
        </view>
        <view class="action-arrow">
          <text>›</text>
        </view>
      </view>
    </view>

    <!-- 动环机房概况看板 -->
    <view class="section-title">
      <text class="title-text">管辖机房实时概览</text>
      <text class="room-tag">{{ assignedRoomText }}</text>
    </view>

    <view class="metrics-cards">
      <view class="metric-item">
        <text class="metric-val">23.8<text class="unit">℃</text></text>
        <text class="metric-lbl">冷通道均温</text>
      </view>
      <view class="metric-item">
        <text class="metric-val">46.5<text class="unit">%</text></text>
        <text class="metric-lbl">环境湿度</text>
      </view>
      <view class="metric-item">
        <text class="metric-val">1.24</text>
        <text class="metric-lbl">实时 PUE</text>
      </view>
      <view class="metric-item">
        <text class="metric-val text-green">0<text class="unit">起</text></text>
        <text class="metric-lbl">严重告警</text>
      </view>
    </view>

    <!-- 手动输入机柜号弹窗 (污损 / 反光 / 扫码受限兜底) -->
    <view v-if="showManualModal" class="modal-mask" @tap.self="closeManualModal">
      <view class="modal-box">
        <view class="modal-header">
          <text class="modal-title">手动输入机柜编号</text>
          <text class="modal-close" @tap="closeManualModal">✕</text>
        </view>
        <view class="modal-body">
          <text class="modal-tips">支持直接输入如 RACK-A01、A-03，或完整资产链接：</text>
          <view class="input-wrap">
            <input
              v-model="manualRackInput"
              class="manual-input"
              placeholder="请输入机柜编码 (如 RACK-A01)"
              placeholder-class="input-placeholder"
              focus
            />
            <text v-if="manualRackInput" class="clear-btn" @tap="manualRackInput = ''">✕</text>
          </view>

          <!-- 快速示例预设 Chip -->
          <view class="chips-title">快捷机柜选择：</view>
          <view class="chips-row">
            <text
              v-for="chip in sampleRackChips"
              :key="chip"
              class="chip-tag"
              :class="{ selected: manualRackInput.toUpperCase().includes(chip) }"
              @tap="selectChip(chip)"
            >
              {{ chip }}
            </text>
          </view>
        </view>

        <view class="modal-actions">
          <button class="btn-cancel" @tap="closeManualModal">取消</button>
          <button class="btn-confirm" @tap="handleConfirmManualRack">进入微画像</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import { useNetworkStore } from '@/store/modules/network';
import { parseRackCode } from '@/utils/rackCodeParser';

const authStore = useAuthStore();
const networkStore = useNetworkStore();

const isFlashlightOn = ref(false);
const showManualModal = ref(false);
const manualRackInput = ref('');
const sampleRackChips = ['RACK-A01', 'RACK-A02', 'RACK-B01', 'RACK-B02', 'A-03'];

const userInitial = computed(() => {
  const name = authStore.user?.nickName || authStore.user?.username || '工';
  return name.slice(0, 1);
});

const roleBadgeText = computed(() => {
  const role = authStore.user?.roleKey;
  if (role === 'supervisor') return '值班主管';
  if (role === 'admin') return '系统管理员';
  return '驻场工程师';
});

const currentTenantName = computed(() => {
  const t = authStore.tenantList.find((item) => item.tenantId === authStore.currentTenantId);
  return t ? t.tenantName : authStore.currentTenantId;
});

const assignedRoomText = computed(() => {
  const rooms = authStore.user?.assignedRooms;
  if (!rooms || rooms.length === 0) return '华东01-A区';
  return Array.isArray(rooms) ? rooms.join(', ') : String(rooms);
});

/**
 * 调起原生扫码并在成功后进行鲁棒参数解析
 */
function handleScanRack() {
  uni.scanCode({
    onlyFromCamera: false,
    scanType: ['qrCode', 'barCode'],
    success: (res) => {
      const code = parseRackCode(res.result);
      if (code) {
        uni.navigateTo({
          url: `/pages/rack/profile?rackCode=${encodeURIComponent(code)}`
        });
      } else {
        uni.showToast({
          title: `无法识别编码: ${res.result.slice(0, 16)}`,
          icon: 'none'
        });
      }
    },
    fail: (err) => {
      if (err && err.errMsg && err.errMsg.includes('cancel')) {
        return;
      }
      uni.showToast({
        title: '调起扫码失败，建议使用手动输入',
        icon: 'none'
      });
    }
  });
}

/**
 * 冷通道弱光补光手电开关
 */
function toggleFlashlight() {
  // #ifdef MP-WEIXIN || APP-PLUS
  uni.setFlashlight({
    flash: !isFlashlightOn.value,
    success: () => {
      isFlashlightOn.value = !isFlashlightOn.value;
      uni.showToast({
        title: isFlashlightOn.value ? '已开启补光手电' : '已关闭补光手电',
        icon: 'none'
      });
    },
    fail: () => {
      uni.showToast({ title: '当前设备不支持控制闪光灯', icon: 'none' });
    }
  });
  // #endif

  // #ifdef H5
  uni.showToast({ title: 'H5 环境受限于安全策略不支持闪光灯', icon: 'none' });
  // #endif
}

function openManualModal() {
  manualRackInput.value = '';
  showManualModal.value = true;
}

function closeManualModal() {
  showManualModal.value = false;
}

function selectChip(chip: string) {
  manualRackInput.value = chip;
}

function handleConfirmManualRack() {
  const code = parseRackCode(manualRackInput.value);
  if (!code) {
    uni.showToast({ title: '请输入有效的机柜编号 (如 RACK-A01)', icon: 'none' });
    return;
  }
  showManualModal.value = false;
  uni.navigateTo({
    url: `/pages/rack/profile?rackCode=${encodeURIComponent(code)}`
  });
}

function handleGoTickets() {
  uni.switchTab({
    url: '/pages/ticket/list'
  });
}
</script>

<style scoped>
.home-container {
  padding: 32rpx;
  min-height: 100vh;
}

.status-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28rpx;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #0284c7, #38bdf8);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  font-weight: 700;
  color: #fff;
  box-shadow: 0 4rpx 16rpx rgba(14, 165, 233, 0.3);
}

.name-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.user-name {
  font-size: 32rpx;
  font-weight: 700;
  color: #f8fafc;
}

.role-badge {
  font-size: 20rpx;
  background: rgba(56, 189, 248, 0.2);
  color: #38bdf8;
  border: 1px solid rgba(56, 189, 248, 0.4);
  padding: 2rpx 10rpx;
  border-radius: 8rpx;
}

.tenant-text {
  display: block;
  font-size: 22rpx;
  color: #94a3b8;
  margin-top: 4rpx;
}

.network-badge {
  display: flex;
  align-items: center;
  gap: 8rpx;
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(71, 85, 105, 0.5);
  padding: 8rpx 16rpx;
  border-radius: 20rpx;
}

.network-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #22c55e;
}

.network-badge.offline .network-dot {
  background: #eab308;
}

.network-label {
  font-size: 20rpx;
  color: #cbd5e1;
}

.offline-alert {
  background: rgba(234, 179, 8, 0.15);
  border: 1px solid rgba(234, 179, 8, 0.4);
  padding: 16rpx 20rpx;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 28rpx;
}

.alert-icon {
  font-size: 28rpx;
}

.alert-msg {
  font-size: 22rpx;
  color: #fef08a;
}

.action-grid {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-bottom: 36rpx;
}

.action-card {
  display: flex;
  align-items: center;
  padding: 32rpx;
  border-radius: 20rpx;
  gap: 24rpx;
  position: relative;
}

.action-card.primary {
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.25), rgba(2, 132, 199, 0.1));
  border: 1px solid rgba(56, 189, 248, 0.4);
}

.action-card.secondary {
  background: rgba(30, 41, 59, 0.7);
  border: 1px solid rgba(71, 85, 105, 0.4);
}

.action-icon-wrap {
  width: 80rpx;
  height: 80rpx;
  border-radius: 20rpx;
  background: rgba(15, 23, 42, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-icon {
  font-size: 40rpx;
}

.action-texts {
  flex: 1;
}

.action-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #f8fafc;
}

.action-desc {
  display: block;
  font-size: 22rpx;
  color: #94a3b8;
  margin-top: 6rpx;
}

.action-arrow {
  color: #64748b;
  font-size: 36rpx;
}

/* 扫码辅助栏 */
.scan-assist-bar {
  display: flex;
  gap: 16rpx;
  margin-top: -6rpx;
}

.assist-btn {
  flex: 1;
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(56, 189, 248, 0.25);
  border-radius: 14rpx;
  padding: 16rpx 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  transition: all 0.2s ease;
}

.assist-btn.active {
  background: rgba(14, 165, 233, 0.2);
  border-color: #38bdf8;
}

.assist-icon {
  font-size: 26rpx;
}

.assist-text {
  font-size: 22rpx;
  color: #cbd5e1;
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.title-text {
  font-size: 28rpx;
  font-weight: 700;
  color: #e2e8f0;
}

.room-tag {
  font-size: 22rpx;
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.1);
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}

.metrics-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
}

.metric-item {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(51, 65, 85, 0.6);
  border-radius: 16rpx;
  padding: 24rpx;
  text-align: center;
}

.metric-val {
  font-size: 38rpx;
  font-weight: 700;
  color: #f8fafc;
}

.unit {
  font-size: 24rpx;
  font-weight: 400;
  color: #94a3b8;
  margin-left: 4rpx;
}

.text-green {
  color: #4ade80;
}

.metric-lbl {
  display: block;
  font-size: 22rpx;
  color: #64748b;
  margin-top: 8rpx;
}

/* 手动输入弹窗样式 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx;
  z-index: 999;
}

.modal-box {
  width: 100%;
  max-width: 620rpx;
  background: #0f172a;
  border: 1px solid rgba(56, 189, 248, 0.35);
  border-radius: 24rpx;
  padding: 36rpx;
  box-shadow: 0 16rpx 48rpx rgba(0, 0, 0, 0.5);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}

.modal-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #f8fafc;
}

.modal-close {
  font-size: 32rpx;
  color: #94a3b8;
  padding: 8rpx;
}

.modal-tips {
  font-size: 22rpx;
  color: #94a3b8;
  line-height: 1.5;
  margin-bottom: 16rpx;
}

.input-wrap {
  position: relative;
  margin-bottom: 24rpx;
}

.manual-input {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(56, 189, 248, 0.4);
  border-radius: 12rpx;
  height: 80rpx;
  padding: 0 64rpx 0 24rpx;
  color: #f8fafc;
  font-size: 26rpx;
}

.input-placeholder {
  color: #64748b;
}

.clear-btn {
  position: absolute;
  right: 20rpx;
  top: 50%;
  transform: translateY(-50%);
  color: #94a3b8;
  font-size: 26rpx;
  padding: 8rpx;
}

.chips-title {
  font-size: 22rpx;
  color: #64748b;
  margin-bottom: 12rpx;
}

.chips-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 32rpx;
}

.chip-tag {
  background: rgba(30, 41, 59, 0.9);
  border: 1px solid rgba(71, 85, 105, 0.6);
  color: #38bdf8;
  padding: 8rpx 16rpx;
  border-radius: 10rpx;
  font-size: 22rpx;
  transition: all 0.2s;
}

.chip-tag.selected {
  background: rgba(14, 165, 233, 0.3);
  border-color: #38bdf8;
  font-weight: 700;
}

.modal-actions {
  display: flex;
  gap: 20rpx;
}

.btn-cancel {
  flex: 1;
  height: 76rpx;
  line-height: 76rpx;
  background: rgba(30, 41, 59, 0.8);
  color: #94a3b8;
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 12rpx;
  font-size: 26rpx;
}

.btn-confirm {
  flex: 1;
  height: 76rpx;
  line-height: 76rpx;
  background: linear-gradient(135deg, #0284c7, #0ea5e9);
  color: #fff;
  border: none;
  border-radius: 12rpx;
  font-size: 26rpx;
  font-weight: 700;
}
</style>
