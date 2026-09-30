<template>
  <view class="profile-container">
    <!-- 顶部导航栏 -->
    <view class="custom-navbar">
      <view class="nav-left" @tap="handleGoBack">
        <text class="nav-back-icon">‹</text>
        <text class="nav-title">机柜微画像</text>
      </view>
      <view class="nav-right" v-if="profile">
        <text class="rack-badge">{{ profile.rackInfo?.rackCode }}</text>
      </view>
    </view>

    <!-- 加载中 Skeleton / Spinner -->
    <view v-if="loading && !profile" class="loading-state">
      <view class="spinner"></view>
      <text class="loading-text">正在拉取机柜微画像...</text>
    </view>

    <!-- BOLA 跨租户越权防护拦截状态 -->
    <view v-else-if="bolaForbidden" class="forbidden-card">
      <view class="forbidden-icon">🛡️</view>
      <text class="forbidden-title">跨租户越权安全防护 (BOLA)</text>
      <text class="forbidden-desc">
        {{ forbiddenMessage || '当前账号无权查看非本租户所属机柜资产与运行指标。' }}
      </text>
      <view class="forbidden-actions">
        <button class="btn-back" @tap="handleGoBack">返回工作台</button>
      </view>
    </view>

    <!-- 正常微画像内容区 -->
    <view v-else-if="profile" class="content-body">
      <!-- 1. 机柜基础信息卡片 -->
      <view class="rack-base-card">
        <view class="card-header">
          <view class="rack-title-row">
            <text class="rack-name">{{ profile.rackInfo.rackName || profile.rackInfo.rackCode }}</text>
            <view class="status-tag" :class="rackStatusClass">
              {{ rackStatusText }}
            </view>
          </view>
          <text class="copy-code-btn" @tap="copyRackCode">
            {{ profile.rackInfo.rackCode }} 📋
          </text>
        </view>

        <view class="meta-grid">
          <view class="meta-item">
            <text class="meta-lbl">所属机房</text>
            <text class="meta-val">{{ profile.rackInfo.roomName || '主数据中心' }}</text>
          </view>
          <view class="meta-item">
            <text class="meta-lbl">归属租户</text>
            <text class="meta-val">{{ profile.rackInfo.tenantId === '000000' ? '平台共用' : profile.rackInfo.tenantId }}</text>
          </view>
          <view class="meta-item">
            <text class="meta-lbl">额定供电</text>
            <text class="meta-val">{{ profile.rackInfo.ratedPowerKva ? profile.rackInfo.ratedPowerKva + ' kVA' : '--' }}</text>
          </view>
          <view class="meta-item">
            <text class="meta-lbl">实时负载率</text>
            <text class="meta-val highlight-val">{{ profile.rackInfo.currentLoadRatio != null ? profile.rackInfo.currentLoadRatio + '%' : '--' }}</text>
          </view>
        </view>
      </view>

      <!-- 2. 动环实时监测卡片 (双环仪表 + 5s 动态轮询) -->
      <view class="telemetry-card">
        <view class="card-header">
          <view class="telemetry-title-group">
            <text class="section-title">实时微动环画像</text>
            <view class="telemetry-badge" :class="telemetryStatusClass">
              <text class="badge-dot"></text>
              <text class="badge-label">{{ profile.telemetry.status === 'ONLINE' ? 'ONLINE 传感器在线' : 'OFFLINE 离线哨兵' }}</text>
            </view>
          </view>
          <text class="poll-tip" :class="{ pulsing: isPolling }">
            ⚡ 5s 静默排障
          </text>
        </view>

        <!-- CSS3/SVG 纯矢量双环仪表 (彻底解决 Canvas 穿透) -->
        <view class="gauges-row">
          <view class="gauge-col">
            <GaugeMeter
              theme="temp"
              title="进风温度"
              unit="℃"
              :value="profile.telemetry.temp"
              :min="10"
              :max="45"
              :status="profile.telemetry.status"
            />
          </view>
          <view class="gauge-col">
            <GaugeMeter
              theme="humidity"
              title="相对湿度"
              unit="%RH"
              :value="profile.telemetry.humidity"
              :min="20"
              :max="80"
              :status="profile.telemetry.status"
            />
          </view>
        </view>

        <!-- 动环指标次要数据九宫格 -->
        <view class="sub-metrics-grid">
          <view class="sub-metric-box">
            <text class="sub-metric-val">
              {{ profile.telemetry.returnTemp != null ? profile.telemetry.returnTemp : '--' }}
              <text class="sub-unit" v-if="profile.telemetry.returnTemp != null">℃</text>
            </text>
            <text class="sub-metric-lbl">出风口温度</text>
          </view>
          <view class="sub-metric-box">
            <text class="sub-metric-val">
              {{ profile.telemetry.voltage != null ? profile.telemetry.voltage : '--' }}
              <text class="sub-unit" v-if="profile.telemetry.voltage != null">V</text>
            </text>
            <text class="sub-metric-lbl">母线电压</text>
          </view>
          <view class="sub-metric-box">
            <text class="sub-metric-val">
              {{ profile.telemetry.current != null ? profile.telemetry.current : '--' }}
              <text class="sub-unit" v-if="profile.telemetry.current != null">A</text>
            </text>
            <text class="sub-metric-lbl">工作电流</text>
          </view>
          <view class="sub-metric-box">
            <text class="sub-metric-val">
              {{ profile.telemetry.powerKw != null ? profile.telemetry.powerKw : '--' }}
              <text class="sub-unit" v-if="profile.telemetry.powerKw != null">kW</text>
            </text>
            <text class="sub-metric-lbl">实时有效功率</text>
          </view>
        </view>

        <!-- 刷新时间脚标 -->
        <view class="update-footer">
          <text class="update-time-text">
            最近采样心跳：{{ formatDateTime(profile.telemetry.updatedAt) }}
          </text>
        </view>
      </view>

      <!-- 3. 活动越限告警 (红灯先亮机制) -->
      <view class="alarm-section">
        <view v-if="profile.activeAlarms && profile.activeAlarms.length > 0" class="alarm-alert-card">
          <view class="alarm-header">
            <text class="alarm-title-tag">🚨 当前活动越限告警 ({{ profile.activeAlarms.length }})</text>
          </view>
          <view class="alarm-list">
            <view
              v-for="alarm in profile.activeAlarms"
              :key="alarm.alarmId"
              class="alarm-item"
              :class="alarm.alarmLevel === 'CRITICAL' ? 'critical-alarm' : 'warning-alarm'"
            >
              <view class="alarm-item-top">
                <text class="alarm-level-badge">{{ alarm.alarmLevel }}</text>
                <text class="alarm-type">{{ alarm.alarmType }}</text>
                <text class="alarm-time">{{ formatTimeOnly(alarm.createTime) }}</text>
              </view>
              <text class="alarm-desc">{{ alarm.alarmDesc }}</text>
            </view>
          </view>
        </view>
        <view v-else class="alarm-normal-card">
          <text class="normal-icon">✅</text>
          <text class="normal-text">机柜动环运行正常，未发生越限告警</text>
        </view>
      </view>

      <!-- 4. 双 Tab 切换架构: [42U 在架设备] 与 [维保工单与历史] -->
      <view class="tabs-container">
        <view class="tabs-header">
          <view
            class="tab-item"
            :class="{ active: currentTab === 0 }"
            @tap="switchTab(0)"
          >
            <text class="tab-label">42U 在架设备</text>
            <text class="tab-badge" v-if="profile.uSlotsSummary">
              {{ profile.uSlotsSummary.usedU }}U/{{ profile.uSlotsSummary.totalU }}U
            </text>
          </view>
          <view
            class="tab-item"
            :class="{ active: currentTab === 1 }"
            @tap="switchTab(1)"
          >
            <text class="tab-label">维保工单记录</text>
            <text class="tab-badge" v-if="ticketsTotal !== null">
              {{ ticketsTotal }} 起
            </text>
          </view>
        </view>

        <!-- Tab 0: 42U 设备槽位画像 -->
        <view v-show="currentTab === 0" class="tab-pane">
          <!-- 槽位容量进度卡片 -->
          <view class="u-summary-card">
            <view class="u-stat-row">
              <view class="u-stat-item">
                <text class="u-stat-val text-cyan">{{ profile.uSlotsSummary?.totalU || 42 }}U</text>
                <text class="u-stat-lbl">标称总高</text>
              </view>
              <view class="u-stat-item">
                <text class="u-stat-val text-green">{{ profile.uSlotsSummary?.usedU || 0 }}U</text>
                <text class="u-stat-lbl">已在架占用</text>
              </view>
              <view class="u-stat-item">
                <text class="u-stat-val text-amber">{{ profile.uSlotsSummary?.freeU || 0 }}U</text>
                <text class="u-stat-lbl">剩余可用</text>
              </view>
            </view>

            <!-- 42U 空间利用率条 -->
            <view class="u-progress-bar">
              <view
                class="u-progress-fill"
                :style="{ width: `${uUsagePercent}%` }"
              ></view>
            </view>
          </view>

          <!-- 挂载设备明细列表 -->
          <view class="devices-list">
            <view
              v-for="dev in profile.uSlotsSummary?.mountedDevices || []"
              :key="dev.deviceId"
              class="device-card"
            >
              <view class="device-u-badge">
                <text class="u-range-text">{{ dev.startU }}U - {{ dev.startU + dev.uHeight - 1 }}U</text>
                <text class="u-height-text">({{ dev.uHeight }}U)</text>
              </view>
              <view class="device-info">
                <view class="device-title-row">
                  <text class="device-name">{{ dev.deviceName || dev.deviceCode }}</text>
                  <text class="device-type-badge">{{ dev.deviceType }}</text>
                </view>
                <view class="device-meta-row">
                  <text class="device-code">{{ dev.deviceCode }}</text>
                  <text class="device-status-badge" :class="dev.status === 'NORMAL' || dev.status === 'ONLINE' ? 'status-ok' : 'status-warn'">
                    {{ dev.status || 'NORMAL' }}
                  </text>
                </view>
              </view>
            </view>

            <!-- 空列表提示 -->
            <view
              v-if="!profile.uSlotsSummary?.mountedDevices || profile.uSlotsSummary.mountedDevices.length === 0"
              class="empty-tip"
            >
              <text class="empty-icon">📦</text>
              <text class="empty-text">当前机柜未上架 IT 服务器与网络设备</text>
            </view>
          </view>
        </view>

        <!-- Tab 1: 按需异步独立加载维保工单 -->
        <view v-show="currentTab === 1" class="tab-pane">
          <view v-if="ticketsLoading && ticketsList.length === 0" class="sub-loading">
            <view class="spinner-sm"></view>
            <text>正在按需拉取工单记录...</text>
          </view>

          <view v-else-if="ticketsList.length === 0" class="empty-tip">
            <text class="empty-icon">📑</text>
            <text class="empty-text">该机柜暂无历史故障排障与维保工单记录</text>
          </view>

          <view v-else class="tickets-list">
            <view
              v-for="ticket in ticketsList"
              :key="ticket.ticketId"
              class="ticket-card"
            >
              <view class="ticket-top">
                <view class="ticket-id-tag">#{{ ticket.ticketId }}</view>
                <text class="ticket-type-badge">{{ ticket.ticketType }}</text>
                <view class="ticket-status-pill" :class="getTicketStatusClass(ticket.status)">
                  {{ ticket.statusText || getTicketStatusText(ticket.status) }}
                </view>
              </view>
              <text class="ticket-title">{{ ticket.title }}</text>
              <view class="ticket-bottom">
                <text class="ticket-operator">工单责任人: {{ ticket.operatorName || '系统分配' }}</text>
                <text class="ticket-date">{{ formatDateTime(ticket.createTime) }}</text>
              </view>
            </view>

            <!-- 分页加载更多按钮 -->
            <view class="tickets-pagination" v-if="ticketsList.length < ticketsTotal">
              <button
                class="btn-load-more"
                :loading="ticketsLoading"
                @tap="loadMoreTickets"
              >
                加载更多工单 (已显示 {{ ticketsList.length }}/{{ ticketsTotal }})
              </button>
            </view>
            <view class="no-more-tip" v-else-if="ticketsTotal > 0">
              <text>已展示全部 {{ ticketsTotal }} 起工单</text>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { onLoad, onShow, onHide, onUnload } from '@dcloudio/uni-app';
