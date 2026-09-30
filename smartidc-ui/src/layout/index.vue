<template>
  <div class="app-wrapper">
    <!-- 侧边栏 -->
    <div class="sidebar-container">
      <div class="logo-box">
        <el-icon class="logo-icon"><Platform /></el-icon>
        <span class="logo-text">智维云 SmartIDC</span>
      </div>
      <el-menu
        :default-active="route.path"
        background-color="#141e30"
        text-color="#c0ccda"
        active-text-color="#409EFF"
        router
        class="sidebar-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>监控指挥态势</span>
        </el-menu-item>

        <el-sub-menu index="/asset">
          <template #title>
            <el-icon><DataBoard /></el-icon>
            <span>空间资产全生命周期</span>
          </template>
          <el-menu-item index="/asset/rack">机架拓扑与U位</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/screen">
          <el-icon><Monitor /></el-icon>
          <span>数字孪生动环大屏</span>
        </el-menu-item>

        <el-menu-item index="/ticket">
          <el-icon><Tickets /></el-icon>
          <span>排障工单协同中心</span>
        </el-menu-item>

        <el-menu-item index="/alarm" disabled>
          <el-icon><Warning /></el-icon>
          <span>动环实时监测 (Phase 2)</span>
        </el-menu-item>

        <el-menu-item index="/aiops" disabled>
          <el-icon><Cpu /></el-icon>
          <span>AIOps 智能中枢 (Phase 4)</span>
        </el-menu-item>

        <el-menu-item index="/billing" disabled>
          <el-icon><Coin /></el-icon>
          <span>PUE 能耗结算 (Phase 5)</span>
        </el-menu-item>
      </el-menu>
    </div>

    <!-- 主体内容区 -->
    <div class="main-container">
      <!-- 顶栏导航 -->
      <div class="navbar">
        <div class="navbar-left">
          <span class="page-title">数据中心动环监控与 AIOps 协同中台</span>
        </div>
        <div class="navbar-right">
          <!-- 快捷进入孪生大屏 -->
          <el-button
            type="primary"
            plain
            size="default"
            class="screen-btn"
            @click="router.push('/screen')"
          >
            <el-icon class="el-icon--left"><Monitor /></el-icon>
            孪生大屏 (Phase 3)
          </el-button>
          <!-- 租户切换选择器 -->
          <div class="tenant-selector">
            <el-icon class="tenant-icon"><OfficeBuilding /></el-icon>
            <el-select
              v-model="tenantStore.currentTenantId"
              size="default"
              style="width: 260px"
              @change="handleTenantChange"
            >
              <el-option
                v-for="item in tenantStore.tenantList"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </div>

          <!-- 全局告警通知铃铛 -->
          <div class="alarm-bell-box" @click="showAlarmDrawer = true">
            <el-badge :value="alarmStore.alarmStats.activeTotal" :hidden="alarmStore.alarmStats.activeTotal === 0" :max="99" type="danger">
              <el-button circle :icon="Bell" :class="{ 'bell-active': alarmStore.alarmStats.activeTotal > 0 }" />
            </el-badge>
          </div>

          <!-- 用户名与头像 -->
          <el-dropdown class="avatar-dropdown">
            <span class="user-info">
              <el-avatar :size="32" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
              <span class="username">张主管 (值班主管)</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item>个人中心</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <!-- 页面主路由内容 -->
      <div class="app-main">
        <router-view />
      </div>

      <!-- 实时告警态势抽屉 -->
      <el-drawer
        v-model="showAlarmDrawer"
        title="🔔 实时动环越限告警态势中心"
        direction="rtl"
        size="480px"
        destroy-on-close
      >
        <div class="alarm-drawer-content">
          <div class="drawer-stats-bar">
            <el-tag type="danger" effect="dark">严重告警: {{ alarmStore.alarmStats.criticalCount }}</el-tag>
            <el-tag type="warning" effect="dark">一般预警: {{ alarmStore.alarmStats.warningCount }}</el-tag>
            <el-tag type="success" effect="plain">累计消除: {{ alarmStore.alarmStats.clearedCount }}</el-tag>
          </div>

          <div v-if="alarmStore.activeAlarms.length === 0" class="empty-alarms">
            <el-empty description="当前全机房动环指标处于安全稳定状态，无活动告警" />
          </div>

          <div v-else class="alarm-list">
            <el-card
              v-for="item in alarmStore.activeAlarms"
              :key="item.alarmId"
              shadow="hover"
              class="alarm-item-card"
              :class="{ 'alarm-critical': item.alarmLevel === 'CRITICAL' }"
            >
              <div class="alarm-item-header">
                <div class="header-tag-group">
                  <el-tag :type="item.alarmLevel === 'CRITICAL' ? 'danger' : 'warning'" effect="dark" size="small">
                    {{ item.alarmLevel === 'CRITICAL' ? '高危严重' : '动环警告' }}
                  </el-tag>
                  <span class="alarm-rack font-mono">{{ item.rackCode }}</span>
                  <span class="alarm-room">{{ item.roomName }}</span>
                </div>
                <span class="alarm-time">{{ item.triggerTime ? item.triggerTime.replace('T', ' ') : '刚刚' }}</span>
              </div>

              <div class="alarm-body">
                <div class="alarm-metric">
                  <span class="metric-label">告警类型:</span>
                  <el-tag size="small" type="info">{{ item.alarmType }}</el-tag>
                  <span class="metric-val">当前数值: <strong>{{ item.metricValue }}</strong></span>
                </div>
                <div class="alarm-summary" v-if="item.rcaSummary">
                  {{ item.rcaSummary }}
                </div>
              </div>

              <div class="alarm-actions">
                <el-button size="small" type="primary" link @click="router.push('/asset/rack')">
                  定位机柜 ➔
                </el-button>
                <el-button size="small" type="warning" link @click="alarmStore.handleMarkFalsePositive(item.alarmId)">
                  标记误报
                </el-button>
                <el-button size="small" type="danger" link @click="alarmStore.handleCloseAlarm(item.alarmId)">
                  人工关闭
                </el-button>
              </div>
            </el-card>
          </div>
        </div>
      </el-drawer>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTenantStore } from '@/stores/tenant'
