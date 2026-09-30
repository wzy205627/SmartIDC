package com.smartidc.biz.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 设备上架请求传输对象 (DTO)
 */
@Schema(description = "设备上架请求参数")
public class DeviceMountDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "目标机架ID不能为空")
    @Schema(description = "所在机柜ID", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long rackId;

    @NotBlank(message = "设备名称不能为空")
    @Schema(description = "设备名称", example = "接入交换机 Huawei-CloudEngine", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceName;

    @NotBlank(message = "设备类型不能为空")
    @Schema(description = "设备类型: SENSOR_TEMP, SENSOR_UPS, IT_SERVER, IT_SWITCH", example = "IT_SWITCH", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceType;

    @Schema(description = "绑定的物联网 DeviceKey (传感器可填)", example = "SW-A03-002")
    private String iotDeviceKey;

    @NotNull(message = "起始U位不能为空")
    @Min(value = 1, message = "起始U位最小为 1U")
    @Max(value = 42, message = "起始U位最大为 42U")
    @Schema(description = "起始U位位置 (1-42)", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer startU;

    @NotNull(message = "占用U位高度不能为空")
    @Min(value = 1, message = "占用U位高度最小为 1U")
    @Max(value = 42, message = "占用U位高度最大为 42U")
    @Schema(description = "占用U位高度 (单位: U)", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("uHeight")
    private Integer uHeight;

    @NotNull(message = "额定功率不能为空")
    @Schema(description = "额定功耗(kW)", example = "0.45", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal ratedPower;

    public DeviceMountDTO() {
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
}