import GaugeMeter from '@/components/GaugeMeter.vue';
import {
  getRackProfileApi,
  getRackTelemetryApi,
  getRackTicketsApi,
  type MobileRackProfileVO,
  type MobileWorkTicketItemVO
} from '@/api/rack';
import { parseRackCode } from '@/utils/rackCodeParser';

const currentRackCode = ref('RACK-A01');
const loading = ref(true);
const profile = ref<MobileRackProfileVO | null>(null);

// BOLA 跨租户越权防护拦截状态
const bolaForbidden = ref(false);
const forbiddenMessage = ref('');

// 5s 静默轮询状态
const isPolling = ref(false);
let telemetryTimer: any = null;

// Dual Tab 状态: 0 - 42U在架设备, 1 - 维保工单
const currentTab = ref(0);

// 维保工单独立分页数据
const ticketsList = ref<MobileWorkTicketItemVO[]>([]);
const ticketsTotal = ref(0);
const ticketsPage = ref(1);
const ticketsLoading = ref(false);
const ticketsLoadedOnce = ref(false);

const rackStatusClass = computed(() => {
  const s = profile.value?.rackInfo?.status;
  if (s === 1) return 'status-online';
  if (s === 2) return 'status-maintain';
  return 'status-free';
});

const rackStatusText = computed(() => {
  const s = profile.value?.rackInfo?.status;
  if (s === 1) return '使用中';
  if (s === 2) return '维保中';
  return '空闲';
});