import { useAlarmStore } from '@/stores/alarm'
import { Bell, OfficeBuilding, Platform, Odometer, DataBoard, Warning, Cpu, Coin, Monitor, Tickets } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const tenantStore = useTenantStore()
const alarmStore = useAlarmStore()
const showAlarmDrawer = ref(false)

onMounted(() => {
  // 启动 STOMP 全局告警订阅
  alarmStore.initWebSocket()
})

function handleTenantChange(val) {
  tenantStore.switchTenant(val)
  ElMessage.success(`已切换为租户环境: ${val}`)
  // 刷新当前页面触发携带最新租户 Header
  window.location.reload()
}

function handleLogout() {
  localStorage.removeItem('smartidc_token')
  router.push('/login')
}
</script>

<style scoped>
.app-wrapper {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}

.sidebar-container {
  width: 240px;
  background-color: #141e30;
  display: flex;
  flex-direction: column;
  box-shadow: 2px 0 6px rgba(0, 21, 41, 0.35);
  z-index: 10;
}

.logo-box {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  background-color: #0d1522;
  color: #fff;
  font-size: 17px;
  font-weight: bold;
}

.logo-icon {
  font-size: 24px;
  margin-right: 12px;
  color: #409EFF;
}

.sidebar-menu {
  border-right: none;
  flex: 1;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: #f0f2f5;
}

.navbar {
  height: 60px;
  background-color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  z-index: 9;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.tenant-selector {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tenant-icon {
  font-size: 20px;
  color: #409EFF;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.username {
  font-size: 14px;
  color: #606266;
}

.app-main {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.alarm-bell-box {
  cursor: pointer;
  display: flex;
  align-items: center;
}

.bell-active {
  color: #f56c6c;
  border-color: #f56c6c;
  animation: bellRing 1.5s infinite;
}

@keyframes bellRing {
  0%, 100% { transform: rotate(0deg); }
  10%, 30% { transform: rotate(-15deg); }
  20%, 40% { transform: rotate(15deg); }
  50% { transform: rotate(0deg); }
}

.alarm-drawer-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.drawer-stats-bar {
  display: flex;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.alarm-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.alarm-item-card {
  border-radius: 8px;
  transition: all 0.3s ease;
}

.alarm-item-card.alarm-critical {
  border-left: 4px solid #f56c6c;
  background-color: #fef0f0;
}

.alarm-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.header-tag-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.alarm-rack {
  font-weight: bold;
  color: #303133;
}

.alarm-room {
  font-size: 12px;
  color: #909399;
}

.alarm-time {
  font-size: 12px;
  color: #909399;
}

.alarm-body {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}

.alarm-metric {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.alarm-summary {
  font-size: 12px;
  color: #e6a23c;
  background-color: #fdf6ec;
  padding: 4px 8px;
  border-radius: 4px;
  margin-top: 6px;
}

.alarm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px dashed #ebeef5;
  padding-top: 6px;
}
</style>
