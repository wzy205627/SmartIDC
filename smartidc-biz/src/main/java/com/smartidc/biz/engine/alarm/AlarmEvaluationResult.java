package com.smartidc.biz.engine.alarm;

/**
 * 动环指标阈值评估结果值对象
 */
public class AlarmEvaluationResult {

    private boolean violated;
    private String alarmType;
    private String alarmLevel;
    private String metricValue;
    private String description;
    private boolean recovered;

    public AlarmEvaluationResult() {
    }

    public static AlarmEvaluationResult normal() {
        AlarmEvaluationResult r = new AlarmEvaluationResult();
        r.setViolated(false);
        r.setRecovered(false);
        return r;
    }

    public static AlarmEvaluationResult violation(String alarmType, String alarmLevel, String metricValue, String description) {
        AlarmEvaluationResult r = new AlarmEvaluationResult();
        r.setViolated(true);
        r.setAlarmType(alarmType);
        r.setAlarmLevel(alarmLevel);
        r.setMetricValue(metricValue);
        r.setDescription(description);
        r.setRecovered(false);
        return r;
    }

    public static AlarmEvaluationResult safeRecovered(String alarmType, String metricValue, String description) {
        AlarmEvaluationResult r = new AlarmEvaluationResult();
        r.setViolated(false);
        r.setAlarmType(alarmType);
        r.setMetricValue(metricValue);
        r.setDescription(description);
        r.setRecovered(true);
        return r;
    }

    public boolean isViolated() {
        return violated;
    }

    public void setViolated(boolean violated) {
        this.violated = violated;
    }

    public String getAlarmType() {
        return alarmType;
    }

    public void setAlarmType(String alarmType) {
        this.alarmType = alarmType;
    }

    public String getAlarmLevel() {
        return alarmLevel;
    }

    public void setAlarmLevel(String alarmLevel) {
        this.alarmLevel = alarmLevel;
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

    public boolean isRecovered() {
        return recovered;
    }

    public void setRecovered(boolean recovered) {
        this.recovered = recovered;
    }

    @Override
    public String toString() {
        return "AlarmEvaluationResult{" +
                "violated=" + violated +
                ", alarmType='" + alarmType + '\'' +
                ", alarmLevel='" + alarmLevel + '\'' +
                ", metricValue='" + metricValue + '\'' +
                ", recovered=" + recovered +
                '}';
    }
}