const telemetryStatusClass = computed(() => {
  return profile.value?.telemetry?.status === 'ONLINE' ? 'telemetry-online' : 'telemetry-offline';
});

const uUsagePercent = computed(() => {
  const total = profile.value?.uSlotsSummary?.totalU || 42;
  const used = profile.value?.uSlotsSummary?.usedU || 0;
  return Math.min(100, Math.round((used / total) * 100));
});

onLoad((options: any) => {
  if (options && (options.rackCode || options.code || options.q)) {
    const raw = options.rackCode || options.code || options.q;
    const parsed = parseRackCode(decodeURIComponent(raw));
    if (parsed) {
      currentRackCode.value = parsed;
    }
  }
  loadRackProfile();
});

onShow(() => {
  startTelemetryPolling();
});

onHide(() => {
  stopTelemetryPolling();
});

onUnload(() => {
  stopTelemetryPolling();
});

/**
 * 加载核心微画像
 */
async function loadRackProfile() {
  loading.value = true;
  bolaForbidden.value = false;
  forbiddenMessage.value = '';

  try {
    const res = await getRackProfileApi(currentRackCode.value);
    profile.value = res;
  } catch (err: any) {
    const msg = err?.message || '获取微画像失败';
    if (msg.includes('403') || msg.includes('无权查看') || msg.includes('租户')) {
      bolaForbidden.value = true;
      forbiddenMessage.value = msg;
    } else {
      uni.showToast({ title: msg, icon: 'none' });
    }
  } finally {
    loading.value = false;
  }
}

