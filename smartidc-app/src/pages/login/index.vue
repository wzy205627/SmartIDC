<template>
  <view class="login-container">
    <!-- 顶部科技感品牌区 -->
    <view class="brand-section">
      <view class="brand-logo">
        <text class="brand-icon">⚡</text>
      </view>
      <text class="brand-title">智维云 · 随行端</text>
      <text class="brand-subtitle">SmartIDC 现场运维安全接入中枢</text>
    </view>

    <!-- 弱网环境感知提示 -->
    <view v-if="!networkStore.isOnline" class="offline-banner">
      <text class="offline-icon">⚠️</text>
      <text class="offline-text">当前处于屏蔽机房弱网离线状态，已启用离线缓存凭证</text>
    </view>

    <!-- 登录卡片容器 -->
    <view class="login-card">
      <!-- 模式一: 微信一键授权快捷登录 (小程序专有) -->
      <!-- #ifdef MP-WEIXIN -->
      <view class="wx-login-area">
        <button
          class="btn-primary wx-btn"
          :loading="loading"
          @tap="handleWxLogin"
        >
          <text class="wx-icon">💬</text>
          <text>微信一键安全登录</text>
        </button>
        <view class="divider">
          <view class="line"></view>
          <text class="divider-text">或使用工号登录</text>
          <view class="line"></view>
        </view>
      </view>
      <!-- #endif -->

      <!-- 模式二: 工程师工号密码登录 -->
      <view class="form-area">
        <view class="input-group">
          <text class="input-label">运维工号 / 账号</text>
          <input
            v-model="loginForm.username"
            class="input-field"
            placeholder="请输入工号 (如 engineer_li)"
            placeholder-class="placeholder-style"
          />
        </view>

        <view class="input-group">
          <text class="input-label">认证口令</text>
          <input
            v-model="loginForm.password"
            class="input-field"
            type="password"
            placeholder="请输入密码 (默认 123456)"
            placeholder-class="placeholder-style"
          />
        </view>

        <button
          class="btn-primary"
          :loading="loading"
          @tap="handlePasswordLogin"
        >
          <text>登 录</text>
        </button>

        <!-- 本地联调/测试一键 Mock 入口 -->
        <view class="mock-section">
          <button class="btn-ghost" @tap="handleMockQuickLogin">
            <text>⚡ 本地离线开发 Mock 一键登录 (ENG-001)</text>
          </button>
        </view>
      </view>
    </view>

    <!-- 模式三: 首次微信登录绑定工号激活弹窗 (NEED_BIND 场景) -->
    <view v-if="showBindModal" class="modal-mask">
      <view class="modal-card">
        <view class="modal-header">
          <text class="modal-title">工程师身份首绑核验</text>
          <text class="modal-desc">检测到当前微信号未与 IDC 运维工号关联，请核验工号密码完成激活绑定。</text>
        </view>
        <view class="modal-body">
          <view class="input-group">
            <text class="input-label">工程师工号</text>
            <input
              v-model="bindForm.username"
              class="input-field"
              placeholder="请输入工程师工号"
              placeholder-class="placeholder-style"
            />
          </view>
          <view class="input-group">
            <text class="input-label">初始认证密码</text>
            <input
              v-model="bindForm.password"
              class="input-field"
              type="password"
              placeholder="请输入密码"
              placeholder-class="placeholder-style"
            />
          </view>
        </view>
        <view class="modal-footer">
          <button class="btn-secondary" @tap="showBindModal = false">取消</button>
          <button class="btn-primary" :loading="bindLoading" @tap="handleConfirmBind">核验并绑定</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import { useNetworkStore } from '@/store/modules/network';

const authStore = useAuthStore();
const networkStore = useNetworkStore();

const loading = ref(false);
const bindLoading = ref(false);
const showBindModal = ref(false);
const currentBindTicket = ref('');

const loginForm = reactive({
  username: 'engineer_li',
  password: '123456'
});

const bindForm = reactive({
  username: '',
  password: ''
});

// 1. 微信登录流程
async function handleWxLogin() {
  loading.value = true;
  try {
    // #ifdef MP-WEIXIN
    uni.login({
      provider: 'weixin',
      success: async (res) => {
        if (res.code) {
          await processWxAuth(res.code);
        } else {
          uni.showToast({ title: '获取微信凭证失败', icon: 'none' });
        }
      },
      fail: () => {
        uni.showToast({ title: '微信授权调用失败', icon: 'none' });
      },
      complete: () => {
        loading.value = false;
      }
    });
    // #endif

    // #ifndef MP-WEIXIN
    // 非小程序平台模拟微信登录
    await processWxAuth('wx_dev_simulated_code');
    loading.value = false;
    // #endif
  } catch (err: any) {
    loading.value = false;
    uni.showToast({ title: err.message || '微信认证异常', icon: 'none' });
  }
}

async function processWxAuth(code: string) {
  const result = await authStore.loginWithWechat({ code });
  if (result.authState === 'LOGIN_SUCCESS') {
    uni.showToast({ title: '登录成功', icon: 'success' });
    uni.switchTab({ url: '/pages/index/index' });
  } else if (result.authState === 'NEED_BIND') {
    currentBindTicket.value = result.bindTicket || '';
    bindForm.username = '';
    bindForm.password = '';
    showBindModal.value = true;
  } else {
    uni.showToast({ title: '未授权运维人员，禁止接入', icon: 'none' });
  }
}

