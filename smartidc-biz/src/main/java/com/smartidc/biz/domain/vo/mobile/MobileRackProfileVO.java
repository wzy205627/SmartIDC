package com.smartidc.biz.domain.vo.mobile;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 移动端机柜微画像轻量聚合视图出参 VO (<10KB 轻量设计)
 */
@Schema(description = "移动端机柜微画像轻量聚合视图")
public class MobileRackProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "机柜基础属性")
    private RackBaseInfoVO rackInfo;

    @Schema(description = "实时动环指标快照 (含离线哨兵状态)")
    private MobileRackTelemetryVO telemetry;

    @Schema(description = "42U 槽位设备摘要")
    private RackSlotSummaryVO uSlotsSummary;

    @Schema(description = "当前活动越限告警列表 (红灯先亮)")
    private List<MobileActiveAlarmVO> activeAlarms = new ArrayList<>();

    public MobileRackProfileVO() {
    }

    public MobileRackProfileVO(RackBaseInfoVO rackInfo, MobileRackTelemetryVO telemetry, RackSlotSummaryVO uSlotsSummary, List<MobileActiveAlarmVO> activeAlarms) {
        this.rackInfo = rackInfo;
        this.telemetry = telemetry;
        this.uSlotsSummary = uSlotsSummary;
        this.activeAlarms = activeAlarms != null ? activeAlarms : new ArrayList<>();
    }

    public RackBaseInfoVO getRackInfo() {
        return rackInfo;
    }

    public void setRackInfo(RackBaseInfoVO rackInfo) {
        this.rackInfo = rackInfo;
    }

    public MobileRackTelemetryVO getTelemetry() {
        return telemetry;
    }

    public void setTelemetry(MobileRackTelemetryVO telemetry) {
        this.telemetry = telemetry;
    }

    public RackSlotSummaryVO getuSlotsSummary() {
        return uSlotsSummary;
    }

    public void setuSlotsSummary(RackSlotSummaryVO uSlotsSummary) {
        this.uSlotsSummary = uSlotsSummary;
    }

    public List<MobileActiveAlarmVO> getActiveAlarms() {
        return activeAlarms;
    }

    public void setActiveAlarms(List<MobileActiveAlarmVO> activeAlarms) {
        this.activeAlarms = activeAlarms;
    }

    @Schema(description = "机柜基础属性")
    public static class RackBaseInfoVO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long rackId;
        private String rackCode;
        private String rackName;
        private String roomName;
        private String tenantId;
        private BigDecimal ratedPowerKva;
        private BigDecimal currentLoadRatio; // 负载率百分比 0..100
        private Integer status; // 0-空闲, 1-使用中, 2-维保中

        public RackBaseInfoVO() {
        }

        public RackBaseInfoVO(Long rackId, String rackCode, String rackName, String roomName, String tenantId, BigDecimal ratedPowerKva, BigDecimal currentLoadRatio, Integer status) {
            this.rackId = rackId;
            this.rackCode = rackCode;
            this.rackName = rackName;
            this.roomName = roomName;
            this.tenantId = tenantId;
            this.ratedPowerKva = ratedPowerKva;
            this.currentLoadRatio = currentLoadRatio;
            this.status = status;
        }

        public Long getRackId() {
            return rackId;
        }

        public void setRackId(Long rackId) {
            this.rackId = rackId;
        }

        public String getRackCode() {
            return rackCode;
        }

        public void setRackCode(String rackCode) {
            this.rackCode = rackCode;
        }

        public String getRackName() {
            return rackName;
        }

        public void setRackName(String rackName) {
            this.rackName = rackName;
        }

        public String getRoomName() {
            return roomName;
        }

        public void setRoomName(String roomName) {
            this.roomName = roomName;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public BigDecimal getRatedPowerKva() {
            return ratedPowerKva;
        }

        public void setRatedPowerKva(BigDecimal ratedPowerKva) {
            this.ratedPowerKva = ratedPowerKva;
        }

        public BigDecimal getCurrentLoadRatio() {
            return currentLoadRatio;
        }

        public void setCurrentLoadRatio(BigDecimal currentLoadRatio) {
            this.currentLoadRatio = currentLoadRatio;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }
    }

    @Schema(description = "42U 槽位设备摘要")
    public static class RackSlotSummaryVO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Integer totalU = 42;
        private Integer usedU = 0;
        private Integer freeU = 42;
        private List<MobileSlotDeviceItemVO> mountedDevices = new ArrayList<>();

        public RackSlotSummaryVO() {
        }

        public RackSlotSummaryVO(Integer totalU, Integer usedU, Integer freeU, List<MobileSlotDeviceItemVO> mountedDevices) {
            this.totalU = totalU != null ? totalU : 42;
            this.usedU = usedU != null ? usedU : 0;
            this.freeU = freeU != null ? freeU : (this.totalU - this.usedU);
            this.mountedDevices = mountedDevices != null ? mountedDevices : new ArrayList<>();
        }

        public Integer getTotalU() {
            return totalU;
        }

        public void setTotalU(Integer totalU) {
            this.totalU = totalU;
        }

        public Integer getUsedU() {
            return usedU;
        }

        public void setUsedU(Integer usedU) {
            this.usedU = usedU;
        }

        public Integer getFreeU() {
            return freeU;
        }

        public void setFreeU(Integer freeU) {
            this.freeU = freeU;
        }

        public List<MobileSlotDeviceItemVO> getMountedDevices() {
            return mountedDevices;
        }

        public void setMountedDevices(List<MobileSlotDeviceItemVO> mountedDevices) {
            this.mountedDevices = mountedDevices;
        }
    }

    @Schema(description = "42U 在架设备项")
    public static class MobileSlotDeviceItemVO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long deviceId;
        private String deviceCode;
        private String deviceName;
        private String deviceType; // IT_SERVER, IT_SWITCH, SENSOR_TEMP, SENSOR_UPS
        private Integer startU;
        private Integer uHeight;
        private String status; // 0-离线, 1-正常, 2-告警, 3-故障

        public MobileSlotDeviceItemVO() {
        }

        public MobileSlotDeviceItemVO(Long deviceId, String deviceCode, String deviceName, String deviceType, Integer startU, Integer uHeight, String status) {
            this.deviceId = deviceId;
            this.deviceCode = deviceCode;
            this.deviceName = deviceName;
            this.deviceType = deviceType;
            this.startU = startU;
            this.uHeight = uHeight;
            this.status = status;
        }

        public Long getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(Long deviceId) {
            this.deviceId = deviceId;
        }

        public String getDeviceCode() {
            return deviceCode;
        }

        public void setDeviceCode(String deviceCode) {
            this.deviceCode = deviceCode;
        }

        public String getDeviceName() {
            return deviceName;
        }

        public void setDeviceName(String deviceName) {
            this.deviceName = deviceName;
        }

        public String getDeviceType() {
            return deviceType;
        }

        public void setDeviceType(String deviceType) {
            this.deviceType = deviceType;
        }

        public Integer getStartU() {
            return startU;
        }

        public void setStartU(Integer startU) {
            this.startU = startU;
        }

        public Integer getuHeight() {
            return uHeight;
        }

        public void setuHeight(Integer uHeight) {
            this.uHeight = uHeight;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