/**
 * 启动 5s 动环轻量静默轮询
 */
function startTelemetryPolling() {
  stopTelemetryPolling();
  telemetryTimer = setInterval(async () => {
    if (!currentRackCode.value || bolaForbidden.value) return;
    try {
      isPolling.value = true;
      const latest = await getRackTelemetryApi(currentRackCode.value);
      if (profile.value && latest) {
        profile.value.telemetry = latest;
      }
    } catch (e) {
      // 静默轮询网络异常不打扰界面
    } finally {
      setTimeout(() => {
        isPolling.value = false;
      }, 800);
    }
  }, 5000);
}

/**
 * 严格销毁轮询定时器，防止后台耗电与流量浪费
 */
function stopTelemetryPolling() {
  if (telemetryTimer) {
    clearInterval(telemetryTimer);
    telemetryTimer = null;
  }
}

/**
 * 切换 Tab 并在首次进入工单 Tab 时按需加载
 */
function switchTab(tabIndex: number) {
  currentTab.value = tabIndex;
  if (tabIndex === 1 && !ticketsLoadedOnce.value) {
    ticketsPage.value = 1;
    ticketsList.value = [];
    loadTickets();
  }
}

/**
 * 按需独立分页加载工单记录
 */
async function loadTickets() {
  ticketsLoading.value = true;
  try {
    const res = await getRackTicketsApi(currentRackCode.value, ticketsPage.value, 5);
    if (ticketsPage.value === 1) {
      ticketsList.value = res.records || [];
    } else {
      ticketsList.value = [...ticketsList.value, ...(res.records || [])];
    }
    ticketsTotal.value = res.total || 0;
    ticketsLoadedOnce.value = true;
  } catch (err: any) {
    uni.showToast({ title: err?.message || '拉取工单失败', icon: 'none' });
  } finally {
    ticketsLoading.value = false;
  }
}

