package com.smartidc.iot.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 动环遥测标准领域指标值对象 (防腐层输出)
 */
@Schema(description = "动环遥测标准领域值对象")
public class TelemetryMetricDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "租户编号")
    private String tenantId;

    @Schema(description = "机架编号", example = "A-03")
    private String rackCode;

    @Schema(description = "机架ID")
    private Long rackId;

    @Schema(description = "设备ID")
    private Long deviceId;

    @Schema(description = "设备物联网标识 DeviceKey", example = "TH-A03-001")
    private String deviceKey;

    @Schema(description = "实时温度 (℃)", example = "24.5")
    private BigDecimal temperature;

    @Schema(description = "实时湿度 (%RH)", example = "48.2")
    private BigDecimal humidity;

    @Schema(description = "输入电压 (V)", example = "220.5")
    private BigDecimal voltage;

    @Schema(description = "工作电流 (A)", example = "16.2")
    private BigDecimal currentAmp;

    @Schema(description = "实时功耗 (kW)", example = "3.57")
    private BigDecimal powerKw;

    @Schema(description = "采样时间戳")
    private Long sampleTimestamp;

    public TelemetryMetricDTO() {
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getRackCode() {
        return rackCode;
    }

    public void setRackCode(String rackCode) {
        this.rackCode = rackCode;
    }

    public Long getRackId() {
        return rackId;
    }

    public void setRackId(Long rackId) {
        this.rackId = rackId;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceKey() {
        return deviceKey;
    }

    public void setDeviceKey(String deviceKey) {
        this.deviceKey = deviceKey;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public BigDecimal getHumidity() {
        return humidity;
    }

    public void setHumidity(BigDecimal humidity) {
        this.humidity = humidity;
    }

    public BigDecimal getVoltage() {
        return voltage;
    }

    public void setVoltage(BigDecimal voltage) {
        this.voltage = voltage;
    }

    public BigDecimal getCurrentAmp() {
        return currentAmp;
    }

    public void setCurrentAmp(BigDecimal currentAmp) {
        this.currentAmp = currentAmp;
    }

    public BigDecimal getPowerKw() {
        return powerKw;
    }

    public void setPowerKw(BigDecimal powerKw) {
        this.powerKw = powerKw;
    }

    public Long getSampleTimestamp() {
        return sampleTimestamp;
    }

    public void setSampleTimestamp(Long sampleTimestamp) {
        this.sampleTimestamp = sampleTimestamp;
    }
}
