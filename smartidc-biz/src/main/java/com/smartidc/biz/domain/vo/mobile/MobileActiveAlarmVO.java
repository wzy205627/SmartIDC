package com.smartidc.biz.domain.vo.mobile;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 移动端机柜当前活动告警出参 VO
 */
@Schema(description = "机柜活动告警视图")
public class MobileActiveAlarmVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "告警主键ID")
    private Long alarmId;

    @Schema(description = "告警等级: WARNING, ERROR, CRITICAL")
    private String alarmLevel;

    @Schema(description = "告警类型: TEMP_HIGH, POWER_FAIL, WATER_LEAK 等")
    private String alarmType;

    @Schema(description = "触发时的遥测值")
    private String metricValue;

    @Schema(description = "根因摘要或告警描述")
    private String description;

    @Schema(description = "告警触发时间")
    private LocalDateTime triggerTime;

    public MobileActiveAlarmVO() {
    }

    public MobileActiveAlarmVO(Long alarmId, String alarmLevel, String alarmType, String metricValue, String description, LocalDateTime triggerTime) {
        this.alarmId = alarmId;
        this.alarmLevel = alarmLevel;
        this.alarmType = alarmType;
        this.metricValue = metricValue;
        this.description = description;
        this.triggerTime = triggerTime;
    }

    public Long getAlarmId() {
        return alarmId;
    }

    public void setAlarmId(Long alarmId) {
        this.alarmId = alarmId;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getTriggerTime() {
        return triggerTime;
    }

    public void setTriggerTime(LocalDateTime triggerTime) {
        this.triggerTime = triggerTime;
    }
}