function loadMoreTickets() {
  if (ticketsLoading.value) return;
  ticketsPage.value += 1;
  loadTickets();
}

function handleGoBack() {
  const pages = getCurrentPages();
  if (pages.length > 1) {
    uni.navigateBack();
  } else {
    uni.switchTab({ url: '/pages/index/index' });
  }
}

function copyRackCode() {
  uni.setClipboardData({
    data: currentRackCode.value,
    success: () => {
      uni.showToast({ title: '机柜编码已复制', icon: 'none' });
    }
  });
}

function getTicketStatusClass(status: number): string {
  switch (status) {
    case 0: return 'status-pending';
    case 1: return 'status-processing';
    case 5: return 'status-review';
    case 6: return 'status-done';
    default: return 'status-pending';
  }
}

function getTicketStatusText(status: number): string {
  switch (status) {
    case 0: return '待分配';
    case 1: return '排障中';
    case 5: return '待消警复核';
    case 6: return '已办结';
    default: return '流转中';
  }
}

function formatDateTime(val?: string | null): string {
  if (!val) return '--';
  return val.replace('T', ' ').slice(0, 19);
}

function formatTimeOnly(val?: string | null): string {
  if (!val) return '--';
  const parts = val.split('T');
  return parts.length > 1 ? parts[1].slice(0, 8) : val.slice(11, 19);
}
</script>

<style scoped>
.profile-container {
  min-height: 100vh;
  background: #0b1120;
  padding: 24rpx;
  padding-bottom: 80rpx;
}

/* 顶部自定义导航 */
.custom-navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 0 24rpx 0;
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.nav-back-icon {
  font-size: 54rpx;
  color: #38bdf8;
  line-height: 1;
}

.nav-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #f8fafc;
}

.rack-badge {
  background: rgba(56, 189, 248, 0.15);
  border: 1px solid rgba(56, 189, 248, 0.4);
  color: #38bdf8;
  padding: 6rpx 16rpx;
  border-radius: 20rpx;
  font-size: 22rpx;
  font-weight: 700;
}

