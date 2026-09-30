<template>
  <view class="detail-container">
    <!-- 自定义顶部导航 -->
    <view class="nav-bar">
      <view class="nav-left" @tap="handleGoBack">
        <text class="nav-back">‹</text>
        <text class="nav-title">工单排障详情</text>
      </view>
      <view class="nav-right" v-if="detail">
        <text class="ticket-id-tag">#{{ detail.ticketId }}</text>
      </view>
    </view>

    <!-- 加载中 -->
    <view v-if="loading && !detail" class="loading-state">
      <view class="spinner"></view>
      <text class="loading-lbl">正在获取工单详情...</text>
    </view>

    <!-- 详情主体 -->
    <view v-else-if="detail" class="detail-body">
      <!-- 1. 工单头部态势卡片 -->
      <view class="card header-card">
        <view class="status-row">
          <view class="status-badge" :class="getStatusClass(detail.status)">
            <text class="dot"></text>
            <text class="status-name">{{ detail.statusText }}</text>
          </view>
          <text class="type-pill">{{ formatTicketType(detail.ticketType) }}</text>
        </view>

        <text class="ticket-title">{{ detail.title }}</text>

        <!-- 权威机架物理拓扑 (替代 GPS 卫星定位) -->
        <view class="topology-box">
          <text class="topo-icon">🏢</text>
          <view class="topo-info">
            <text class="topo-lbl">机架权威物理位置拓扑</text>
            <text class="topo-val">{{ detail.rackLocation || (detail.roomName + ' ➔ ' + detail.rackCode) }}</text>
          </view>
        </view>

        <!-- 实时温度与防抖判定 -->
        <view class="temp-row">
          <text class="temp-lbl">当前机柜实时进风温度：</text>
          <text class="temp-val" :class="detail.isDebounceStable ? 'text-green' : 'text-amber'">
            {{ detail.telemetryTemp != null ? detail.telemetryTemp + ' ℃' : '遥测离线' }}
          </text>
          <text class="temp-status-tag" :class="detail.isDebounceStable ? 'tag-safe' : 'tag-warn'">
            {{ detail.isDebounceStable ? '安全区间 (<=35℃)' : '越限观察中' }}
          </text>
        </view>

        <view class="meta-row">
          <text class="meta-txt">责任工号：{{ detail.operatorName || '未指定' }}</text>
          <text class="meta-txt">创建时间：{{ formatTime(detail.createTime) }}</text>
        </view>
      </view>

      <!-- 2. RAG 排障 SOP 规程建议卡片 -->
      <view class="card sop-card" v-if="detail.sopGuide">
        <view class="card-title-row">
          <text class="card-icon">🧠</text>
          <text class="card-title">AIOps 排障 SOP 指导建议</text>
        </view>
        <text class="sop-content">{{ detail.sopGuide }}</text>
      </view>

      <!-- 3. 关联活动告警卡片 -->
      <view class="card alarm-card" v-if="detail.alarmId">
        <view class="card-title-row">
          <text class="card-icon">🚨</text>
          <text class="card-title">关联动环越限告警</text>
          <text class="alarm-level-tag">{{ detail.alarmLevel || 'CRITICAL' }}</text>
        </view>
        <text class="alarm-desc">告警编号 #{{ detail.alarmId }}，触发类型：{{ detail.alarmType || '温度过高' }}</text>
      </view>

      <!-- 4. 状态 6: 动环 5 分钟滞后防抖自动归档判定卡片 -->
      <view class="card debounce-card" v-if="detail.status === 6">
        <view class="debounce-header">
          <text class="debounce-pulse">⚡</text>
          <text class="debounce-title">动环 5 分钟滞后防抖判定中</text>
        </view>
        <text class="debounce-desc">
          消警申请已提交。后端防抖守卫守护任务正在对机柜温度进行连续采样。
          当温度连续 5 分钟稳定在 35.0℃ 安全阈值以下，系统将自动推进为【已办结归档】并消除关联告警；若超过 30 分钟仍未回稳，将触发逃生通道自动打回排障中。
        </text>
        <view class="debounce-meta">
          <text class="debounce-time">服务端权威消警时间戳：{{ formatTime(detail.resolveTime) }}</text>
        </view>
      </view>

      <!-- 5. 已上传存证照片回显卡片 -->
      <view class="card evidence-card" v-if="detail.evidenceObjectKey || localEvidencePreview">
        <view class="card-title-row">
          <text class="card-icon">📷</text>
          <text class="card-title">现场双步防爆水印存证照片</text>
        </view>
        <view class="photo-preview-wrap">
          <image
            class="evidence-img"
            :src="localEvidencePreview || detail.evidenceViewUrl"
            mode="aspectFit"
            @tap="previewPhoto"
          />
        </view>
        <view class="hash-row" v-if="detail.evidenceHash">
          <text class="hash-lbl">SHA-256 指纹：</text>
          <text class="hash-val">{{ detail.evidenceHash }}</text>
        </view>
      </view>

      <!-- 6. 状态 2: 现场排障作业区 (拍照、输入说明、挂起、消警) -->
      <view class="card action-form-card" v-if="detail.status === 2">
        <view class="card-title-row">
          <text class="card-icon">🛠️</text>
          <text class="card-title">现场排障消警提交</text>
        </view>

        <!-- 拍照存证入口 -->
        <view class="photo-uploader" @tap="handleTakeWatermarkPhoto">
          <view v-if="!localEvidencePreview" class="upload-placeholder">
            <text class="camera-icon">📸</text>
            <text class="upload-text">调起相机拍摄现场存证</text>
            <text class="upload-sub">双步防爆流水线：限制1920px ➔ 自动嵌物联拓扑防伪水印</text>
          </view>
          <view v-else class="uploaded-thumb">
            <image class="thumb-img" :src="localEvidencePreview" mode="aspectFill" />
            <view class="reupload-mask">
              <text class="reupload-txt">点击重新拍摄</text>
            </view>
          </view>
        </view>

        <!-- 排障处置记录输入框 -->
        <view class="form-group">
          <text class="form-lbl">现场处置与故障排查说明 <text class="req">*</text></text>
          <textarea
            v-model="processNotesInput"
            class="form-textarea"
            placeholder="请详细描述现场根因排查、百叶窗/空调调整或备件更换细节..."
            placeholder-class="textarea-placeholder"
          />
        </view>

        <!-- 按钮区 -->
        <view class="btn-group">
          <button class="btn-secondary" @tap="openSuspendModal">
            ⏸️ 申请挂起待备件
          </button>
          <button
            class="btn-primary"
            :loading="submitting"
            @tap="handleSubmitResolve"
          >
            ✅ 提交现场消警
          </button>
        </view>
      </view>

      <!-- 7. 状态 0/1: 一键接单主卡片 -->
      <view class="bottom-action-bar" v-if="detail.status === 0 || detail.status === 1">
        <button class="btn-large-primary" :loading="submitting" @tap="handleAcceptTicket">
          ⚡ 现场工程师一键接单
        </button>
      </view>

      <!-- 8. 状态 3: 恢复排障 -->
      <view class="bottom-action-bar" v-if="detail.status === 3">
        <button class="btn-large-primary" :loading="submitting" @tap="handleResumeTicket">
          ▶️ 备件就绪，恢复现场排障
        </button>
      </view>
    </view>

    <!-- 申请挂起原因弹窗 -->
    <view v-if="showSuspendModal" class="modal-mask" @tap.self="showSuspendModal = false">
      <view class="modal-box">
        <text class="modal-title">申请工单挂起</text>
        <text class="modal-desc">因现场缺少特定备件、光模块或需原厂上门支持时可申请暂时挂起：</text>
        <textarea
          v-model="suspendReason"
          class="modal-textarea"
          placeholder="请输入挂起原因与备件申请说明 (如等待40G光模块到货)..."
        />
        <view class="modal-btns">
          <button class="btn-modal-cancel" @tap="showSuspendModal = false">取消</button>
          <button class="btn-modal-confirm" @tap="handleConfirmSuspend">确认挂起</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import {
  getTicketDetailApi,
  acceptTicketApi,
  suspendTicketApi,
  resumeTicketApi,
  resolveTicketApi,
  type MobileTicketDetailVO
} from '@/api/ticket';
import { getPresignedUrlApi, uploadToPresignedUrl, uploadFileFallbackApi } from '@/api/oss';
import { generateWatermarkedPhoto } from '@/utils/watermark';
import { useAuthStore } from '@/store/modules/auth';

