package com.smartidc.biz.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 动环遥测时序快照实体 (按天分区表 idc_telemetry_snapshot)
 */
@TableName("idc_telemetry_snapshot")
@Schema(description = "动环遥测时序快照实体")
public class IdcTelemetrySnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "snapshot_id", type = IdType.AUTO)
    @Schema(description = "自增主键ID", example = "1")
    private Long snapshotId;

    @Schema(description = "设备/传感器ID", example = "101")
    private Long deviceId;

    @Schema(description = "机柜ID", example = "3")
    private Long rackId;

    @Schema(description = "实时温度(℃)", example = "24.5")
    private BigDecimal temperature;

    @Schema(description = "实时湿度(%RH)", example = "45.2")
    private BigDecimal humidity;

    @Schema(description = "输入电压(V)", example = "221.80")
    private BigDecimal voltage;

    @Schema(description = "工作电流(A)", example = "12.30")
    private BigDecimal currentAmp;

    @Schema(description = "实时功耗(kW)", example = "2.73")
    private BigDecimal powerKw;

    @Schema(description = "遥测采样时间戳")
    private LocalDateTime sampleTime;

    public IdcTelemetrySnapshot() {
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public Long getRackId() {
        return rackId;
    }

    public void setRackId(Long rackId) {
        this.rackId = rackId;
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

    public LocalDateTime getSampleTime() {
        return sampleTime;
    }

    public void setSampleTime(LocalDateTime sampleTime) {
        this.sampleTime = sampleTime;
    }

    @Override
    public String toString() {
        return "IdcTelemetrySnapshot{" +
                "snapshotId=" + snapshotId +
                ", deviceId=" + deviceId +
                ", rackId=" + rackId +
                ", temperature=" + temperature +
                ", humidity=" + humidity +
                ", voltage=" + voltage +
                ", currentAmp=" + currentAmp +
                ", powerKw=" + powerKw +
                ", sampleTime=" + sampleTime +
                '}';
    }
}
