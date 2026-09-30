<template>
  <div class="cabinet-view-root" @wheel="handleRootWheel">
    <!-- 顶部状态概览与操作栏 -->
    <div class="cabinet-header-card">
      <div class="header-left">
        <div class="rack-title">
          <span class="rack-tag">{{ rack?.rackCode || '未知机柜' }}</span>
          <span class="room-tag">{{ rack?.roomName || '未知机房' }}</span>
          <el-tag v-if="rack?.status === 1" type="success" size="small" effect="dark">托管运行中</el-tag>
          <el-tag v-else-if="rack?.status === 0" type="info" size="small">空闲可用</el-tag>
          <el-tag v-else type="danger" size="small">维保锁定</el-tag>
          <!-- 实时告警状态标签 -->
          <el-tag
            v-if="isRackAlarm"
            :type="activeRackAlarm?.alarmLevel === 'CRITICAL' ? 'danger' : 'warning'"
            effect="dark"
            size="small"
            class="pulse-alarm-tag"
          >
            {{ activeRackAlarm?.alarmLevel === 'CRITICAL' ? '🚨 严重越限告警' : '⚠️ 动环超温预警' }}
          </el-tag>
        </div>
        <div class="rack-metrics">
          <div class="metric-item">
            <span class="metric-label">U位负载:</span>
            <span class="metric-value font-mono">{{ usedUCount }} / 42 U</span>
            <el-progress
              :percentage="usageRate"
              :status="usageProgressStatus"
              :stroke-width="8"
              class="metric-progress"
            />
          </div>
          <div class="metric-item">
            <span class="metric-label">额定容量:</span>
            <span class="metric-value font-mono">{{ currentPowerTotal.toFixed(2) }} / {{ Number(rack?.powerRating || 5.0).toFixed(2) }} kW</span>
          </div>
        </div>

        <!-- 秒级动环遥测推屏面板 -->
        <div class="telemetry-live-panel">
          <div class="telemetry-item">
            <span class="tel-label">🌡️ 实时温度:</span>
            <span class="tel-val font-mono" :class="getTempClass(liveTelemetry.temperature)">
              {{ liveTelemetry.temperature !== null ? `${liveTelemetry.temperature} ℃` : '-- ℃' }}
            </span>
          </div>
          <div class="telemetry-item">
            <span class="tel-label">💧 相对湿度:</span>
            <span class="tel-val font-mono">
              {{ liveTelemetry.humidity !== null ? `${liveTelemetry.humidity} %RH` : '-- %' }}
            </span>
          </div>
          <div class="telemetry-item">
            <span class="tel-label">⚡ 输入电压:</span>
            <span class="tel-val font-mono">
              {{ liveTelemetry.voltage !== null ? `${liveTelemetry.voltage} V` : '-- V' }}
            </span>
          </div>
          <div class="telemetry-item">
            <span class="tel-label">💡 实时功耗:</span>
            <span class="tel-val font-mono">
              {{ liveTelemetry.powerKw !== null ? `${liveTelemetry.powerKw} kW` : '-- kW' }}
            </span>
          </div>
          <div class="telemetry-item stream-status">
            <span class="live-dot" :class="{ 'live-dot-active': isStreamActive }"></span>
            <span class="tel-time">{{ liveTelemetry.timestamp ? `${liveTelemetry.timestamp} 实时更新` : 'STOMP 监听中' }}</span>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button type="primary" :icon="Plus" size="small" @click="handleOpenMountDialog()">
          上架新设备
        </el-button>
        <el-button
          type="danger"
          plain
          :icon="Delete"
          size="small"
          :disabled="usedUCount === 0"
          :loading="clearingAll"
          @click="handleClearAllDevices"
        >
          一键清空设备
        </el-button>
        <el-button :icon="Refresh" size="small" :loading="loading" @click="fetchSlots">
          刷新
        </el-button>
      </div>
    </div>

    <!-- 图例说明条 -->
    <div class="legend-bar">
      <div class="legend-item">
        <span class="legend-color legend-server"></span>
        <span>IT 服务器 (IT_SERVER)</span>
      </div>
      <div class="legend-item">
        <span class="legend-color legend-switch"></span>
        <span>网络交换机 (IT_SWITCH)</span>
      </div>
      <div class="legend-item">
        <span class="legend-color legend-sensor"></span>
        <span>动环传感器 (SENSOR_*)</span>
      </div>
      <div class="legend-item">
        <span class="legend-color legend-empty"></span>
        <span>空闲槽位 (点击快捷上架)</span>
      </div>
    </div>

    <!-- 42U 可视化机柜主体结构 -->
    <div class="cabinet-stage" ref="stageRef" v-loading="loading">
      <div
        class="cabinet-chassis"
        :class="{
          'chassis-alarm': isRackAlarm,
          'chassis-critical': activeRackAlarm?.alarmLevel === 'CRITICAL'
        }"
      >
        <!-- 告警紧急顶标条 -->
        <div v-if="isRackAlarm" class="chassis-alarm-banner">
          <span class="alarm-banner-icon">🚨</span>
          <span class="alarm-banner-text">
            【{{ activeRackAlarm?.alarmLevel === 'CRITICAL' ? '严重故障越限' : '超温越限预警' }}】
            {{ activeRackAlarm?.rcaSummary || `${activeRackAlarm?.alarmType} (${activeRackAlarm?.metricValue})` }}
          </span>
          <span class="alarm-banner-time">{{ activeRackAlarm?.triggerTime ? activeRackAlarm.triggerTime.replace('T', ' ') : '' }}</span>
        </div>

        <!-- 机柜顶部排风与顶标 -->
        <div class="chassis-header">
          <div class="vent-grill">
            <span></span><span></span><span></span><span></span><span></span><span></span>
          </div>
          <div class="chassis-title">SMART-IDC // 19" STANDARD 42U RACK [{{ rack?.rackCode }}]</div>
          <div class="vent-grill">
            <span></span><span></span><span></span><span></span><span></span><span></span>
          </div>
        </div>

        <!-- 机柜中心插槽区域 -->
        <div class="chassis-body">
          <!-- 左侧导轨与 42U~1U 标尺 -->
          <div class="rack-rail left-rail">
            <div
              v-for="u in 42"
              :key="'l-' + u"
              class="rail-unit"
              :class="{ 'rail-active': isSlotOccupied(43 - u) }"
            >
              <span class="u-number">{{ 43 - u }}U</span>
              <div class="screw-holes">
                <span class="screw"></span>
                <span class="screw"></span>
              </div>
            </div>
          </div>

          <!-- 中间插槽设备装配区 (42U~1U 连续视图) -->
          <div class="equipment-bay">
            <template v-for="block in displayBlocks" :key="block.key">
              <!-- 空闲插槽 (1U 盲板样式) -->
              <div
                v-if="block.type === 'empty'"
                class="slot-block empty-slot"
                :style="{ height: `${block.uHeight * slotUnitHeight}px` }"
                @click="handleOpenMountDialog(block.slotU)"
              >
                <div class="empty-panel-lines">
                  <span class="vent-line"></span>
                  <span class="vent-line"></span>
                </div>
                <div class="empty-hint">
                  <el-icon><Plus /></el-icon>
                  <span>{{ block.slotU }}U 空闲 - 点击上架</span>
                </div>
              </div>

              <!-- 占用设备块 (跨 U 位聚合) -->
              <div
                v-else
                class="slot-block device-block"
                :class="getDeviceThemeClass(block.device.deviceType)"
                :style="{ height: `${block.uHeight * slotUnitHeight}px` }"
                @click="handleViewDeviceDetail(block.device)"
              >
                <!-- 左侧固定角件与拉手耳 -->
                <div class="rack-ear ear-left">
                  <span class="ear-screw"></span>
                  <span class="ear-handle"></span>
                </div>

                <!-- 仿真设备前面板面貌 -->
                <div class="faceplate-content">
                  <!-- 指示灯与状态区 -->
                  <div class="device-status-leds">
                    <span class="led-dot power-led" :class="{ 'led-fault': block.device.status === 3 }" title="电源指示灯"></span>
                    <span class="led-dot net-led" title="网络通信活动指示灯"></span>
                  </div>

                  <!-- 设备名称与类型信息 -->
                  <div class="device-main-info">
                    <div class="device-title-row">
                      <el-icon class="device-icon">
                        <component :is="getDeviceIcon(block.device.deviceType)" />
                      </el-icon>
                      <span class="device-name font-mono" :title="block.device.deviceName">
                        {{ block.device.deviceName }}
                      </span>
                      <el-tag size="small" :type="getDeviceTagType(block.device.deviceType)" class="type-badge">
                        {{ block.device.deviceType }}
                      </el-tag>
                    </div>
                    <div class="device-sub-row" v-if="block.uHeight >= 2">
                      <span class="device-key" v-if="block.device.iotDeviceKey">
                        KEY: {{ block.device.iotDeviceKey }}
                      </span>
                      <span class="device-power" v-if="block.device.ratedPower">
                        ⚡ {{ Number(block.device.ratedPower).toFixed(2) }} kW
                      </span>
                    </div>
                  </div>

                  <!-- 工业纹理与装饰（交换机网口矩阵 / 服务器硬盘仓模拟） -->
                  <div class="device-decorative-panel" v-if="block.uHeight >= 2">
                    <!-- 交换机网口阵列 -->
                    <div v-if="block.device.deviceType === 'IT_SWITCH'" class="switch-port-matrix">
                      <span v-for="p in 12" :key="p" class="rj45-port"></span>
                    </div>
                    <!-- 服务器硬盘仓格栅 -->
                    <div v-else-if="block.device.deviceType === 'IT_SERVER'" class="server-drive-bays">
                      <span v-for="d in (block.uHeight >= 4 ? 6 : 4)" :key="d" class="drive-bay"></span>
                    </div>
                  </div>

                  <!-- 右侧 U 位区间标识与快捷操作 -->
                  <div class="device-end-badge">
                    <span class="u-span-badge">
                      {{ block.startU }}-{{ block.startU + block.uHeight - 1 }}U ({{ block.uHeight }}U)
                    </span>
                    <el-button
                      link
                      type="danger"
                      size="small"
                      class="quick-unmount-btn"
                      @click.stop="confirmUnmount(block.device)"
                      title="下架该设备"
                    >
                      下架
                    </el-button>
                  </div>
                </div>

                <!-- 右侧固定角件与拉手耳 -->
                <div class="rack-ear ear-right">
                  <span class="ear-handle"></span>
                  <span class="ear-screw"></span>
                </div>
              </div>
            </template>
          </div>

          <!-- 右侧导轨与螺栓孔 -->
          <div class="rack-rail right-rail">
            <div
              v-for="u in 42"
              :key="'r-' + u"
              class="rail-unit"
              :class="{ 'rail-active': isSlotOccupied(43 - u) }"
            >
              <div class="screw-holes">
                <span class="screw"></span>
                <span class="screw"></span>
              </div>
              <span class="u-number">{{ 43 - u }}U</span>
            </div>
          </div>
        </div>

        <!-- 机柜底部支撑地脚与底标 -->
        <div class="chassis-footer">
          <div class="rack-foot foot-left"></div>
          <div class="footer-center">GROUNDED // STATIC PROTECTED</div>
          <div class="rack-foot foot-right"></div>
        </div>
      </div>
    </div>

    <!-- 弹窗 1：设备上架操作引导弹窗 -->
    <el-dialog
      v-model="mountDialogVisible"
      title="机架设备上架安装"
      width="560px"
      destroy-on-close
      append-to-body
    >
      <el-form
        ref="mountFormRef"
        :model="mountForm"
        :rules="mountFormRules"
        label-width="110px"
        status-icon
      >
        <el-form-item label="目标机柜">
          <el-input :model-value="`${rack?.rackCode} (${rack?.roomName}) - 额定 ${rack?.powerRating} kVA`" disabled />
        </el-form-item>

        <el-form-item label="设备类型" prop="deviceType">
          <el-select v-model="mountForm.deviceType" placeholder="请选择设备类型" style="width: 100%">
            <el-option label="IT 计算服务器 (IT_SERVER)" value="IT_SERVER" />
            <el-option label="核心/接入网络交换机 (IT_SWITCH)" value="IT_SWITCH" />
            <el-option label="温湿度动环传感器 (SENSOR_TEMP)" value="SENSOR_TEMP" />
            <el-option label="智能 PDU / 动环电表 (SENSOR_UPS)" value="SENSOR_UPS" />
          </el-select>
        </el-form-item>

        <el-form-item label="设备名称" prop="deviceName">
          <el-input
            v-model="mountForm.deviceName"
            placeholder="如: GPU计算服务器 Inspur-NF5468 / 华为交换机"
            maxlength="64"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="物联网标识">
          <el-input
            v-model="mountForm.iotDeviceKey"
            placeholder="DeviceKey，如: SRV-A03-002 (选填)"
            maxlength="64"
          />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="起始 U 位" prop="startU">
              <el-input-number
                v-model="mountForm.startU"
                :min="1"
                :max="42"
                style="width: 100%"
                controls-position="right"
                @change="onStartUChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束 U 位" prop="endU">
              <el-input-number
                v-model="mountForm.endU"
                :min="mountForm.startU"
                :max="42"
                style="width: 100%"
                controls-position="right"
                @change="onEndUChange"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="占用空间">
          <div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap;">
            <el-tag type="info" size="large" effect="plain" class="font-mono">
              共 <strong>{{ mountForm.uHeight }}</strong> 个 U 位 (区间: {{ mountForm.startU }}U ~ {{ mountForm.endU }}U)
            </el-tag>
            <el-radio-group v-model="quickHeightSelected" size="small" @change="onQuickHeightChange">
              <el-radio-button :value="1">1U</el-radio-button>
              <el-radio-button :value="2">2U</el-radio-button>
              <el-radio-button :value="4">4U</el-radio-button>
            </el-radio-group>
          </div>
          <div v-if="mountExpansionHint" style="margin-top: 6px;">
            <el-tag
              size="small"
              :type="collisionCheckResult.isConflict ? 'danger' : (mountExpansionHint.includes('向下') ? 'warning' : 'success')"
              effect="plain"
            >
              {{ mountExpansionHint }}
            </el-tag>
          </div>
        </el-form-item>

        <el-form-item label="额定功耗" prop="ratedPower">
          <el-input-number
            v-model="mountForm.ratedPower"
            :min="0.01"
            :max="20"
            :precision="2"
            :step="0.1"
            style="width: 100%"
            controls-position="right"
          >
            <template #suffix>kW</template>
          </el-input-number>
        </el-form-item>

        <!-- 空间区间碰撞实时预检提示 -->
        <div class="collision-preview-box">
          <el-alert
            v-if="collisionCheckResult.isOutOfBounds"
            title="越界错误：拟上架区间超出机柜 42U 物理上限！"
            type="error"
            :closable="false"
            show-icon
          />
          <el-alert
            v-else-if="collisionCheckResult.isConflict"
            :title="`🚫 空间冲突：拟占区间 [${mountForm.startU}U-${mountForm.endU}U] 与已有在架设备重叠，无法上架！`"
            :description="`冲突设备: 【${collisionCheckResult.conflictDevice?.deviceName}】(占用区间 ${collisionCheckResult.conflictDevice?.startU}U-${collisionCheckResult.conflictDevice?.startU + collisionCheckResult.conflictDevice?.uHeight - 1}U)`"
            type="error"
            :closable="false"
            show-icon
          />
          <el-alert
            v-else
            :title="`区间检测通过：拟上架 [${mountForm.startU}U-${mountForm.endU}U] (共 ${mountForm.uHeight}U) 槽位完全可用`"
            type="success"
            :closable="false"
            show-icon
          />
        </div>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="mountDialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="collisionCheckResult.isOutOfBounds || collisionCheckResult.isConflict"
            @click="submitMount"
          >
            {{ collisionCheckResult.isConflict ? '空间冲突无法上架' : '确认上架' }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 弹窗 2：设备资产详情与下架确认弹窗 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="设备资产详情与下架"
      width="520px"
      append-to-body
    >
      <div v-if="selectedDevice" class="device-detail-content">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="设备名称">
            <strong>{{ selectedDevice.deviceName }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="设备类型">
            <el-tag :type="getDeviceTagType(selectedDevice.deviceType)">
              {{ selectedDevice.deviceType }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="所在 U 位区间">
            <span class="font-mono text-primary">
              {{ selectedDevice.startU }}U ~ {{ selectedDevice.startU + selectedDevice.uHeight - 1 }}U (占用 {{ selectedDevice.uHeight }}U)
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="额定功耗">
            {{ Number(selectedDevice.ratedPower || 0).toFixed(2) }} kW
          </el-descriptions-item>
          <el-descriptions-item label="物联网 DeviceKey">
            {{ selectedDevice.iotDeviceKey || '未绑定' }}
          </el-descriptions-item>
          <el-descriptions-item label="设备运行状态">
            <el-tag v-if="selectedDevice.status === 1" type="success">正常在线</el-tag>
            <el-tag v-else-if="selectedDevice.status === 2" type="warning">告警</el-tag>
            <el-tag v-else-if="selectedDevice.status === 3" type="danger">故障</el-tag>
            <el-tag v-else type="info">离线</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div class="simulation-zone">
          <div class="sim-title">🔄 设备替换与容量规划沙盘 (Capacity What-If Simulation)</div>
          <div class="sim-desc">
            运维人员可模拟替换该设备，系统将实时推演【U位画像切片】、【已用U位】与【供电负荷】变动，若指标合适可一键执行原子替换：
          </div>
          <el-button
            type="primary"
            :icon="Cpu"
            @click="openReplaceSimulationModal(selectedDevice)"
          >
            🚀 开启设备替换沙盘推演
          </el-button>
        </div>

        <div class="danger-zone">
          <div class="danger-title">资产下架区</div>
          <div class="danger-desc">
            下架该设备后，系统将立即释放其占用的 <strong>{{ selectedDevice.uHeight }}U</strong> 空间，机架总已用 U 位将自动递减。
          </div>
          <el-button type="danger" :loading="unmounting" @click="confirmUnmount(selectedDevice)">
            确认下架该设备
          </el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 弹窗 3：设备替换与容量规划沙盘推演弹窗 -->
    <el-dialog
      v-model="replaceSimVisible"
      title="机架设备替换与容量规划推演沙盘 (What-If Simulation)"
      width="780px"
      append-to-body
      destroy-on-close
    >
      <div v-if="replaceTarget" class="replace-sim-dialog">
        <!-- 上方：旧设备 vs 拟上架新设备 对比卡片 -->
        <el-row :gutter="20" class="sim-compare-row">
          <!-- 当前在架旧设备 (Before) -->
          <el-col :span="11">
            <div class="sim-card sim-card-old">
              <div class="sim-card-header">
                <el-tag type="danger" effect="dark" size="small">旧设备现状 (Before)</el-tag>
                <span class="sim-device-title">{{ replaceTarget.deviceName }}</span>
              </div>
              <div class="sim-card-body">
                <div class="sim-prop-row">
                  <span class="prop-label">所在区间:</span>
                  <span class="prop-value font-mono">{{ replaceTarget.startU }}U ~ {{ replaceTarget.startU + replaceTarget.uHeight - 1 }}U</span>
                </div>
                <div class="sim-prop-row">
                  <span class="prop-label">占用高度:</span>
                  <span class="prop-value font-mono">{{ replaceTarget.uHeight }} U</span>
                </div>
                <div class="sim-prop-row">
                  <span class="prop-label">额定功耗:</span>
                  <span class="prop-value font-mono">{{ Number(replaceTarget.ratedPower || 0).toFixed(2) }} kW</span>
                </div>
                <div class="sim-prop-row">
                  <span class="prop-label">设备类型:</span>
                  <el-tag size="small" :type="getDeviceTagType(replaceTarget.deviceType)">{{ replaceTarget.deviceType }}</el-tag>
                </div>
              </div>
            </div>
          </el-col>

          <!-- 中间转换箭头 -->
          <el-col :span="2" class="sim-arrow-col">
            <div class="sim-arrow">➔</div>
            <div class="sim-arrow-text">替换推演</div>
          </el-col>

          <!-- 拟替换新设备规格 (After / What-If) -->
          <el-col :span="11">
            <div class="sim-card sim-card-new">
              <div class="sim-card-header">
                <el-tag type="success" effect="dark" size="small">拟替换新设备 (After)</el-tag>
                <span class="sim-device-title">参数设定</span>
              </div>
              <div class="sim-card-body">
                <el-form :model="replaceForm" label-width="85px" size="small">
                  <el-form-item label="设备名称">
                    <el-input v-model="replaceForm.deviceName" placeholder="如: 新一代高密AI服务器" />
                  </el-form-item>
                  <el-form-item label="设备类型">
                    <el-select v-model="replaceForm.deviceType" style="width: 100%">
                      <el-option label="IT 计算服务器" value="IT_SERVER" />
                      <el-option label="网络交换机" value="IT_SWITCH" />
                      <el-option label="温湿度传感器" value="SENSOR_TEMP" />
                      <el-option label="智能 PDU / 电表" value="SENSOR_UPS" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="占用高度">
                    <el-radio-group v-model="replaceForm.uHeight" @change="onSimHeightChange">
                      <el-radio-button :value="1">1U</el-radio-button>
                      <el-radio-button :value="2">2U</el-radio-button>
                      <el-radio-button :value="3">3U</el-radio-button>
                      <el-radio-button :value="4">4U</el-radio-button>
                    </el-radio-group>
                  </el-form-item>
                  <el-form-item label="U位区间">
                    <div class="sim-range-input-row">
                      <el-input-number
                        v-model="replaceForm.startU"
                        :min="1"
                        :max="42"
                        controls-position="right"
                        class="sim-u-input"
                        @change="onSimRangeManualChange('start')"
                      />
                      <span class="range-sep">~</span>
                      <el-input-number
                        v-model="replaceForm.endU"
                        :min="replaceForm.startU"
                        :max="42"
                        controls-position="right"
                        class="sim-u-input"
                        @change="onSimRangeManualChange('end')"
                      />
                      <el-tag
                        size="small"
                        :type="simCollisionConflict ? 'danger' : 'success'"
                        effect="dark"
                        class="font-mono"
                      >
                        {{ replaceForm.uHeight }}U
                      </el-tag>
                    </div>
                    <div v-if="expansionDirectionText" class="expansion-hint-badge">
                      <el-tag
                        size="small"
                        :type="expansionDirectionTagType"
                        effect="plain"
                      >
                        {{ expansionDirectionText }}
                      </el-tag>
                    </div>
                  </el-form-item>
                  <el-form-item label="额定功耗">
                    <el-input-number
                      v-model="replaceForm.ratedPower"
                      :min="0.05"
                      :max="20"
                      :precision="2"
                      :step="0.1"
                      style="width: 100%"
                    >
                      <template #suffix>kW</template>
                    </el-input-number>
                  </el-form-item>
                </el-form>
              </div>
            </div>
          </el-col>
        </el-row>

        <!-- 下方：推演指标实时变化评估卡片 -->
        <div class="sim-impact-section">
          <div class="impact-section-title">📊 机柜全景推演影响评估 (What-If Impact Analysis)</div>

          <el-row :gutter="16">
            <!-- U位空间影响 -->
            <el-col :span="12">
              <div class="impact-card">
                <div class="impact-card-title">📐 机柜 U 位空间变化</div>
                <div class="impact-metric-row">
                  <span class="metric-before font-mono">{{ usedUCount }}U</span>
                  <span class="metric-arrow">➔</span>
                  <span class="metric-after font-mono">{{ simNewUsedU }}U / 42U</span>
                  <el-tag v-if="simUDelta < 0" type="success" size="small">净释放 {{ -simUDelta }}U 空间</el-tag>
                  <el-tag v-else-if="simUDelta === 0" type="info" size="small">空间等额平替 (0U)</el-tag>
                  <el-tag v-else type="warning" size="small">额外占用 +{{ simUDelta }}U</el-tag>
                </div>
                <el-progress
                  :percentage="simNewUsageRate"
                  :status="simNewUsageRate >= 80 ? 'exception' : (simNewUsageRate >= 50 ? 'warning' : 'success')"
                  :stroke-width="8"
                  class="mt-10"
                />
                <div class="impact-hint font-mono">
                  空间利用率: {{ usageRate }}% ➔ {{ simNewUsageRate }}%
                  (占用区间: {{ replaceForm.startU }}U ~ {{ replaceForm.endU }}U)
                </div>
              </div>
            </el-col>

            <!-- 电力负载影响 -->
            <el-col :span="12">
              <div class="impact-card">
                <div class="impact-card-title">⚡ 机柜电力负荷评估</div>
                <div class="impact-metric-row">
                  <span class="metric-before font-mono">{{ currentPowerTotal.toFixed(2) }}kW</span>
                  <span class="metric-arrow">➔</span>
                  <span class="metric-after font-mono" :class="{ 'text-danger': simIsPowerOverloaded }">
                    {{ simNewPowerTotal.toFixed(2) }} kW
                  </span>
                  <span class="metric-cap font-mono">/ 额定 {{ Number(rack?.powerRating || 6).toFixed(2) }} kVA</span>
                </div>
                <el-progress
                  :percentage="Math.min(100, simPowerRate)"
                  :status="simIsPowerOverloaded ? 'exception' : (simPowerRate >= 80 ? 'warning' : 'success')"
                  :stroke-width="8"
                  class="mt-10"
                />
                <div class="impact-hint">
                  负载率: {{ simPowerRate }}%
                  <span v-if="simPowerDelta >= 0"> (功率净增 +{{ simPowerDelta.toFixed(2) }} kW)</span>
                  <span v-else> (功率净降 {{ simPowerDelta.toFixed(2) }} kW)</span>
                </div>
              </div>
            </el-col>
          </el-row>

          <!-- 安全诊断提示条 -->
          <div class="sim-alert-box mt-16">
            <el-alert
              v-if="simIsPowerOverloaded"
              title="🚫 电力负载超限警告：替换后机架总负荷超出额定供电容量！"
              :description="`预计总负荷 ${simNewPowerTotal.toFixed(2)} kW > 机柜额定容量 ${rack?.powerRating} kVA。系统已安全阻断执行，防止引发 PDU 跳闸！`"
              type="error"
              :closable="false"
              show-icon
            />
            <el-alert
              v-else-if="simCollisionConflict"
              title="🚫 空间扩充冲突：扩充的 U 区间已有设备，无法进行替换！"
              :description="simCollisionConflictDesc"
              type="error"
              :closable="false"
              show-icon
            />
            <el-alert
              v-else
              title="✅ 沙盘推演检测合格：空间与电力各项指标均在安全阈值内！"
              :description="`执行替换后：机柜已用 U 位变更为 ${simNewUsedU}U，总负荷处于安全负载率 (${simPowerRate}%)。若各项指标合适，可直接确认执行替换。`"
              type="success"
              :closable="false"
              show-icon
            />
          </div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="replaceSimVisible = false">取消推演</el-button>
          <el-button
            type="primary"
            :loading="replacing"
            :disabled="simIsPowerOverloaded || simCollisionConflict"
            @click="submitReplace"
          >
            确认执行设备替换 (原子下架旧设备+上架新设备)
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { Plus, Refresh, Monitor, Connection, Odometer, Cpu, Delete } from '@element-plus/icons-vue'
import { getRackSlots, mountDevice, unmountDevice, replaceDevice, clearRackDevices } from '@/api/device'
import { getLatestTelemetry } from '@/api/telemetry'
import { useAlarmStore } from '@/stores/alarm'
import stompManager from '@/utils/websocket'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  rack: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['refresh'])

const alarmStore = useAlarmStore()

// 机柜告警联动状态
const isRackAlarm = computed(() => {
  return alarmStore.isRackInAlarm(props.rack?.rackCode)
})

const activeRackAlarm = computed(() => {
  return alarmStore.getRackAlarm(props.rack?.rackCode)
})

// 实时遥测流数据
const liveTelemetry = ref({
  temperature: null,
  humidity: null,
  voltage: null,
  currentAmp: null,
  powerKw: null,
  timestamp: null
})

const isStreamActive = ref(false)
let telemetrySub = null

function getTempClass(temp) {
  if (temp === null || temp === undefined) return ''
  const t = Number(temp)
  if (t >= 35) return 'temp-critical'
  if (t >= 30) return 'temp-warning'
  return 'temp-normal'
}

// 订阅单机柜实时遥测数据
async function subscribeRackTelemetry(rackCode) {
  if (telemetrySub) {
    telemetrySub.unsubscribe()
    telemetrySub = null
  }
  if (!rackCode) return

  // 1. 先异步拉取最新快照 (Redis 缓存优先)
  try {
    const latest = await getLatestTelemetry(rackCode)
    if (latest && latest.temperature !== undefined) {
      liveTelemetry.value = {
        temperature: Number(latest.temperature).toFixed(1),
        humidity: Number(latest.humidity || 0).toFixed(1),
        voltage: Number(latest.voltage || 0).toFixed(1),
        currentAmp: Number(latest.currentAmp || 0).toFixed(1),
        powerKw: Number(latest.powerKw || 0).toFixed(2),
        timestamp: latest.sampleTimestamp ? new Date(latest.sampleTimestamp).toLocaleTimeString() : '快照载入'
      }
      isStreamActive.value = true
    }
  } catch (e) {
    // 忽略快照缺失
  }

  // 2. 动态订阅 STOMP 主题 /topic/rack-telemetry/{rackCode}
  const destination = `/topic/rack-telemetry/${rackCode}`
  telemetrySub = stompManager.subscribe(destination, (payload) => {
    if (!payload) return
    liveTelemetry.value = {
      temperature: payload.temperature !== undefined ? Number(payload.temperature).toFixed(1) : null,
      humidity: payload.humidity !== undefined ? Number(payload.humidity).toFixed(1) : null,
      voltage: payload.voltage !== undefined ? Number(payload.voltage).toFixed(1) : null,
      currentAmp: payload.currentAmp !== undefined ? Number(payload.currentAmp).toFixed(1) : null,
      powerKw: payload.powerKw !== undefined ? Number(payload.powerKw).toFixed(2) : null,
      timestamp: payload.timestamp ? new Date(payload.timestamp).toLocaleTimeString() : new Date().toLocaleTimeString()
    }
    isStreamActive.value = true
  })
}

watch(
  () => props.rack?.rackCode,
  (newCode) => {
    if (newCode) {
      subscribeRackTelemetry(newCode)
    }
  },
  { immediate: true }
)

onUnmounted(() => {
  if (telemetrySub) {
    telemetrySub.unsubscribe()
    telemetrySub = null
  }
})

// 渲染槽位高度常数 (每 1U 的像素高度)
const slotUnitHeight = 20

const loading = ref(false)
const rawSlots = ref([])
const stageRef = ref(null)

// 当鼠标在顶部状态卡片或图例栏等非滚动区滚动时，顺畅转接滚动至机柜插槽区域
function handleRootWheel(e) {
  if (stageRef.value && !stageRef.value.contains(e.target)) {
    stageRef.value.scrollTop += e.deltaY
  }
}

// 弹窗状态
const mountDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const submitting = ref(false)
const unmounting = ref(false)
const clearingAll = ref(false)
const selectedDevice = ref(null)

// 设备替换沙盘推演状态
const replaceSimVisible = ref(false)
const replacing = ref(false)
const replaceTarget = ref(null)
const replaceForm = ref({
  deviceName: '',
  deviceType: 'IT_SERVER',
  iotDeviceKey: '',
  startU: 20,
  endU: 23,
  uHeight: 4,
  ratedPower: 2.50
})

// 上架表单对象
const mountFormRef = ref(null)
const quickHeightSelected = ref(1)
const mountForm = ref({
  rackId: null,
  deviceName: '',
  deviceType: 'IT_SERVER',
  iotDeviceKey: '',
  startU: 1,
  endU: 1,
  uHeight: 1,
  ratedPower: 0.50
})

const mountFormRules = {
  deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  deviceType: [{ required: true, message: '请选择设备类型', trigger: 'change' }],
  startU: [{ required: true, message: '请输入起始U位', trigger: 'blur' }],
  endU: [{ required: true, message: '请输入结束U位', trigger: 'blur' }],
  ratedPower: [{ required: true, message: '请输入额定功耗', trigger: 'blur' }]
}

// 获取插槽画像数据
async function fetchSlots() {
  if (!props.rack?.rackId) return
  loading.value = true
  try {
    const data = await getRackSlots(props.rack.rackId)
    rawSlots.value = data || []
  } catch (err) {
    ElMessage.error('获取机柜插槽数据失败')
  } finally {
    loading.value = false
  }
}

// 将 42U 从顶向下切片进行跨 U 位设备块聚合运算
const displayBlocks = computed(() => {
  if (!rawSlots.value || rawSlots.value.length === 0) {
    // 降级构建 42~1 空闲占位
    return Array.from({ length: 42 }, (_, idx) => ({
      key: `fallback-${42 - idx}`,
      type: 'empty',
      slotU: 42 - idx,
      uHeight: 1
    }))
  }

  const blocks = []
  let i = 0
  const len = rawSlots.value.length

  while (i < len) {
    const slot = rawSlots.value[i]
    if (!slot.isOccupied) {
      blocks.push({
        key: `empty-${slot.slotU}`,
        type: 'empty',
        slotU: slot.slotU,
        uHeight: 1
      })
      i++
    } else {
      const uHeight = Math.max(1, slot.uHeight || 1)
      blocks.push({
        key: `device-${slot.deviceId}-${slot.startU}`,
        type: 'device',
        slotU: slot.slotU,
        startU: slot.startU,
        uHeight: uHeight,
        device: slot
      })
      // 跳过该设备占用的其余 U 位槽（由上往下）
      i += uHeight
    }
  }

  return blocks
})

// 统计机柜总占用 U 位数
const usedUCount = computed(() => {
  return rawSlots.value.filter(s => s.isOccupied).length
})

// 空间占用百分比
const usageRate = computed(() => {
  return Math.round((usedUCount.value / 42) * 100)
})

// 当前机柜已占用额定功率汇总
const currentPowerTotal = computed(() => {
  const seenDeviceIds = new Set()
  let sum = 0
  for (const s of rawSlots.value) {
    if (s.isOccupied && s.deviceId && !seenDeviceIds.has(s.deviceId)) {
      seenDeviceIds.add(s.deviceId)
      sum += Number(s.ratedPower || 0)
    }
  }
  return sum
})

const usageProgressStatus = computed(() => {
  if (usageRate.value >= 80) return 'exception'
  if (usageRate.value >= 50) return 'warning'
  return 'success'
})

function isSlotOccupied(slotU) {
  const target = rawSlots.value.find(s => s.slotU === slotU)
  return target ? target.isOccupied : false
}

// 校验某一段区间 [fromU, toU] 是否完全空闲 (excludeDeviceId 可选)
function isRangeFree(fromU, toU, excludeDeviceId = null) {
  if (fromU < 1 || toU > 42 || fromU > toU) return false
  for (const s of rawSlots.value) {
    if (s.isOccupied && s.deviceId && (!excludeDeviceId || s.deviceId !== excludeDeviceId)) {
      const devStart = s.startU
      const devEnd = devStart + s.uHeight - 1
      if (Math.max(fromU, devStart) <= Math.min(toU, devEnd)) {
        return false
      }
    }
  }
  return true
}

// 获取某区间内发生重叠的第一台设备 (excludeDeviceId 可选)
function getConflictDeviceInRange(fromU, toU, excludeDeviceId = null) {
  if (fromU < 1 || toU > 42 || fromU > toU) return null
  for (const s of rawSlots.value) {
    if (s.isOccupied && s.deviceId && (!excludeDeviceId || s.deviceId !== excludeDeviceId)) {
      const devStart = s.startU
      const devEnd = devStart + s.uHeight - 1
      if (Math.max(fromU, devStart) <= Math.min(toU, devEnd)) {
        return {
          deviceId: s.deviceId,
          deviceName: s.deviceName,
          startU: devStart,
          endU: devEnd,
          rangeText: `${devStart}U~${devEnd}U`
        }
      }
    }
  }
  return null
}

const mountExpansionHint = ref('')
const mountAnchorSlot = ref(1)

// 空间区间实时几何碰撞预检
const collisionCheckResult = computed(() => {
  const start = mountForm.value.startU
  const end = mountForm.value.endU
  if (!start || !end) return { isConflict: false, isOutOfBounds: false }

  if (start < 1 || end > 42 || start > end) {
    return { isConflict: false, isOutOfBounds: true }
  }

  const conflictDev = getConflictDeviceInRange(start, end, null)
  if (conflictDev) {
    return { isConflict: true, isOutOfBounds: false, conflictDevice: conflictDev }
  }

  return { isConflict: false, isOutOfBounds: false }
})

function onStartUChange(val) {
  if (!val) return
  if (mountForm.value.endU < val) {
    mountForm.value.endU = Math.min(42, val + mountForm.value.uHeight - 1)
  }
  mountForm.value.uHeight = mountForm.value.endU - mountForm.value.startU + 1
  quickHeightSelected.value = mountForm.value.uHeight
  mountAnchorSlot.value = mountForm.value.startU
  mountExpansionHint.value = ''
}

function onEndUChange(val) {
  if (!val) return
  if (val < mountForm.value.startU) {
    mountForm.value.endU = mountForm.value.startU
  }
  mountForm.value.uHeight = mountForm.value.endU - mountForm.value.startU + 1
  quickHeightSelected.value = mountForm.value.uHeight
  mountExpansionHint.value = ''
}

// 上架快捷规格选择：基于 anchor 槽位智能自适应向上/向下扩充区间
function onQuickHeightChange(height) {
  mountForm.value.uHeight = height
  const anchor = mountAnchorSlot.value || mountForm.value.startU

  if (height === 1) {
    mountForm.value.startU = anchor
    mountForm.value.endU = anchor
    mountExpansionHint.value = ''
    return
  }

  // 1. 优先探测向上扩充: 以 anchor 为起始底座，向 42U 顶端方向延伸 height 个 U 位
  const upEnd = anchor + height - 1
  const upFree = upEnd <= 42 && isRangeFree(anchor, upEnd, null)

  // 2. 探测向下避让扩充: 以 anchor 为顶端，向 1U 机柜底端方向延伸 height 个 U 位
  // 例如选定 39U，选 4U -> 向下延展为 39 - 4 + 1 = 36U ~ 39U
  const downStart = anchor - height + 1
  const downFree = downStart >= 1 && isRangeFree(downStart, anchor, null)

  if (upFree) {
    // 上方完全空闲可用 -> 严格标记为【向上扩充】
    mountForm.value.startU = anchor
    mountForm.value.endU = upEnd
    mountExpansionHint.value = `⬆️ 【已自动向上扩充】[${anchor}U ~ ${upEnd}U] (向 42U 顶端方向延展)`
  } else if (downFree) {
    // 上方有设备阻挡，下方空闲 -> 严格标记为【向下扩充】
    mountForm.value.startU = downStart
    mountForm.value.endU = anchor
    mountExpansionHint.value = `⬇️ 【已自动向下扩充】[${downStart}U ~ ${anchor}U] (上方有设备受阻，向 1U 底端方向延展)`
  } else {
    // 上下均被占用
    mountForm.value.startU = anchor
    mountForm.value.endU = upEnd <= 42 ? upEnd : (downStart >= 1 ? anchor : 42)
    mountExpansionHint.value = `🚫 【上下两侧均有设备冲突】扩充至 ${height}U 空间不足，无法上架！`
  }
}

// 打开设备上架弹窗 (若指定 slotU 则自动回填并锚定)
function handleOpenMountDialog(slotU = null) {
  const start = slotU !== null ? slotU : findFirstFreeSlot()
  mountAnchorSlot.value = start // 牢牢锚定初始选定或点击的基准槽位
  mountForm.value = {
    rackId: props.rack.rackId,
    deviceName: '',
    deviceType: 'IT_SERVER',
    iotDeviceKey: '',
    startU: start,
    endU: start,
    uHeight: 1,
    ratedPower: 0.50
  }
  quickHeightSelected.value = 1
  mountExpansionHint.value = ''
  mountDialogVisible.value = true
}

// ==================== 设备替换沙盘推演计算 ====================
const simUDelta = computed(() => {
  const oldH = replaceTarget.value?.uHeight || 0
  const newH = replaceForm.value.uHeight || 0
  return newH - oldH
})

const simNewUsedU = computed(() => {
  return Math.max(0, usedUCount.value + simUDelta.value)
})

const simNewUsageRate = computed(() => {
  return Math.round((simNewUsedU.value / 42) * 100)
})

const simPowerDelta = computed(() => {
  const oldP = Number(replaceTarget.value?.ratedPower || 0)
  const newP = Number(replaceForm.value.ratedPower || 0)
  return newP - oldP
})

const simNewPowerTotal = computed(() => {
  return Math.max(0, currentPowerTotal.value + simPowerDelta.value)
})

const simPowerLimit = computed(() => {
  return Number(props.rack?.powerRating || 6.0)
})

const simIsPowerOverloaded = computed(() => {
  return simNewPowerTotal.value > simPowerLimit.value
})

const simPowerRate = computed(() => {
  if (simPowerLimit.value <= 0) return 0
  return Math.round((simNewPowerTotal.value / simPowerLimit.value) * 100)
})

const expansionDirectionText = ref('')
const expansionErrorDetail = ref('')

const expansionDirectionTagType = computed(() => {
  if (simCollisionConflict.value) return 'danger'
  if (expansionDirectionText.value.includes('向上')) return 'success'
  if (expansionDirectionText.value.includes('向下')) return 'warning'
  return 'info'
})


// 沙盘空间区间冲突校验 (新设备在拟占区间是否撞上除原设备以外的其他设备)
const simCollisionConflict = computed(() => {
  const start = replaceForm.value.startU
  const end = replaceForm.value.endU
  if (!start || !end || !replaceTarget.value) return false

  if (start < 1 || end > 42 || start > end) return true

  if (expansionDirectionText.value.includes('无法扩充') || expansionErrorDetail.value) {
    return true
  }

  for (const s of rawSlots.value) {
    if (s.isOccupied && s.deviceId && s.deviceId !== replaceTarget.value.deviceId) {
      const devStart = s.startU
      const devEnd = devStart + s.uHeight - 1
      if (Math.max(start, devStart) <= Math.min(end, devEnd)) {
        return true
      }
    }
  }
  return false
})

const simCollisionConflictDesc = computed(() => {
  const start = replaceForm.value.startU
  const end = replaceForm.value.endU
  if (!start || !end || !replaceTarget.value) return ''

  if (expansionErrorDetail.value) {
    return expansionErrorDetail.value
  }

  if (start < 1 || end > 42) return `拟扩充区间 [${start}U-${end}U] 超出机柜 1~42U 物理边界！`

  for (const s of rawSlots.value) {
    if (s.isOccupied && s.deviceId && s.deviceId !== replaceTarget.value.deviceId) {
      const devStart = s.startU
      const devEnd = devStart + s.uHeight - 1
      if (Math.max(start, devStart) <= Math.min(end, devEnd)) {
        return `扩充的 U 位区间 [${start}U-${end}U] 与已有在架设备【${s.deviceName} (${devStart}U-${devEnd}U)】发生重叠冲突，无法进行替换！`
      }
    }
  }
  return '扩充的 U 区间已有设备，无法进行替换！'
})

// 拟替换新设备高度改变时，智能推演扩充方向 (优先向上，上阻向下，双阻报警)
function onSimHeightChange(newHeight) {
  if (!replaceTarget.value) return
  const oldStart = replaceTarget.value.startU
  const oldHeight = replaceTarget.value.uHeight
  const oldEnd = oldStart + oldHeight - 1
  const excludeId = replaceTarget.value.deviceId

  replaceForm.value.uHeight = newHeight

  if (newHeight <= oldHeight) {
    // 缩减或等额平替
    replaceForm.value.startU = oldStart
    replaceForm.value.endU = oldStart + newHeight - 1
    expansionDirectionText.value = newHeight === oldHeight ? '等额平替 (无空间扩张)' : `缩减空间 (释放 ${oldHeight - newHeight}U)`
    expansionErrorDetail.value = ''
    return
  }

  // 占用高度比原来高 -> 必须扩充 U 区间
  // 规则：优先向上扩充；若上方有设备则向下扩充；若上下都有设备则拦截报错
  const upTargetEnd = oldStart + newHeight - 1
  const upFree = upTargetEnd <= 42 && isRangeFree(oldEnd + 1, upTargetEnd, excludeId)

  const downTargetStart = oldEnd - newHeight + 1
  const downFree = downTargetStart >= 1 && isRangeFree(downTargetStart, oldStart - 1, excludeId)

  if (upFree) {
    // 1. 上面无设备 -> 向上扩充 (向 42U 顶端延展)
    replaceForm.value.startU = oldStart
    replaceForm.value.endU = upTargetEnd
    expansionDirectionText.value = `⬆️ 【已自动向上扩充】[${oldStart}U ~ ${upTargetEnd}U] (向 42U 顶端方向)`
    expansionErrorDetail.value = ''
  } else if (downFree) {
    // 2. 上面有设备/越界，但下方可用 -> 向下扩充 (向 1U 底端延展)
    replaceForm.value.startU = downTargetStart
    replaceForm.value.endU = oldEnd
    expansionDirectionText.value = `⬇️ 【已自动向下扩充】[${downTargetStart}U ~ ${oldEnd}U] (上方有设备受阻，向 1U 底端方向)`
    expansionErrorDetail.value = ''
  } else {
    // 3. 上下均有设备 (或越界) -> 阻断报错
    replaceForm.value.startU = oldStart
    replaceForm.value.endU = upTargetEnd <= 42 ? upTargetEnd : (downTargetStart >= 1 ? oldEnd : oldStart + newHeight - 1)
    expansionDirectionText.value = `🚫 【上下两侧均有设备冲突】扩充至 ${newHeight}U 空间不足，无法替换！`

    const upConflict = upTargetEnd > 42 
      ? { deviceName: '机柜顶部 42U 顶格越界', rangeText: '>42U' }
      : getConflictDeviceInRange(oldEnd + 1, upTargetEnd, excludeId)
    const downConflict = downTargetStart < 1
      ? { deviceName: '机柜底部 1U 底格越界', rangeText: '<1U' }
      : getConflictDeviceInRange(downTargetStart, oldStart - 1, excludeId)

    let detail = `拟将设备从 ${oldHeight}U 扩充至 ${newHeight}U：`
    if (upConflict) {
      detail += `上方区间 [${oldEnd + 1}U-${upTargetEnd}U] 已被【${upConflict.deviceName} (${upConflict.rangeText})】占用；`
    }
    if (downConflict) {
      detail += `下方区间 [${downTargetStart}U-${oldStart - 1}U] 已被【${downConflict.deviceName} (${downConflict.rangeText})】占用。`
    }
    detail += ' 上下两侧均无连续空闲 U 位，无法进行替换！'
    expansionErrorDetail.value = detail
  }
}

// 手动调整 U 位区间输入框
function onSimRangeManualChange(field) {
  if (!replaceTarget.value) return
  const start = replaceForm.value.startU
  const end = replaceForm.value.endU
  if (field === 'start') {
    if (start > end) {
      replaceForm.value.endU = start
    }
  } else if (field === 'end') {
    if (end < start) {
      replaceForm.value.startU = end
    }
  }
  const calcHeight = Math.max(1, replaceForm.value.endU - replaceForm.value.startU + 1)
  replaceForm.value.uHeight = calcHeight
  expansionDirectionText.value = `手动设定区间 (${calcHeight}U)`
  expansionErrorDetail.value = ''
}

function openReplaceSimulationModal(device) {
  detailDialogVisible.value = false
  replaceTarget.value = device
  const defaultHeight = device.uHeight || 2
  replaceForm.value = {
    deviceName: `新一代算力节点 (${device.deviceName} 升级替代)`,
    deviceType: device.deviceType || 'IT_SERVER',
    iotDeviceKey: device.iotDeviceKey ? `${device.iotDeviceKey}-NEW` : '',
    startU: device.startU,
    endU: device.startU + defaultHeight - 1,
    uHeight: defaultHeight,
    ratedPower: Number(device.ratedPower || 2.0) + 0.50
  }
  expansionDirectionText.value = '等额平替'
  expansionErrorDetail.value = ''
  replaceSimVisible.value = true
}

async function submitReplace() {
  if (!replaceTarget.value) return
  replacing.value = true
  try {
    const payload = {
      rackId: props.rack.rackId,
      deviceName: replaceForm.value.deviceName,
      deviceType: replaceForm.value.deviceType,
      iotDeviceKey: replaceForm.value.iotDeviceKey,
      startU: replaceForm.value.startU,
      uHeight: replaceForm.value.uHeight,
      ratedPower: replaceForm.value.ratedPower
    }
    await replaceDevice(replaceTarget.value.deviceId, payload)
    ElMessage.success('设备替换成功！U位画像、空间与供电负载已即时重新渲染')
    replaceSimVisible.value = false
    await fetchSlots()
    emit('refresh')
  } catch (err) {
    // 由统一拦截器处理
  } finally {
    replacing.value = false
  }
}

function findFirstFreeSlot() {
  const free = rawSlots.value.find(s => !s.isOccupied)
  return free ? free.slotU : 1
}

// 提交设备上架
async function submitMount() {
  if (!mountFormRef.value) return
  await mountFormRef.value.validate(async valid => {
    if (!valid) return
    submitting.value = true
    try {
      await mountDevice(mountForm.value)
      ElMessage.success('设备上架成功！U位已更新')
      mountDialogVisible.value = false
      await fetchSlots()
      emit('refresh')
    } catch (err) {
      // 错误信息已由 request.js 统一弹出
    } finally {
      submitting.value = false
    }
  })
}

// 查看设备详情
function handleViewDeviceDetail(device) {
  selectedDevice.value = device
  detailDialogVisible.value = true
}

// 确认下架设备
function confirmUnmount(device) {
  ElMessageBox.confirm(
    `确定要将设备【${device.deviceName}】从机架下架吗？下架将释放该设备占用的 ${device.uHeight}U 空间。`,
    '资产下架二次确认',
    {
      confirmButtonText: '确定下架',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    }
  ).then(async () => {
    unmounting.value = true
    try {
      await unmountDevice(device.deviceId)
      ElMessage.success('设备已成功下架！')
      detailDialogVisible.value = false
      await fetchSlots()
      emit('refresh')
    } catch (err) {
      // 错误由拦截器捕获
    } finally {
      unmounting.value = false
    }
  }).catch(() => {})
}

// 一键清空机柜所有在架设备
function handleClearAllDevices() {
  if (!props.rack?.rackId) return
  ElMessageBox.confirm(
    `确定要一键清空机柜【${props.rack.rackCode}】上的所有在架设备吗？此操作将移出全部设备并重置 U 位负载为 0U，动环温度与功耗将动态联动恢复为空机柜冷通道基线！`,
    '一键清空在架设备二次确认',
    {
      confirmButtonText: '确定清空',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    }
  ).then(async () => {
    clearingAll.value = true
    try {
      const res = await clearRackDevices(props.rack.rackId)
      ElMessage.success(res.msg || '机柜在架设备已全部清空！')
      await fetchSlots()
      emit('refresh')
    } catch (err) {
      // 由 request.js 统一处理
    } finally {
      clearingAll.value = false
    }
  }).catch(() => {})
}

// 视觉外观样式辅助函数
function getDeviceThemeClass(type) {
  if (type === 'IT_SERVER') return 'theme-server'
  if (type === 'IT_SWITCH') return 'theme-switch'
  if (type?.startsWith('SENSOR')) return 'theme-sensor'
  return 'theme-default'
}

function getDeviceTagType(type) {
  if (type === 'IT_SERVER') return 'primary'
  if (type === 'IT_SWITCH') return 'success'
  if (type?.startsWith('SENSOR')) return 'warning'
  return 'info'
}

function getDeviceIcon(type) {
  if (type === 'IT_SERVER') return Cpu
  if (type === 'IT_SWITCH') return Connection
  if (type?.startsWith('SENSOR')) return Odometer
  return Monitor
}

watch(
  () => props.rack?.rackId,
  newVal => {
    if (newVal) fetchSlots()
  },
  { immediate: true }
)
</script>

<style scoped>
.cabinet-view-root {
  display: flex;
  flex-direction: column;
  gap: 12px;
  background-color: #0b0f19;
  padding: 16px;
  color: #e2e8f0;
  box-sizing: border-box;
  flex: 1;
  height: 100%;
  max-height: 100%;
  min-height: 0;
  overflow: hidden; /* 保证根容器绝不产生滚动条 */
}

/* 顶部状态面板 */
.cabinet-header-card {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #151d2f;
  border: 1px solid #24334f;
  padding: 12px 16px;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rack-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rack-tag {
  font-size: 16px;
  font-weight: 700;
  color: #38bdf8;
  letter-spacing: 1px;
}

.room-tag {
  font-size: 13px;
  color: #94a3b8;
}

.rack-metrics {
  display: flex;
  align-items: center;
  gap: 24px;
}

.metric-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #94a3b8;
}

.metric-value {
  color: #f8fafc;
  font-weight: 600;
}

.metric-progress {
  width: 100px;
}

.header-right {
  display: flex;
  gap: 8px;
}

/* 图例栏 */
.legend-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 11px;
  color: #94a3b8;
  padding: 4px 8px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.legend-color {
  width: 12px;
  height: 12px;
  border-radius: 2px;
}

.legend-server {
  background: linear-gradient(135deg, #1e3a8a, #0f172a);
  border: 1px solid #3b82f6;
}

.legend-switch {
  background: linear-gradient(135deg, #065f46, #022c22);
  border: 1px solid #10b981;
}

.legend-sensor {
  background: linear-gradient(135deg, #854d0e, #451a03);
  border: 1px solid #f59e0b;
}

.legend-empty {
  background: #182234;
  border: 1px dashed #334155;
}

/* 机柜舞台架构 (内部唯一保留的滑栏，支持自适应满屏与极度丝滑滚动) */
.cabinet-stage {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  scroll-behavior: smooth;
  display: flex;
  justify-content: center;
  padding: 8px 12px 36px 12px;
  box-sizing: border-box;
}

/* 美化保留的里侧滑栏 */
.cabinet-stage::-webkit-scrollbar {
  width: 10px;
}

.cabinet-stage::-webkit-scrollbar-track {
  background: #090d16;
  border-radius: 5px;
}

.cabinet-stage::-webkit-scrollbar-thumb {
  background: #334155;
  border-radius: 5px;
  border: 2px solid #090d16;
}

.cabinet-stage::-webkit-scrollbar-thumb:hover {
  background: #38bdf8;
}

.cabinet-stage {
  scrollbar-width: thin;
  scrollbar-color: #334155 #090d16;
}

/* 工业级机柜金属外壳 */
.cabinet-chassis {
  width: 100%;
  max-width: 580px;
  background: #111622;
  border: 3px solid #334155;
  border-radius: 6px;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.7), 0 8px 10px -6px rgba(0, 0, 0, 0.7);
  display: flex;
  flex-direction: column;
}

/* 顶部金属横梁与散热孔 */
.chassis-header {
  background: #1e293b;
  border-bottom: 2px solid #334155;
  padding: 6px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.vent-grill {
  display: flex;
  gap: 3px;
}

.vent-grill span {
  width: 14px;
  height: 4px;
  background: #0f172a;
  border-radius: 2px;
}

.chassis-title {
  font-size: 11px;
  font-family: monospace;
  font-weight: bold;
  color: #64748b;
  letter-spacing: 1px;
}

/* 底部防静电支撑脚 */
.chassis-footer {
  background: #1e293b;
  border-top: 2px solid #334155;
  padding: 6px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 10px;
  color: #475569;
  font-family: monospace;
}

.rack-foot {
  width: 28px;
  height: 8px;
  background: #0f172a;
  border: 1px solid #475569;
  border-radius: 2px;
}

/* 机柜核心躯体 (左右立柱 + 设备 bay) */
.chassis-body {
  display: flex;
  background: #090d16;
}

/* 19 英寸左右安装立柱 */
.rack-rail {
  width: 36px;
  background: #141c2c;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  user-select: none;
}

.left-rail {
  border-right: 1px solid #1e293b;
}

.right-rail {
  border-left: 1px solid #1e293b;
}

.rail-unit {
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 3px;
  box-sizing: border-box;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}

.rail-unit.rail-active {
  background: rgba(56, 189, 248, 0.08);
}

.u-number {
  font-size: 9px;
  font-family: monospace;
  color: #64748b;
  font-weight: 600;
}

.rail-active .u-number {
  color: #38bdf8;
}

.screw-holes {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.screw {
  width: 4px;
  height: 4px;
  background: #334155;
  border-radius: 50%;
  box-shadow: inset 0 0 1px #000;
}

/* 设备装配主槽区 */
.equipment-bay {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #090d16;
}

.slot-block {
  width: 100%;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  position: relative;
  transition: all 0.15s ease-in-out;
  cursor: pointer;
}

/* 空闲槽位 (盲板式样) */
.empty-slot {
  background: #0e1422;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
  justify-content: center;
}

.empty-slot:hover {
  background: rgba(56, 189, 248, 0.12);
  border: 1px dashed #38bdf8;
  z-index: 5;
}

.empty-panel-lines {
  display: flex;
  flex-direction: column;
  gap: 2px;
  opacity: 0.15;
}

.vent-line {
  width: 140px;
  height: 1px;
  background: #94a3b8;
}

.empty-hint {
  position: absolute;
  font-size: 10px;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.15s;
}

.empty-slot:hover .empty-hint {
  opacity: 1;
  color: #38bdf8;
  font-weight: 600;
}

/* 已占用设备块 */
.device-block {
  border-bottom: 1px solid rgba(0, 0, 0, 0.8);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.1);
  padding: 0 4px;
}

.device-block:hover {
  filter: brightness(1.15);
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.3);
  z-index: 10;
}

/* 设备类型主题色彩 */
.theme-server {
  background: linear-gradient(90deg, #111d38 0%, #1e293b 50%, #111d38 100%);
  border-left: 3px solid #3b82f6;
}

.theme-switch {
  background: linear-gradient(90deg, #063d2f 0%, #064e3b 50%, #063d2f 100%);
  border-left: 3px solid #10b981;
}

.theme-sensor {
  background: linear-gradient(90deg, #3d2406 0%, #78350f 50%, #3d2406 100%);
  border-left: 3px solid #f59e0b;
}

.theme-default {
  background: linear-gradient(90deg, #1e293b 0%, #334155 50%, #1e293b 100%);
  border-left: 3px solid #94a3b8;
}

/* 机架左右固定耳件 */
.rack-ear {
  width: 12px;
  height: 80%;
  background: #1e293b;
  border: 1px solid #475569;
  border-radius: 2px;
  display: flex;
  flex-direction: column;
  justify-content: space-around;
  align-items: center;
  flex-shrink: 0;
}

.ear-screw {
  width: 4px;
  height: 4px;
  background: #cbd5e1;
  border-radius: 50%;
}

.ear-handle {
  width: 3px;
  height: 60%;
  background: #64748b;
  border-radius: 1px;
}

/* 仿真面板内部结构 */
.faceplate-content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px;
  overflow: hidden;
  height: 100%;
}

/* LED 指示灯群 */
.device-status-leds {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-right: 6px;
  flex-shrink: 0;
}

.led-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
}

.power-led {
  background-color: #22c55e;
  box-shadow: 0 0 6px #22c55e;
  animation: pulse-led 2s infinite ease-in-out;
}

.led-fault {
  background-color: #ef4444 !important;
  box-shadow: 0 0 6px #ef4444 !important;
}

.net-led {
  background-color: #38bdf8;
  box-shadow: 0 0 4px #38bdf8;
  animation: blink-led 0.8s infinite alternate;
}

@keyframes pulse-led {
  0%, 100% { opacity: 0.7; transform: scale(0.9); }
  50% { opacity: 1; transform: scale(1.1); }
}

@keyframes blink-led {
  0% { opacity: 0.3; }
  100% { opacity: 1; }
}

/* 设备主描述区 */
.device-main-info {
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
  min-width: 140px;
}

.device-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.device-icon {
  font-size: 13px;
  color: #38bdf8;
  flex-shrink: 0;
}

.device-name {
  font-size: 11px;
  font-weight: 600;
  color: #f1f5f9;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.type-badge {
  font-size: 9px;
  padding: 0 4px;
  height: 16px;
  line-height: 14px;
  flex-shrink: 0;
}

.device-sub-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 10px;
  color: #94a3b8;
  font-family: monospace;
}

/* 装饰性网口/硬盘仓 */
.device-decorative-panel {
  display: flex;
  align-items: center;
  margin: 0 8px;
  flex-shrink: 0;
}

.switch-port-matrix {
  display: grid;
  grid-template-columns: repeat(6, 6px);
  gap: 2px;
}

.rj45-port {
  width: 6px;
  height: 6px;
  background: #022c22;
  border: 1px solid #10b981;
  border-radius: 1px;
}

.server-drive-bays {
  display: flex;
  gap: 3px;
}

.drive-bay {
  width: 12px;
  height: 14px;
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: 1px;
}

/* 右侧 U 区间标识 */
.device-end-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.u-span-badge {
  font-size: 10px;
  font-family: monospace;
  color: #cbd5e1;
  background: rgba(0, 0, 0, 0.4);
  padding: 1px 6px;
  border-radius: 3px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.quick-unmount-btn {
  font-size: 11px;
  display: none;
}

.device-block:hover .quick-unmount-btn {
  display: inline-flex;
}

/* 弹窗中的碰撞校验样式 */
.collision-preview-box {
  margin-top: 14px;
}

.font-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.text-primary {
  color: #38bdf8;
  font-weight: 600;
}

.simulation-zone {
  margin-top: 16px;
  padding: 14px;
  background: rgba(56, 189, 248, 0.08);
  border: 1px dashed #38bdf8;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sim-title {
  font-size: 13px;
  font-weight: bold;
  color: #38bdf8;
}

.sim-desc {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.4;
}

/* 推演弹窗内排版 */
.sim-compare-row {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
}

.sim-card {
  border-radius: 6px;
  padding: 12px 14px;
  background: #151d2f;
  border: 1px solid #24334f;
}

.sim-card-old {
  border-left: 3px solid #ef4444;
}

.sim-card-new {
  border-left: 3px solid #10b981;
}

.sim-range-input-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sim-u-input {
  width: 78px !important;
}

.range-sep {
  color: #64748b;
  font-weight: bold;
}

.expansion-hint-badge {
  margin-top: 6px;
}

.sim-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.sim-device-title {
  font-size: 13px;
  font-weight: 600;
  color: #f1f5f9;
}

.sim-prop-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 12px;
}

.prop-label {
  color: #94a3b8;
}

.prop-value {
  color: #f8fafc;
  font-weight: 600;
}

.sim-arrow-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.sim-arrow {
  font-size: 20px;
  color: #38bdf8;
}

.sim-arrow-text {
  font-size: 10px;
  color: #64748b;
  margin-top: 4px;
}

.sim-impact-section {
  background: #0f172a;
  border: 1px solid #1e293b;
  border-radius: 6px;
  padding: 14px;
}

.impact-section-title {
  font-size: 13px;
  font-weight: bold;
  color: #f8fafc;
  margin-bottom: 12px;
}

.impact-card {
  background: #1e293b;
  border-radius: 4px;
  padding: 12px;
}

.impact-card-title {
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 8px;
}

.impact-metric-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: bold;
}

.metric-before {
  color: #94a3b8;
}

.metric-arrow {
  color: #64748b;
  font-size: 12px;
}

.metric-after {
  color: #38bdf8;
}

.metric-cap {
  font-size: 11px;
  color: #64748b;
  font-weight: normal;
}

.impact-hint {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 6px;
}

.text-danger {
  color: #ef4444 !important;
}

.mt-10 {
  margin-top: 10px;
}

.mt-16 {
  margin-top: 16px;
}

.danger-zone {
  margin-top: 20px;
  padding: 14px;
  border: 1px dashed #ef4444;
  border-radius: 6px;
  background: rgba(239, 68, 68, 0.05);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.danger-title {
  font-size: 13px;
  font-weight: bold;
  color: #ef4444;
}

.danger-desc {
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}

/* 动环秒级遥测推屏状态条 */
.telemetry-live-panel {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 10px;
  padding: 6px 14px;
  background: rgba(15, 23, 42, 0.6);
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 12px;
  flex-wrap: wrap;
}

.telemetry-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tel-label {
  color: #94a3b8;
}

.tel-val {
  font-weight: bold;
}

.temp-normal {
  color: #10b981;
}

.temp-warning {
  color: #f59e0b;
}

.temp-critical {
  color: #ef4444;
  text-shadow: 0 0 6px rgba(239, 68, 68, 0.6);
}

.stream-status {
  margin-left: auto;
  color: #64748b;
  font-size: 11px;
}

.live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background-color: #64748b;
  display: inline-block;
  transition: all 0.3s ease;
}

.live-dot-active {
  background-color: #10b981;
  box-shadow: 0 0 8px #10b981;
  animation: pulseDot 1.8s infinite;
}

@keyframes pulseDot {
  0% { transform: scale(0.9); opacity: 0.7; }
  50% { transform: scale(1.3); opacity: 1; }
  100% { transform: scale(0.9); opacity: 0.7; }
}

/* 告警机柜红光呼吸闪烁视觉特效 */
.cabinet-chassis.chassis-alarm {
  border: 2px solid #f59e0b !important;
  box-shadow: 0 0 20px rgba(245, 158, 11, 0.35) !important;
}

.cabinet-chassis.chassis-critical {
  border: 2px solid #ef4444 !important;
  box-shadow: 0 0 28px rgba(239, 68, 68, 0.6) !important;
  animation: rackAlarmGlow 1.8s infinite ease-in-out;
}

@keyframes rackAlarmGlow {
  0%, 100% {
    box-shadow: 0 0 15px rgba(239, 68, 68, 0.4);
    border-color: #ef4444;
  }
  50% {
    box-shadow: 0 0 32px rgba(239, 68, 68, 0.85);
    border-color: #ff7875;
  }
}

.chassis-alarm-banner {
  background: linear-gradient(90deg, #b91c1c, #dc2626);
  color: #fff;
  padding: 6px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: bold;
  border-radius: 4px 4px 0 0;
  animation: bannerFlash 2s infinite alternate;
}

@keyframes bannerFlash {
  0% { background: linear-gradient(90deg, #991b1b, #dc2626); }
  100% { background: linear-gradient(90deg, #dc2626, #ef4444); }
}

.alarm-banner-icon {
  font-size: 14px;
}

.alarm-banner-text {
  flex: 1;
}

.alarm-banner-time {
  font-size: 11px;
  opacity: 0.85;
  font-weight: normal;
}

.pulse-alarm-tag {
  animation: tagPulse 1.5s infinite;
}

@keyframes tagPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.8; transform: scale(1.05); }
}
</style>