const authStore = useAuthStore();

const currentTicketId = ref<number | null>(null);
const loading = ref(true);
const submitting = ref(false);
const detail = ref<MobileTicketDetailVO | null>(null);

// 排障表单
const processNotesInput = ref('');
const uploadedObjectKey = ref('');
const uploadedHash = ref('');
const localEvidencePreview = ref('');

// 挂起弹窗
const showSuspendModal = ref(false);
const suspendReason = ref('');

onLoad((options: any) => {
  if (options && options.ticketId) {
    currentTicketId.value = Number(options.ticketId);
    loadDetail();
  }
});

async function loadDetail() {
  if (!currentTicketId.value) return;
  loading.value = true;
  try {
    const data = await getTicketDetailApi(currentTicketId.value);
    detail.value = data;
    if (data.processNotes && !processNotesInput.value) {
      processNotesInput.value = data.processNotes;
    }
  } catch (err: any) {
    uni.showToast({ title: err?.message || '获取工单失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

/**
 * 现场工程师一键接单
 */
async function handleAcceptTicket() {
  if (!currentTicketId.value) return;
  submitting.value = true;
  try {
    await acceptTicketApi(currentTicketId.value);
    uni.showToast({ title: '接单成功，已进入排障状态', icon: 'success' });
    await loadDetail();
  } catch (err: any) {
    uni.showToast({ title: err?.message || '接单失败', icon: 'none' });
  } finally {
    submitting.value = false;
  }
}

/**
 * 恢复排障
 */
async function handleResumeTicket() {
  if (!currentTicketId.value) return;
  submitting.value = true;
  try {
    await resumeTicketApi(currentTicketId.value);
    uni.showToast({ title: '工单已恢复排障', icon: 'success' });
    await loadDetail();
  } catch (err: any) {
    uni.showToast({ title: err?.message || '恢复失败', icon: 'none' });
  } finally {
    submitting.value = false;
  }
}

/**
 * 调起现场相机 ➔ 双步防爆水印 ➔ S3 预签名直传
 */
function handleTakeWatermarkPhoto() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed', 'original'],
    sourceType: ['camera', 'album'],
    success: async (res) => {
      const rawPath = res.tempFilePaths[0];
      uni.showLoading({ title: '双步防爆处理中...' });

      try {
        // 1. 双步防爆水印流水线：限制 1920px + 叠加物理拓扑
        const watermarkRes = await generateWatermarkedPhoto(rawPath, {
          rackLocation: detail.value?.rackLocation || '华东01 ➔ A区 ➔ RACK-A01',
          operatorName: authStore.user?.nickName || authStore.user?.username || '现场工程师',
          timestampText: new Date().toISOString().replace('T', ' ').slice(0, 19)
        });

        localEvidencePreview.value = watermarkRes.filePath;

        // 2. 申请 S3 预签名直传 PUT 凭证
        uni.showLoading({ title: '申请 S3 直传凭证...' });
        const presigned = await getPresignedUrlApi({
          ticketId: currentTicketId.value || undefined,
          fileName: 'evidence.jpg',
          fileType: 'jpg',
          contentType: 'image/jpeg'
        });

        // 3. 客户端直传 MinIO S3
        uni.showLoading({ title: 'S3 存证上传中...' });
        try {
          await uploadToPresignedUrl(presigned.uploadUrl, watermarkRes.filePath, 'image/jpeg');
          uploadedObjectKey.value = presigned.objectKey;
        } catch (s3Err) {
          console.warn('[OSS] S3 直传异常，启用后端流式中转兜底', s3Err);
          const fallbackRes = await uploadFileFallbackApi(watermarkRes.filePath, currentTicketId.value || undefined);
          uploadedObjectKey.value = fallbackRes.objectKey;
        }

        uni.hideLoading();
        uni.showToast({ title: '现场存证上传成功', icon: 'success' });
      } catch (err: any) {
        uni.hideLoading();
        uni.showToast({ title: err?.message || '存证拍照失败', icon: 'none' });
      }
    }
  });
}

function openSuspendModal() {
  suspendReason.value = '';
  showSuspendModal.value = true;
}

async function handleConfirmSuspend() {
  if (!currentTicketId.value) return;
  showSuspendModal.value = false;
  submitting.value = true;
  try {
    await suspendTicketApi(currentTicketId.value, suspendReason.value);
    uni.showToast({ title: '工单已挂起', icon: 'none' });
    await loadDetail();
  } catch (err: any) {
    uni.showToast({ title: err?.message || '挂起失败', icon: 'none' });
  } finally {
    submitting.value = false;
  }
}

/**
 * 提交现场消警申请
 */
async function handleSubmitResolve() {
  if (!currentTicketId.value) return;

  if (!uploadedObjectKey.value && !detail.value?.evidenceObjectKey) {
    uni.showToast({ title: '请先拍摄现场存证照片', icon: 'none' });
    return;
  }
  if (!processNotesInput.value.trim()) {
    uni.showToast({ title: '请填写现场处置与排障记录', icon: 'none' });
    return;
  }

  submitting.value = true;
  try {
    await resolveTicketApi({
      ticketId: currentTicketId.value,
      processNotes: processNotesInput.value.trim(),
      evidenceObjectKey: uploadedObjectKey.value || detail.value?.evidenceObjectKey || '',
      rackLocation: detail.value?.rackLocation
    });

    uni.showToast({ title: '消警申请已提交，进入防抖观察期', icon: 'success' });
    await loadDetail();
  } catch (err: any) {
    uni.showToast({ title: err?.message || '提交消警失败', icon: 'none' });
  } finally {
    submitting.value = false;
  }
}

function previewPhoto() {
  const url = localEvidencePreview.value || detail.value?.evidenceViewUrl;
  if (url) {
    uni.previewImage({ urls: [url] });
  }
}

function handleGoBack() {
  const pages = getCurrentPages();
  if (pages.length > 1) {
    uni.navigateBack();
  } else {
    uni.switchTab({ url: '/pages/ticket/list' });
  }
}

function getStatusClass(status?: number): string {
  switch (status) {
    case 0:
    case 1: return 'st-pending';
    case 2: return 'st-processing';
    case 3: return 'st-suspended';
    case 6: return 'st-review';
    case 7: return 'st-done';
    default: return 'st-pending';
  }
}

function formatTicketType(type?: string): string {
  if (type === 'ALARM_REPAIR') return '故障排障';
  if (type === 'ROUTINE_CHECK') return '常规巡检';
  return type || '日常工单';
}

function formatTime(val?: string | null): string {
  if (!val) return '--';
  return val.replace('T', ' ').slice(0, 19);
}
</script>

<style scoped>
.detail-container {
  min-height: 100vh;
  background: #0b1120;
  padding: 24rpx;
  padding-bottom: 80rpx;
}

/* 导航 */
.nav-bar {
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

.nav-back {
  font-size: 54rpx;
  color: #38bdf8;
  line-height: 1;
}

.nav-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #f8fafc;
}

.ticket-id-tag {
  font-size: 22rpx;
  font-weight: 700;
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.15);
  border: 1px solid rgba(56, 189, 248, 0.35);
  padding: 4rpx 14rpx;
  border-radius: 12rpx;
}