/* 加载中状态 */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 24rpx;
}

.spinner {
  width: 56rpx;
  height: 56rpx;
  border: 4rpx solid rgba(56, 189, 248, 0.2);
  border-top-color: #38bdf8;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.loading-text {
  font-size: 26rpx;
  color: #94a3b8;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* BOLA 跨租户越权防护拦截 */
.forbidden-card {
  margin-top: 80rpx;
  background: rgba(15, 23, 42, 0.85);
  border: 1px solid rgba(239, 68, 68, 0.4);
  border-radius: 24rpx;
  padding: 48rpx 32rpx;
  text-align: center;
}

.forbidden-icon {
  font-size: 80rpx;
  margin-bottom: 20rpx;
}

.forbidden-title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #f87171;
  margin-bottom: 16rpx;
}

.forbidden-desc {
  display: block;
  font-size: 24rpx;
  color: #94a3b8;
  line-height: 1.6;
  margin-bottom: 36rpx;
}

.forbidden-actions {
  display: flex;
  justify-content: center;
}

.btn-back {
  background: #1e293b;
  border: 1px solid rgba(56, 189, 248, 0.4);
  color: #38bdf8;
  font-size: 26rpx;
  padding: 12rpx 48rpx;
  border-radius: 12rpx;
}

/* 内容卡片共有样式 */
.content-body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.rack-base-card,
.telemetry-card {
  background: rgba(15, 23, 42, 0.75);
  border: 1px solid rgba(51, 65, 85, 0.7);
  border-radius: 20rpx;
  padding: 28rpx;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}

.rack-title-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.rack-name {
  font-size: 32rpx;
  font-weight: 700;
  color: #f8fafc;
}

.status-tag {
  font-size: 20rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}

.status-online {
  background: rgba(34, 197, 94, 0.2);
  color: #4ade80;
  border: 1px solid rgba(34, 197, 94, 0.4);
}

.status-maintain {
  background: rgba(234, 179, 8, 0.2);
  color: #facc15;
  border: 1px solid rgba(234, 179, 8, 0.4);
}

.status-free {
  background: rgba(56, 189, 248, 0.2);
  color: #38bdf8;
  border: 1px solid rgba(56, 189, 248, 0.4);
}

.copy-code-btn {
  font-size: 22rpx;
  color: #64748b;
  background: rgba(30, 41, 59, 0.6);
  padding: 6rpx 14rpx;
  border-radius: 8rpx;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.meta-lbl {
  font-size: 20rpx;
  color: #64748b;
}

.meta-val {
  font-size: 24rpx;
  font-weight: 600;
  color: #cbd5e1;
}

.highlight-val {
  color: #38bdf8;
}

/* 动环卡片样式 */
.telemetry-title-group {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.section-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #e2e8f0;
}

.telemetry-badge {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 4rpx 12rpx;
  border-radius: 12rpx;
  font-size: 20rpx;
}

.badge-dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
}

.telemetry-online {
  background: rgba(34, 197, 94, 0.15);
  border: 1px solid rgba(34, 197, 94, 0.35);
  color: #4ade80;
}

.telemetry-online .badge-dot {
  background: #22c55e;
}

.telemetry-offline {
  background: rgba(245, 158, 11, 0.15);
  border: 1px solid rgba(245, 158, 11, 0.35);
  color: #fbbf24;
}

.telemetry-offline .badge-dot {
  background: #f59e0b;
}

.poll-tip {
  font-size: 20rpx;
  color: #64748b;
  transition: all 0.3s;
}

.poll-tip.pulsing {
  color: #38bdf8;
  font-weight: 700;
}

.gauges-row {
  display: flex;
  justify-content: space-around;
  margin: 16rpx 0 28rpx 0;
}

.gauge-col {
  flex: 1;
  display: flex;
  justify-content: center;
}

