package com.smartidc.biz.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 动环告警事件实体 (idc_alarm_event)
 */
@TableName("idc_alarm_event")
@Schema(description = "动环告警事件实体")
public class IdcAlarmEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "alarm_id", type = IdType.AUTO)
    @Schema(description = "告警主键ID", example = "1")
    private Long alarmId;

    @Schema(description = "租户ID", example = "000000")
    private String tenantId;

    @Schema(description = "触发设备ID (0为机房/机柜整体)", example = "0")
    private Long deviceId;

    @Schema(description = "关联机柜ID", example = "3")
    private Long rackId;

    @Schema(description = "告警等级: WARNING(预警), ERROR(一般), CRITICAL(严重)", example = "WARNING")
    private String alarmLevel;

    @Schema(description = "告警类型: TEMP_HIGH(过温), VOLTAGE_LOW(欠压), POWER_FAIL(失电), HUMIDITY_HIGH(高湿)", example = "TEMP_HIGH")
    private String alarmType;

    @Schema(description = "触发告警时的遥测指标值", example = "32.50℃")
    private String metricValue;

    @Schema(description = "状态: 1-触发中, 2-已派单处理, 3-已消除, 4-已标记误报", example = "1")
    private Integer status;

    @Schema(description = "根因摘要或空间聚合归并说明", example = "[空间聚合主告警] 华东01-A区 发生集群性高温越限")
    private String rcaSummary;

    @Schema(description = "告警触发时间")
    private LocalDateTime triggerTime;

    @Schema(description = "消除时间")
    private LocalDateTime clearTime;

    public IdcAlarmEvent() {
    }

    public Long getAlarmId() {
        return alarmId;
    }

    public void setAlarmId(Long alarmId) {
        this.alarmId = alarmId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
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

    public String getAlarmLevel() {
        return alarmLevel;
    }

    public void setAlarmLevel(String alarmLevel) {
        this.alarmLevel = alarmLevel;
    }

    public String getAlarmType() {
        return alarmType;
    }

    public void setAlarmType(String alarmType) {
        this.alarmType = alarmType;
    }

    public String getMetricValue() {
        return metricValue;
    }

    public void setMetricValue(String metricValue) {
        this.metricValue = metricValue;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRcaSummary() {
        return rcaSummary;
    }

    public void setRcaSummary(String rcaSummary) {
        this.rcaSummary = rcaSummary;
    }

    public LocalDateTime getTriggerTime() {
        return triggerTime;
    }

    public void setTriggerTime(LocalDateTime triggerTime) {
        this.triggerTime = triggerTime;
    }

    public LocalDateTime getClearTime() {
        return clearTime;
    }

    public void setClearTime(LocalDateTime clearTime) {
        this.clearTime = clearTime;
    }

    @Override
    public String toString() {
        return "IdcAlarmEvent{" +
                "alarmId=" + alarmId +
                ", tenantId='" + tenantId + '\'' +
                ", deviceId=" + deviceId +
                ", rackId=" + rackId +
                ", alarmLevel='" + alarmLevel + '\'' +
                ", alarmType='" + alarmType + '\'' +
                ", metricValue='" + metricValue + '\'' +
                ", status=" + status +
                ", rcaSummary='" + rcaSummary + '\'' +
                ", triggerTime=" + triggerTime +
                ", clearTime=" + clearTime +
                '}';
    }
}
