package com.smartidc.biz.domain.vo.mobile;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 移动端机柜动环指标轻量快照 VO (含离线哨兵状态)
 */
@Schema(description = "移动端机柜动环指标轻量快照")
public class MobileRackTelemetryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "传感器通信状态: ONLINE / OFFLINE")
    private String status;

    @Schema(description = "进风/环境温度 (℃)")
    private BigDecimal temp;

    @Schema(description = "出风口温度 (℃)")
    private BigDecimal returnTemp;

    @Schema(description = "相对湿度 (%RH)")
    private BigDecimal humidity;

    @Schema(description = "母线供电电压 (V)")
    private BigDecimal voltage;

    @Schema(description = "工作电流 (A)")
    private BigDecimal current;

    @Schema(description = "实时有效功率 (kW)")
    private BigDecimal powerKw;

    @Schema(description = "动环健康评级: HEALTHY / WARNING / CRITICAL / UNKNOWN")
    private String healthLevel;

    @Schema(description = "指标最新更新时间戳")
    private LocalDateTime updatedAt;

    public MobileRackTelemetryVO() {
    }

    public MobileRackTelemetryVO(String status, BigDecimal temp, BigDecimal returnTemp, BigDecimal humidity, BigDecimal voltage, BigDecimal current, BigDecimal powerKw, String healthLevel, LocalDateTime updatedAt) {
        this.status = status;
        this.temp = temp;
        this.returnTemp = returnTemp;
        this.humidity = humidity;
        this.voltage = voltage;
        this.current = current;
        this.powerKw = powerKw;
        this.healthLevel = healthLevel;
        this.updatedAt = updatedAt;
    }

    public static MobileRackTelemetryVO offlineFallback(LocalDateTime lastHeartbeat) {
        MobileRackTelemetryVO vo = new MobileRackTelemetryVO();
        vo.setStatus("OFFLINE");
        vo.setHealthLevel("UNKNOWN");
        vo.setUpdatedAt(lastHeartbeat != null ? lastHeartbeat : LocalDateTime.now());
        return vo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getTemp() {
        return temp;
    }

    public void setTemp(BigDecimal temp) {
        this.temp = temp;
    }

    public BigDecimal getReturnTemp() {
        return returnTemp;
    }

    public void setReturnTemp(BigDecimal returnTemp) {
        this.returnTemp = returnTemp;
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

    public BigDecimal getCurrent() {
        return current;
    }

    public void setCurrent(BigDecimal current) {
        this.current = current;
    }

    public BigDecimal getPowerKw() {
        return powerKw;
    }

    public void setPowerKw(BigDecimal powerKw) {
        this.powerKw = powerKw;
    }

    public String getHealthLevel() {
        return healthLevel;
    }

    public void setHealthLevel(String healthLevel) {
        this.healthLevel = healthLevel;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