.sub-metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12rpx;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(51, 65, 85, 0.5);
  border-radius: 14rpx;
  padding: 16rpx;
}

.sub-metric-box {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.sub-metric-val {
  font-size: 26rpx;
  font-weight: 700;
  color: #f8fafc;
}

.sub-unit {
  font-size: 18rpx;
  font-weight: 400;
  color: #94a3b8;
  margin-left: 2rpx;
}

.sub-metric-lbl {
  font-size: 18rpx;
  color: #64748b;
  margin-top: 4rpx;
}

.update-footer {
  margin-top: 16rpx;
  text-align: right;
}

.update-time-text {
  font-size: 18rpx;
  color: #475569;
}

/* 越限告警区 */
.alarm-section {
  margin-top: -6rpx;
}

.alarm-alert-card {
  background: rgba(239, 68, 68, 0.12);
  border: 1px solid rgba(239, 68, 68, 0.35);
  border-radius: 16rpx;
  padding: 20rpx;
}

.alarm-title-tag {
  font-size: 24rpx;
  font-weight: 700;
  color: #f87171;
  margin-bottom: 12rpx;
  display: block;
}

.alarm-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.alarm-item {
  background: rgba(15, 23, 42, 0.6);
  border-radius: 10rpx;
  padding: 14rpx 18rpx;
}

.critical-alarm {
  border-left: 6rpx solid #ef4444;
}

.warning-alarm {
  border-left: 6rpx solid #f59e0b;
}

.alarm-item-top {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 6rpx;
}

.alarm-level-badge {
  font-size: 18rpx;
  background: rgba(239, 68, 68, 0.25);
  color: #f87171;
  padding: 2rpx 8rpx;
  border-radius: 6rpx;
  font-weight: 700;
}

.alarm-type {
  font-size: 22rpx;
  color: #e2e8f0;
  flex: 1;
}

.alarm-time {
  font-size: 18rpx;
  color: #64748b;
}

.alarm-desc {
  font-size: 20rpx;
  color: #94a3b8;
  display: block;
}

.alarm-normal-card {
  background: rgba(34, 197, 94, 0.08);
  border: 1px solid rgba(34, 197, 94, 0.25);
  border-radius: 16rpx;
  padding: 18rpx 24rpx;
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.normal-icon {
  font-size: 28rpx;
}

.normal-text {
  font-size: 22rpx;
  color: #4ade80;
}

/* Tabs 架构 */
.tabs-container {
  margin-top: 10rpx;
}

.tabs-header {
  display: flex;
  background: rgba(15, 23, 42, 0.8);
  border: 1px solid rgba(51, 65, 85, 0.6);
  border-radius: 16rpx;
  padding: 6rpx;
  margin-bottom: 20rpx;
}

.tab-item {
  flex: 1;
  height: 68rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  border-radius: 12rpx;
  transition: all 0.2s ease;
}

.tab-item.active {
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.3), rgba(2, 132, 199, 0.15));
  border: 1px solid rgba(56, 189, 248, 0.4);
}

.tab-label {
  font-size: 24rpx;
  color: #94a3b8;
}

.tab-item.active .tab-label {
  color: #f8fafc;
  font-weight: 700;
}

.tab-badge {
  font-size: 18rpx;
  background: rgba(30, 41, 59, 0.8);
  color: #38bdf8;
  padding: 2rpx 10rpx;
  border-radius: 10rpx;
}

