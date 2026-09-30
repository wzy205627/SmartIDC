package com.smartidc.biz.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多轴动环历史时序回溯与降采样视图对象
 */
@Schema(description = "多轴动环历史时序回溯与降采样视图对象")
public class TelemetryTimelineVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "机架编码", example = "A-03")
    private String rackCode;

    @Schema(description = "机架ID", example = "3")
    private Long rackId;

    @Schema(description = "机房区域名称", example = "华东01-A区")
    private String roomName;

    @Schema(description = "查询时间范围", example = "24h")
    private String timeRange;

    @Schema(description = "时间分桶聚合步长(秒)", example = "300")
    private Integer bucketStepSeconds;

    @Schema(description = "时序横坐标点(按时间正序)")
    private List<String> timestamps = new ArrayList<>();

    @Schema(description = "多物理量降采样时序序列集合")
    private Map<String, List<BigDecimal>> series = new HashMap<>();

    @Schema(description = "该时间段内的越限告警区间集合 (供前端 MarkArea 高亮)")
    private List<AlarmIntervalVO> alarmIntervals = new ArrayList<>();

    public TelemetryTimelineVO() {
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

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(String timeRange) {
        this.timeRange = timeRange;
    }

    public Integer getBucketStepSeconds() {
        return bucketStepSeconds;
    }

    public void setBucketStepSeconds(Integer bucketStepSeconds) {
        this.bucketStepSeconds = bucketStepSeconds;
    }

    public List<String> getTimestamps() {
        return timestamps;
    }

    public void setTimestamps(List<String> timestamps) {
        this.timestamps = timestamps;
    }

    public Map<String, List<BigDecimal>> getSeries() {
        return series;
    }

    public void setSeries(Map<String, List<BigDecimal>> series) {
        this.series = series;
    }

    public List<AlarmIntervalVO> getAlarmIntervals() {
        return alarmIntervals;
    }

    public void setAlarmIntervals(List<AlarmIntervalVO> alarmIntervals) {
        this.alarmIntervals = alarmIntervals;
    }

    /**
     * 越限告警时段标记区间
     */
    @Schema(description = "越限告警时段标记区间")
    public static class AlarmIntervalVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "告警ID", example = "15")
        private Long alarmId;

        @Schema(description = "告警等级: CRITICAL, WARNING", example = "CRITICAL")
        private String alarmLevel;

        @Schema(description = "告警类型", example = "TEMP_HIGH")
        private String alarmType;

        @Schema(description = "开始时间 (yyyy-MM-dd HH:mm:ss)", example = "2026-09-19 12:10:33")
        private String startTime;

        @Schema(description = "消除时间 (为 null 表示当前仍在持续)", example = "2026-09-19 12:45:00")
        private String endTime;

        @Schema(description = "触发采样值", example = "39.40℃")
        private String metricValue;

        @Schema(description = "根因或摘要", example = "机柜严重超温越限 (>35.0℃)")
        private String summary;

        public AlarmIntervalVO() {
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

        public String getStartTime() {
            return startTime;
        }

        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }

        public String getEndTime() {
            return endTime;
        }

        public void setEndTime(String endTime) {
            this.endTime = endTime;
        }

        public String getMetricValue() {
            return metricValue;
        }

        public void setMetricValue(String metricValue) {
            this.metricValue = metricValue;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }
    }
}
