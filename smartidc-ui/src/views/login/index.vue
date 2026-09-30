<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <div class="logo">
          <el-icon :size="36" color="#409EFF"><Platform /></el-icon>
        </div>
        <h2 class="title">智维云 SmartIDC</h2>
        <p class="subtitle">数据中心动环监控与 AIOps 智能运维平台</p>
      </div>

      <el-form :model="loginForm" class="login-form">
        <el-form-item>
          <el-input
            v-model="loginForm.username"
            placeholder="账号 (如: admin 或 supervisor_zhang)"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item>
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码 (默认: 123456)"
            size="large"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            登 录 控 制 台
          </el-button>
        </el-form-item>
      </el-form>

      <div class="quick-hints">
        <span>测试账号: admin / supervisor_zhang</span>
        <span>默认密码: 123456</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)

const loginForm = ref({
  username: 'supervisor_zhang',
  password: 'root'
})

function handleLogin() {
  loading.value = true
  setTimeout(() => {
    loading.value = false
    localStorage.setItem('smartidc_token', 'mock_jwt_token_' + Date.now())
    localStorage.setItem('smartidc_tenant_id', '000000')
    ElMessage.success('欢迎登录智维云运维中台！')
    router.push('/dashboard')
  }, 400)
}
</script>

<style scoped>
.login-container {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(135deg, #0d1522 0%, #1a2a44 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  width: 420px;
  padding: 40px 36px;
  background: rgba(255, 255, 255, 0.96);
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.35);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.title {
  font-size: 24px;
  font-weight: bold;
  color: #1a2a44;
  margin-top: 12px;
}

.subtitle {
  font-size: 13px;
  color: #909399;
  margin-top: 6px;
}

.login-form {
  margin-top: 20px;
}

.login-btn {
  width: 100%;
  border-radius: 6px;
  font-weight: bold;
  letter-spacing: 2px;
}

.quick-hints {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #a8abb2;
  margin-top: 10px;
}
</style>
