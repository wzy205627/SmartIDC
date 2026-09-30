package com.smartidc.biz.engine.alarm;

import com.smartidc.iot.domain.dto.TelemetryMetricDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 动环告警阈值与等级评估规则引擎
 * 针对机柜温度、电压、湿度指标提供带迟滞回差 (Hysteresis) 的精确判定
 */
@Component
public class AlarmRuleEngine {

    // 温度规则 (℃)
    public static final BigDecimal TEMP_CRITICAL = new BigDecimal("35.0");
    public static final BigDecimal TEMP_WARNING = new BigDecimal("30.0");
    public static final BigDecimal TEMP_SAFE_RECOVER = new BigDecimal("28.0");

    // 电压规则 (V)
    public static final BigDecimal VOLT_CRITICAL_BLACKOUT = new BigDecimal("50.0");
    public static final BigDecimal VOLT_WARNING_LOW = new BigDecimal("198.0");
    public static final BigDecimal VOLT_SAFE_RECOVER = new BigDecimal("205.0");

    // 湿度规则 (%RH)
    public static final BigDecimal HUM_CRITICAL = new BigDecimal("90.0");
    public static final BigDecimal HUM_WARNING = new BigDecimal("80.0");
    public static final BigDecimal HUM_SAFE_RECOVER = new BigDecimal("75.0");

    /**
     * 针对单条遥测数据执行全面规则研判
     *
     * @param metric 遥测指标 DTO
     * @return 评估结果列表 (可能包含温度、电压等多项评估)
     */
    public List<AlarmEvaluationResult> evaluate(TelemetryMetricDTO metric) {
        List<AlarmEvaluationResult> results = new ArrayList<>();
        if (metric == null) {
            return results;
        }

        // 1. 评估机柜温度
        BigDecimal temp = metric.getTemperature();
        if (temp != null) {
            if (temp.compareTo(TEMP_CRITICAL) > 0) {
                results.add(AlarmEvaluationResult.violation("TEMP_HIGH", "CRITICAL",
                        temp.toPlainString() + "℃", "机柜严重超温越限 (>35.0℃)"));
            } else if (temp.compareTo(TEMP_WARNING) > 0) {
                results.add(AlarmEvaluationResult.violation("TEMP_HIGH", "WARNING",
                        temp.toPlainString() + "℃", "机柜温度超温预警 (>30.0℃)"));
            } else if (temp.compareTo(TEMP_SAFE_RECOVER) <= 0) {
                results.add(AlarmEvaluationResult.safeRecovered("TEMP_HIGH",
                        temp.toPlainString() + "℃", "机柜温度已安全回落至回差线以下 (<=28.0℃)"));
            }
        }

        // 2. 评估供电电压
        BigDecimal volt = metric.getVoltage();
        if (volt != null) {
            if (volt.compareTo(VOLT_CRITICAL_BLACKOUT) < 0) {
                results.add(AlarmEvaluationResult.violation("POWER_FAIL", "CRITICAL",
                        volt.toPlainString() + "V", "机柜疑似失电/断电异常 (<50.0V)"));
            } else if (volt.compareTo(VOLT_WARNING_LOW) < 0) {
                results.add(AlarmEvaluationResult.violation("VOLTAGE_LOW", "WARNING",
                        volt.toPlainString() + "V", "机柜供电电压偏低预警 (<198.0V)"));
            } else if (volt.compareTo(VOLT_SAFE_RECOVER) >= 0) {
                results.add(AlarmEvaluationResult.safeRecovered("VOLTAGE_LOW",
                        volt.toPlainString() + "V", "机柜供电电压已恢复正常 (>=205.0V)"));
            }
        }

        // 3. 评估相对湿度
        BigDecimal hum = metric.getHumidity();
        if (hum != null) {
            if (hum.compareTo(HUM_CRITICAL) > 0) {
                results.add(AlarmEvaluationResult.violation("HUMIDITY_HIGH", "CRITICAL",
                        hum.toPlainString() + "%RH", "机柜湿度严重超标 (>90.0%RH)"));
            } else if (hum.compareTo(HUM_WARNING) > 0) {
                results.add(AlarmEvaluationResult.violation("HUMIDITY_HIGH", "WARNING",
                        hum.toPlainString() + "%RH", "机柜湿度偏高预警 (>80.0%RH)"));
            } else if (hum.compareTo(HUM_SAFE_RECOVER) <= 0) {
                results.add(AlarmEvaluationResult.safeRecovered("HUMIDITY_HIGH",
                        hum.toPlainString() + "%RH", "机柜湿度已回落正常安全区间 (<=75.0%RH)"));
            }
        }

        return results;
    }
}
