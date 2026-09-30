<template>
  <view class="my-container">
    <!-- 个人资料卡片 -->
    <view class="profile-card">
      <view class="avatar-large">
        <text>{{ (authStore.user?.nickName || '工').slice(0, 1) }}</text>
      </view>
      <view class="profile-info">
        <view class="name-line">
          <text class="profile-name">{{ authStore.user?.nickName || '现场工程师' }}</text>
          <text class="role-badge">{{ authStore.user?.roleKey || 'engineer' }}</text>
        </view>
        <text class="profile-sub">工号: {{ authStore.user?.username || '---' }}</text>
        <text class="profile-sub">电话: {{ authStore.user?.phone || '13800000002' }}</text>
      </view>
    </view>

    <!-- 管辖机房区域 -->
    <view class="section-card">
      <view class="card-header">
        <text class="card-title">管辖机房范围 (行级权限)</text>
      </view>
      <view class="tag-list">
        <text v-for="room in roomList" :key="room" class="room-tag">
          📍 {{ room }}
        </text>
      </view>
    </view>

    <!-- 多租户切换中心 (核心重点: 杜绝水平越权) -->
    <view class="section-card">
      <view class="card-header">
        <text class="card-title">授权租户机房环境</text>
        <text class="card-sub-hint">点击一键无感切换 JWT 事实源</text>
      </view>

      <view class="tenant-list">
        <view
          v-for="tenant in authStore.tenantList"
          :key="tenant.tenantId"
          class="tenant-item"
          :class="{ active: tenant.tenantId === authStore.currentTenantId }"
          @tap="handleSelectTenant(tenant.tenantId)"
        >
          <view class="tenant-left">
            <view class="tenant-radio">
              <view v-if="tenant.tenantId === authStore.currentTenantId" class="radio-inner"></view>
            </view>
            <view class="tenant-info">
              <text class="tenant-name">{{ tenant.tenantName }}</text>
              <text class="tenant-code">ID: {{ tenant.tenantId }}</text>
            </view>
          </view>
          <text v-if="tenant.tenantId === authStore.currentTenantId" class="current-badge">生效中</text>
        </view>
      </view>
    </view>

    <!-- 机房弱网与离线容灾状态 -->
    <view class="section-card">
      <view class="card-header">
        <text class="card-title">随行端运行环境</text>
      </view>
      <view class="info-row">
        <text class="info-label">网络通信状态</text>
        <text class="info-val" :class="{ 'text-yellow': !networkStore.isOnline }">
          {{ networkStore.isOnline ? `在线 (${networkStore.networkType})` : '离线容灾' }}
        </text>
      </view>
      <view class="info-row">
        <text class="info-label">双 Token 续期策略</text>
        <text class="info-val">Access(2h) / Refresh(7d)</text>
      </view>
      <view class="info-row">
        <text class="info-label">多租户事实源机制</text>
        <text class="info-val text-cyan">JWT 载荷强校验 (单权威源)</text>
      </view>
    </view>

    <!-- 登出按钮 -->
    <view class="logout-section">
      <button class="btn-logout" @tap="handleLogout">
        <text>退出登录与废除凭证</text>
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import { useNetworkStore } from '@/store/modules/network';

const authStore = useAuthStore();
const networkStore = useNetworkStore();

const roomList = computed(() => {
  const rooms = authStore.user?.assignedRooms;
  if (!rooms || rooms.length === 0) return ['华东01-A区', '华东02-B区'];
  return Array.isArray(rooms) ? rooms : [String(rooms)];
});

async function handleSelectTenant(targetTenantId: string) {
  if (targetTenantId === authStore.currentTenantId) {
    return;
  }
  uni.showLoading({ title: '正在切换租户...' });
  try {
    await authStore.switchTenant(targetTenantId);
    uni.hideLoading();
    uni.showToast({ title: '已成功切换生效租户', icon: 'success' });
  } catch (err: any) {
    uni.hideLoading();
    uni.showToast({ title: err.message || '租户切换失败', icon: 'none' });
  }
}

function handleLogout() {
  uni.showModal({
    title: '确认登出',
    content: '登出将立即废除 Redis 中的 Refresh Token 凭证，确认退出？',
    confirmColor: '#ef4444',
    success: async (res) => {
      if (res.confirm) {
        await authStore.logout();
        uni.reLaunch({
          url: '/pages/login/index'
        });
      }
    }
  });
}
</script>

<style scoped>
.my-container {
  padding: 32rpx;
  min-height: 100vh;
}

.profile-card {
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(56, 189, 248, 0.25);
  border-radius: 24rpx;
  padding: 32rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-bottom: 24rpx;
}

.avatar-large {
  width: 108rpx;
  height: 108rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #0284c7, #38bdf8);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  font-weight: 700;
  color: #fff;
  box-shadow: 0 8rpx 24rpx rgba(14, 165, 233, 0.35);
}

.name-line {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.profile-name {
  font-size: 34rpx;
  font-weight: 700;
  color: #f8fafc;
}

.role-badge {
  font-size: 20rpx;
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.15);
  border: 1px solid rgba(56, 189, 248, 0.4);
  padding: 2rpx 12rpx;
  border-radius: 8rpx;
}

.profile-sub {
  display: block;
  font-size: 24rpx;
  color: #94a3b8;
  margin-top: 6rpx;
}

.section-card {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(51, 65, 85, 0.6);
  border-radius: 20rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
}

.card-header {
  margin-bottom: 20rpx;
}

.card-title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #e2e8f0;
}

.card-sub-hint {
  display: block;
  font-size: 20rpx;
  color: #64748b;
  margin-top: 4rpx;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.room-tag {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(71, 85, 105, 0.6);
  color: #cbd5e1;
  font-size: 24rpx;
  padding: 10rpx 20rpx;
  border-radius: 12rpx;
}

.tenant-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.tenant-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(30, 41, 59, 0.5);
  border: 1px solid rgba(51, 65, 85, 0.5);
  padding: 24rpx;
  border-radius: 16rpx;
  transition: all 0.2s ease;
}

.tenant-item.active {
  background: rgba(14, 165, 233, 0.15);
  border-color: rgba(56, 189, 248, 0.6);
}

.tenant-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.tenant-radio {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 2px solid #64748b;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tenant-item.active .tenant-radio {
  border-color: #38bdf8;
}

.radio-inner {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  background: #38bdf8;
}

.tenant-name {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #f1f5f9;
}

.tenant-code {
  display: block;
  font-size: 22rpx;
  color: #64748b;
  margin-top: 4rpx;
}

.current-badge {
  font-size: 20rpx;
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.2);
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 0;
  border-bottom: 1px solid rgba(51, 65, 85, 0.4);
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  font-size: 24rpx;
  color: #94a3b8;
}

.info-val {
  font-size: 24rpx;
  color: #e2e8f0;
}

.text-yellow {
  color: #facc15;
}

.text-cyan {
  color: #38bdf8;
}

.logout-section {
  margin-top: 48rpx;
  margin-bottom: 64rpx;
}

.btn-logout {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.4);
  color: #f87171;
  font-size: 28rpx;
  font-weight: 600;
  border-radius: 16rpx;
}
</style>