.tab-pane {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

/* 42U 设备槽位画像 */
.u-summary-card {
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(51, 65, 85, 0.6);
  border-radius: 16rpx;
  padding: 20rpx;
}

.u-stat-row {
  display: flex;
  justify-content: space-around;
  margin-bottom: 16rpx;
}

.u-stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.u-stat-val {
  font-size: 30rpx;
  font-weight: 700;
}

.text-cyan { color: #38bdf8; }
.text-green { color: #4ade80; }
.text-amber { color: #fbbf24; }

.u-stat-lbl {
  font-size: 20rpx;
  color: #64748b;
  margin-top: 4rpx;
}

.u-progress-bar {
  height: 12rpx;
  background: #1e293b;
  border-radius: 6rpx;
  overflow: hidden;
}

.u-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #38bdf8, #22c55e);
  border-radius: 6rpx;
  transition: width 0.4s ease;
}

.devices-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.device-card {
  background: rgba(15, 23, 42, 0.75);
  border: 1px solid rgba(51, 65, 85, 0.6);
  border-radius: 14rpx;
  padding: 20rpx;
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.device-u-badge {
  background: rgba(30, 41, 59, 0.9);
  border: 1px solid rgba(56, 189, 248, 0.3);
  border-radius: 10rpx;
  padding: 10rpx 14rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 110rpx;
}

.u-range-text {
  font-size: 22rpx;
  font-weight: 700;
  color: #38bdf8;
}

.u-height-text {
  font-size: 18rpx;
  color: #64748b;
}

.device-info {
  flex: 1;
}

.device-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8rpx;
}

.device-name {
  font-size: 26rpx;
  font-weight: 600;
  color: #f8fafc;
}

.device-type-badge {
  font-size: 18rpx;
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
  padding: 2rpx 8rpx;
  border-radius: 6rpx;
}

.device-meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.device-code {
  font-size: 20rpx;
  color: #64748b;
}

.device-status-badge {
  font-size: 18rpx;
  padding: 2rpx 8rpx;
  border-radius: 6rpx;
}

.status-ok {
  background: rgba(34, 197, 94, 0.2);
  color: #4ade80;
}

.status-warn {
  background: rgba(234, 179, 8, 0.2);
  color: #facc15;
}

/* 维保工单卡片样式 */
.tickets-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.ticket-card {
  background: rgba(15, 23, 42, 0.75);
  border: 1px solid rgba(51, 65, 85, 0.6);
  border-radius: 14rpx;
  padding: 20rpx;
}

.ticket-top {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 12rpx;
}

.ticket-id-tag {
  font-size: 20rpx;
  font-weight: 700;
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.15);
  padding: 2rpx 8rpx;
  border-radius: 6rpx;
}

.ticket-type-badge {
  font-size: 20rpx;
  color: #94a3b8;
  flex: 1;
}

.ticket-status-pill {
  font-size: 18rpx;
  padding: 2rpx 10rpx;
  border-radius: 8rpx;
  font-weight: 600;
}

.status-pending {
  background: rgba(100, 116, 139, 0.3);
  color: #cbd5e1;
}

.status-processing {
  background: rgba(14, 165, 233, 0.25);
  color: #38bdf8;
}

.status-review {
  background: rgba(245, 158, 11, 0.25);
  color: #fbbf24;
}

.status-done {
  background: rgba(34, 197, 94, 0.25);
  color: #4ade80;
}

.ticket-title {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: #f8fafc;
  margin-bottom: 12rpx;
}

.ticket-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 20rpx;
  color: #64748b;
}

.tickets-pagination {
  margin-top: 12rpx;
}

.btn-load-more {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(71, 85, 105, 0.6);
  color: #94a3b8;
  font-size: 24rpx;
  border-radius: 12rpx;
  height: 72rpx;
  line-height: 72rpx;
}

.no-more-tip {
  text-align: center;
  font-size: 20rpx;
  color: #475569;
  padding: 16rpx 0;
}

.sub-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  padding: 60rpx 0;
  font-size: 24rpx;
  color: #94a3b8;
}

.spinner-sm {
  width: 32rpx;
  height: 32rpx;
  border: 3rpx solid rgba(56, 189, 248, 0.2);
  border-top-color: #38bdf8;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.empty-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60rpx 20rpx;
  gap: 16rpx;
}

.empty-icon {
  font-size: 64rpx;
}

.empty-text {
  font-size: 24rpx;
  color: #64748b;
}
</style>