// 2. 工号密码常规登录
async function handlePasswordLogin() {
  if (!loginForm.username.trim() || !loginForm.password.trim()) {
    uni.showToast({ title: '工号与密码不可为空', icon: 'none' });
    return;
  }
  loading.value = true;
  try {
    const result = await authStore.loginWithPassword({
      username: loginForm.username.trim(),
      password: loginForm.password.trim()
    });
    if (result.authState === 'LOGIN_SUCCESS') {
      uni.showToast({ title: '登录成功', icon: 'success' });
      uni.switchTab({ url: '/pages/index/index' });
    }
  } catch (err: any) {
    uni.showToast({ title: err.message || '账号或密码错误', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

// 3. 本地 Mock 极速接入
async function handleMockQuickLogin() {
  loading.value = true;
  try {
    const result = await authStore.loginWithWechat({
      mock: true,
      mockUserCode: 'ENG-001'
    });
    if (result.authState === 'LOGIN_SUCCESS') {
      uni.showToast({ title: 'Mock 联调成功', icon: 'success' });
      uni.switchTab({ url: '/pages/index/index' });
    }
  } catch (err: any) {
    uni.showToast({ title: err.message || 'Mock 登录失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

// 4. 首绑核验确认
async function handleConfirmBind() {
  if (!bindForm.username.trim() || !bindForm.password.trim()) {
    uni.showToast({ title: '请完整输入工号和密码', icon: 'none' });
    return;
  }
  bindLoading.value = true;
  try {
    const result = await authStore.bindWechat({
      bindTicket: currentBindTicket.value,
      username: bindForm.username.trim(),
      password: bindForm.password.trim()
    });
    if (result.authState === 'LOGIN_SUCCESS') {
      showBindModal.value = false;
      uni.showToast({ title: '首绑激活成功', icon: 'success' });
      uni.switchTab({ url: '/pages/index/index' });
    }
  } catch (err: any) {
    uni.showToast({ title: err.message || '首绑验证失败', icon: 'none' });
  } finally {
    bindLoading.value = false;
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  padding: 40rpx 32rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: radial-gradient(circle at 50% 20%, #1e293b 0%, #0b0f19 80%);
}

.brand-section {
  text-align: center;
  margin-bottom: 48rpx;
}

.brand-logo {
  width: 120rpx;
  height: 120rpx;
  margin: 0 auto 20rpx;
  background: linear-gradient(135deg, #0ea5e9, #38bdf8);
  border-radius: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10rpx 30rpx rgba(14, 165, 233, 0.4);
}

.brand-icon {
  font-size: 56rpx;
}

.brand-title {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  color: #f8fafc;
  letter-spacing: 2rpx;
}

.brand-subtitle {
  display: block;
  font-size: 24rpx;
  color: #94a3b8;
  margin-top: 8rpx;
}

.offline-banner {
  background: rgba(234, 179, 8, 0.15);
  border: 1px solid rgba(234, 179, 8, 0.4);
  padding: 16rpx 24rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  margin-bottom: 24rpx;
}

.offline-icon {
  font-size: 32rpx;
  margin-right: 12rpx;
}

.offline-text {
  font-size: 22rpx;
  color: #fef08a;
  line-height: 1.4;
}

.login-card {
  background: rgba(15, 23, 42, 0.75);
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-radius: 28rpx;
  padding: 40rpx 32rpx;
  backdrop-filter: blur(20px);
  box-shadow: 0 20rpx 50rpx rgba(0, 0, 0, 0.5);
}

.wx-btn {
  background: #07c160;
  margin-bottom: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.wx-icon {
  font-size: 36rpx;
  margin-right: 12rpx;
}

.divider {
  display: flex;
  align-items: center;
  margin: 24rpx 0;
}

.divider .line {
  flex: 1;
  height: 1px;
  background: rgba(148, 163, 184, 0.2);
}

.divider-text {
  font-size: 22rpx;
  color: #64748b;
  padding: 0 16rpx;
}

.input-group {
  margin-bottom: 28rpx;
}

.input-label {
  display: block;
  font-size: 26rpx;
  color: #cbd5e1;
  margin-bottom: 12rpx;
}

.input-field {
  width: 100%;
  height: 88rpx;
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(71, 85, 105, 0.6);
  border-radius: 16rpx;
  padding: 0 24rpx;
  color: #f8fafc;
  font-size: 28rpx;
}

.input-field:focus {
  border-color: #38bdf8;
}

.placeholder-style {
  color: #64748b;
  font-size: 26rpx;
}

.btn-primary {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: linear-gradient(135deg, #0284c7, #0ea5e9);
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 600;
  border-radius: 16rpx;
  border: none;
  margin-top: 16rpx;
}

.btn-ghost {
  width: 100%;
  height: 76rpx;
  line-height: 76rpx;
  background: transparent;
  color: #38bdf8;
  font-size: 24rpx;
  border: 1px dashed rgba(56, 189, 248, 0.4);
  border-radius: 16rpx;
  margin-top: 24rpx;
}

.modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
  padding: 32rpx;
}

.modal-card {
  width: 100%;
  max-width: 640rpx;
  background: #0f172a;
  border: 1px solid rgba(56, 189, 248, 0.4);
  border-radius: 24rpx;
  padding: 36rpx;
}

.modal-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #f8fafc;
}

.modal-desc {
  display: block;
  font-size: 22rpx;
  color: #94a3b8;
  margin-top: 8rpx;
  margin-bottom: 24rpx;
  line-height: 1.4;
}

.modal-footer {
  display: flex;
  gap: 20rpx;
  margin-top: 32rpx;
}

.btn-secondary {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  background: #334155;
  color: #e2e8f0;
  font-size: 28rpx;
  border-radius: 14rpx;
}
</style>
