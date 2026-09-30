package com.smartidc.biz.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 机房设备资产与传感器实体 (对应表 idc_device)
 */
@TableName("idc_device")
@Schema(description = "机房设备资产实体")
public class IdcDevice implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "设备ID", example = "1")
    private Long deviceId;

    @Schema(description = "租户编号", example = "000000")
    private String tenantId;

    @Schema(description = "所在机柜ID", example = "3")
    private Long rackId;

    @Schema(description = "设备名称", example = "核心交换机 H3C-S6800")
    private String deviceName;

    @Schema(description = "设备类型: SENSOR_TEMP, SENSOR_UPS, IT_SERVER, IT_SWITCH", example = "IT_SWITCH")
    private String deviceType;

    @Schema(description = "绑定的物联网平台 DeviceKey", example = "SW-A03-001")
    private String iotDeviceKey;

    @Schema(description = "起始U位位置 (1-42)", example = "40")
    private Integer startU;

    @Schema(description = "占用U位高度", example = "2")
    @JsonProperty("uHeight")
    private Integer uHeight;

    @Schema(description = "额定功率(kW)", example = "0.80")
    private BigDecimal ratedPower;

    @Schema(description = "运行状态: 0-离线, 1-正常, 2-告警, 3-故障", example = "1")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    public IdcDevice() {
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Long getRackId() {
        return rackId;
    }

    public void setRackId(Long rackId) {
        this.rackId = rackId;
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

    public String getIotDeviceKey() {
        return iotDeviceKey;
    }

    public void setIotDeviceKey(String iotDeviceKey) {
        this.iotDeviceKey = iotDeviceKey;
    }

    public Integer getStartU() {
        return startU;
    }

    public void setStartU(Integer startU) {
        this.startU = startU;
    }

    public Integer getUHeight() {
        return uHeight;
    }

    public void setUHeight(Integer uHeight) {
        this.uHeight = uHeight;
    }

    public BigDecimal getRatedPower() {
        return ratedPower;
    }

    public void setRatedPower(BigDecimal ratedPower) {
        this.ratedPower = ratedPower;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