/* 卡片基础 */
.detail-body {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.card {
  background: rgba(15, 23, 42, 0.75);
  border: 1px solid rgba(51, 65, 85, 0.7);
  border-radius: 20rpx;
  padding: 28rpx;
}

.card-title-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.card-icon {
  font-size: 30rpx;
}

.card-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #e2e8f0;
  flex: 1;
}

/* 头部卡片 */
.status-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 4rpx 14rpx;
  border-radius: 12rpx;
  font-size: 22rpx;
  font-weight: 600;
}

.dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
}

.st-pending {
  background: rgba(14, 165, 233, 0.15);
  color: #38bdf8;
  border: 1px solid rgba(14, 165, 233, 0.35);
}
.st-pending .dot { background: #38bdf8; }

.st-processing {
  background: rgba(234, 179, 8, 0.15);
  color: #facc15;
  border: 1px solid rgba(234, 179, 8, 0.35);
}
.st-processing .dot { background: #facc15; }

.st-suspended {
  background: rgba(239, 68, 68, 0.15);
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.35);
}
.st-suspended .dot { background: #f87171; }

.st-review {
  background: rgba(168, 85, 247, 0.15);
  color: #c084fc;
  border: 1px solid rgba(168, 85, 247, 0.35);
}
.st-review .dot { background: #c084fc; }

.st-done {
  background: rgba(34, 197, 94, 0.15);
  color: #4ade80;
  border: 1px solid rgba(34, 197, 94, 0.35);
}
.st-done .dot { background: #4ade80; }

.type-pill {
  font-size: 22rpx;
  color: #94a3b8;
}

.ticket-title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #f8fafc;
  line-height: 1.4;
  margin-bottom: 20rpx;
}

/* 拓扑信息 */
.topology-box {
  background: rgba(30, 41, 59, 0.7);
  border: 1px solid rgba(56, 189, 248, 0.25);
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

.topo-icon {
  font-size: 36rpx;
}

.topo-lbl {
  display: block;
  font-size: 20rpx;
  color: #64748b;
}

.topo-val {
  display: block;
  font-size: 24rpx;
  font-weight: 700;
  color: #38bdf8;
  margin-top: 4rpx;
}

.temp-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-bottom: 16rpx;
}

.temp-lbl {
  font-size: 22rpx;
  color: #94a3b8;
}

.temp-val {
  font-size: 26rpx;
  font-weight: 700;
}

.text-green { color: #4ade80; }
.text-amber { color: #fbbf24; }

.temp-status-tag {
  font-size: 18rpx;
  padding: 2rpx 8rpx;
  border-radius: 6rpx;
}

.tag-safe {
  background: rgba(34, 197, 94, 0.2);
  color: #4ade80;
}

.tag-warn {
  background: rgba(234, 179, 8, 0.2);
  color: #facc15;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  border-top: 1px solid rgba(51, 65, 85, 0.4);
  padding-top: 14rpx;
  font-size: 20rpx;
  color: #64748b;
}

/* SOP */
.sop-card {
  background: rgba(14, 165, 233, 0.08);
  border-color: rgba(56, 189, 248, 0.3);
}

.sop-content {
  font-size: 24rpx;
  color: #cbd5e1;
  line-height: 1.6;
  white-space: pre-wrap;
}

/* 告警 */
.alarm-card {
  background: rgba(239, 68, 68, 0.08);
  border-color: rgba(239, 68, 68, 0.3);
}

.alarm-level-tag {
  font-size: 18rpx;
  font-weight: 700;
  background: rgba(239, 68, 68, 0.25);
  color: #f87171;
  padding: 2rpx 10rpx;
  border-radius: 6rpx;
}

.alarm-desc {
  font-size: 22rpx;
  color: #cbd5e1;
}

/* 防抖判定中 */
.debounce-card {
  background: linear-gradient(135deg, rgba(168, 85, 247, 0.15), rgba(14, 165, 233, 0.1));
  border-color: rgba(168, 85, 247, 0.4);
}

.debounce-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 12rpx;
}

.debounce-pulse {
  font-size: 32rpx;
  animation: pulse 1s infinite alternate;
}

@keyframes pulse {
  from { transform: scale(0.9); opacity: 0.7; }
  to { transform: scale(1.1); opacity: 1; }
}

.debounce-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #c084fc;
}

.debounce-desc {
  font-size: 22rpx;
  color: #cbd5e1;
  line-height: 1.6;
  margin-bottom: 14rpx;
  display: block;
}

.debounce-meta {
  font-size: 20rpx;
  color: #94a3b8;
}

/* 存证图片 */
.photo-preview-wrap {
  margin: 16rpx 0;
  background: #020617;
  border-radius: 12rpx;
  overflow: hidden;
  height: 380rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.evidence-img {
  width: 100%;
  height: 100%;
}

.hash-row {
  font-size: 20rpx;
  color: #64748b;
  word-break: break-all;
}

.hash-val {
  color: #4ade80;
  font-family: monospace;
}

/* 作业卡片 */
.photo-uploader {
  background: rgba(30, 41, 59, 0.7);
  border: 1px dashed rgba(56, 189, 248, 0.4);
  border-radius: 16rpx;
  height: 220rpx;
  margin-bottom: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  position: relative;
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 20rpx;
  text-align: center;
}

.camera-icon {
  font-size: 48rpx;
}

.upload-text {
  font-size: 26rpx;
  font-weight: 700;
  color: #38bdf8;
}

.upload-sub {
  font-size: 18rpx;
  color: #64748b;
}

.uploaded-thumb {
  width: 100%;
  height: 100%;
  position: relative;
}

.thumb-img {
  width: 100%;
  height: 100%;
}

.reupload-mask {
  position: absolute;
  bottom: 0;
  inset-inline: 0;
  background: rgba(0, 0, 0, 0.6);
  padding: 8rpx 0;
  text-align: center;
}

.reupload-txt {
  font-size: 20rpx;
  color: #cbd5e1;
}

.form-group {
  margin-bottom: 24rpx;
}

.form-lbl {
  display: block;
  font-size: 24rpx;
  font-weight: 600;
  color: #e2e8f0;
  margin-bottom: 12rpx;
}

.req {
  color: #ef4444;
}

.form-textarea {
  width: 100%;
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(71, 85, 105, 0.6);
  border-radius: 12rpx;
  padding: 20rpx;
  color: #f8fafc;
  font-size: 24rpx;
  height: 180rpx;
  box-sizing: border-box;
}

.textarea-placeholder {
  color: #64748b;
}

.btn-group {
  display: flex;
  gap: 16rpx;
}

.btn-secondary {
  flex: 1;
  background: rgba(30, 41, 59, 0.85);
  border: 1px solid rgba(71, 85, 105, 0.6);
  color: #94a3b8;
  font-size: 26rpx;
  border-radius: 12rpx;
  height: 80rpx;
  line-height: 80rpx;
}

.btn-primary {
  flex: 1.5;
  background: linear-gradient(135deg, #0284c7, #0ea5e9);
  color: #fff;
  font-size: 26rpx;
  font-weight: 700;
  border: none;
  border-radius: 12rpx;
  height: 80rpx;
  line-height: 80rpx;
}

/* 底部大按钮 */
.bottom-action-bar {
  margin-top: 12rpx;
}

.btn-large-primary {
  background: linear-gradient(135deg, #0284c7, #0ea5e9);
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
  border: none;
  border-radius: 16rpx;
  height: 90rpx;
  line-height: 90rpx;
  box-shadow: 0 8rpx 24rpx rgba(14, 165, 233, 0.3);
}

/* 弹窗 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.75);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx;
  z-index: 999;
}

.modal-box {
  width: 100%;
  max-width: 600rpx;
  background: #0f172a;
  border: 1px solid rgba(56, 189, 248, 0.35);
  border-radius: 20rpx;
  padding: 32rpx;
}

.modal-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #f8fafc;
  margin-bottom: 12rpx;
}

.modal-desc {
  display: block;
  font-size: 22rpx;
  color: #94a3b8;
  line-height: 1.5;
  margin-bottom: 20rpx;
}

.modal-textarea {
  width: 100%;
  background: rgba(30, 41, 59, 0.85);
  border: 1px solid rgba(71, 85, 105, 0.6);
  border-radius: 12rpx;
  padding: 16rpx;
  color: #f8fafc;
  font-size: 24rpx;
  height: 150rpx;
  box-sizing: border-box;
  margin-bottom: 24rpx;
}

.modal-btns {
  display: flex;
  gap: 16rpx;
}

.btn-modal-cancel {
  flex: 1;
  background: rgba(30, 41, 59, 0.8);
  color: #94a3b8;
  border: 1px solid rgba(71, 85, 105, 0.5);
  font-size: 26rpx;
  border-radius: 12rpx;
  height: 76rpx;
  line-height: 76rpx;
}

.btn-modal-confirm {
  flex: 1;
  background: #ef4444;
  color: #fff;
  border: none;
  font-size: 26rpx;
  font-weight: 700;
  border-radius: 12rpx;
  height: 76rpx;
  line-height: 76rpx;
}

/* 加载中 */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 20rpx;
}

.spinner {
  width: 50rpx;
  height: 50rpx;
  border: 4rpx solid rgba(56, 189, 248, 0.2);
  border-top-color: #38bdf8;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.loading-lbl {
  font-size: 26rpx;
  color: #94a3b8;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
