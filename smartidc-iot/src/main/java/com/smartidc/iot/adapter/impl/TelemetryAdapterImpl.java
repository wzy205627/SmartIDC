package com.smartidc.iot.adapter.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.smartidc.iot.adapter.TelemetryAdapter;
import com.smartidc.iot.domain.dto.TelemetryMetricDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动环物联报文防腐层落地实现 (ACL)
 * 职责：
 * 1. 从 MQTT Topic 结构中解析租户与机架编码；
 * 2. 对多厂商异构 JSON 报文进行多别名自适应映射；
 * 3. 物理量程安全防御与非法脏数据清洗；
 * 4. 转换为系统统一标准的 TelemetryMetricDTO 领域值对象。
 */
@Component
public class TelemetryAdapterImpl implements TelemetryAdapter {

    private static final Logger log = LoggerFactory.getLogger(TelemetryAdapterImpl.class);

    /**
     * 动环标准 MQTT Topic 正则表达式
     * 示例: /sys/smartidc/000000/rack/A-03/telemetry
     */
    private static final Pattern TOPIC_PATTERN = Pattern.compile("^/sys/smartidc/([^/]+)/rack/([^/]+)/telemetry$");

    // 物理量程极值安全阈值常数
    private static final BigDecimal MIN_TEMP = new BigDecimal("-40.0");
    private static final BigDecimal MAX_TEMP = new BigDecimal("100.0");
    private static final BigDecimal MIN_HUMIDITY = BigDecimal.ZERO;
    private static final BigDecimal MAX_HUMIDITY = new BigDecimal("100.0");
    private static final BigDecimal MIN_VOLTAGE = BigDecimal.ZERO;
    private static final BigDecimal MAX_VOLTAGE = new BigDecimal("500.0");
    private static final BigDecimal MIN_CURRENT = BigDecimal.ZERO;
    private static final BigDecimal MAX_CURRENT = new BigDecimal("200.0");
    private static final BigDecimal MIN_POWER = BigDecimal.ZERO;
    private static final BigDecimal MAX_POWER = new BigDecimal("100.0");

    @Override
    public TelemetryMetricDTO convert(String topic, String payload) {
        if (payload == null || payload.isBlank()) {
            log.warn("[ACL-Adapter] 收到空报文，丢弃. topic={}", topic);
            return null;
        }

        try {
            // 1. 尝试从 Topic 中解析 tenantId 与 rackCode
            String tenantId = "000000";
            String rackCode = null;
            if (topic != null) {
                Matcher matcher = TOPIC_PATTERN.matcher(topic);
                if (matcher.find()) {
                    tenantId = matcher.group(1);
                    rackCode = matcher.group(2);
                }
            }

            // 2. 解析 JSON 报文体
            JSONObject root = JSON.parseObject(payload);
            if (root == null) {
                return null;
            }

            // 报文支持嵌套结构 (metrics: { ... }) 或扁平结构
            JSONObject metrics = root.getJSONObject("metrics");
            if (metrics == null) {
                metrics = root;
            }

            // 若 Topic 未解析出租户或机柜，则尝试从 JSON 根节点回退读取
            if (rackCode == null || rackCode.isBlank()) {
                rackCode = root.getString("rackCode");
            }
            if (root.containsKey("tenantId") && !root.getString("tenantId").isBlank()) {
                tenantId = root.getString("tenantId");
            }

            String deviceKey = root.getString("deviceKey");
            if (deviceKey == null || deviceKey.isBlank()) {
                deviceKey = metrics.getString("deviceKey");
            }

            // 3. 采样时间戳提取
            Long timestamp = root.getLong("timestamp");
            if (timestamp == null) {
                timestamp = metrics.getLong("sampleTimestamp");
            }
            if (timestamp == null) {
                timestamp = root.getLong("ts");
            }
            if (timestamp == null || timestamp <= 0) {
                timestamp = System.currentTimeMillis();
            }

            // 4. 自适应映射物理量指标并进行量程安全清洗
            BigDecimal temperature = sanitize(extractDecimal(metrics, root, "temperature", "temp", "temp_c", "tempC", "t"),
                    MIN_TEMP, MAX_TEMP, "温度 (℃)", rackCode);

            BigDecimal humidity = sanitize(extractDecimal(metrics, root, "humidity", "hum", "rh", "h"),
                    MIN_HUMIDITY, MAX_HUMIDITY, "湿度 (%RH)", rackCode);

            BigDecimal voltage = sanitize(extractDecimal(metrics, root, "voltage", "volt", "v"),
                    MIN_VOLTAGE, MAX_VOLTAGE, "电压 (V)", rackCode);

            BigDecimal currentAmp = sanitize(extractDecimal(metrics, root, "currentAmp", "current_amp", "current", "curr", "a"),
                    MIN_CURRENT, MAX_CURRENT, "电流 (A)", rackCode);

            BigDecimal powerKw = sanitize(extractDecimal(metrics, root, "powerKw", "power_kw", "power", "active_power", "activePower", "p"),
                    MIN_POWER, MAX_POWER, "有功功率 (kW)", rackCode);

            // 5. 组装标准领域值对象
            TelemetryMetricDTO dto = new TelemetryMetricDTO();
            dto.setTenantId(tenantId);
            dto.setRackCode(rackCode);
            dto.setDeviceKey(deviceKey);
            dto.setTemperature(temperature);
            dto.setHumidity(humidity);
            dto.setVoltage(voltage);
            dto.setCurrentAmp(currentAmp);
            dto.setPowerKw(powerKw);
            dto.setSampleTimestamp(timestamp);

            return dto;
        } catch (Exception e) {
            log.error("[ACL-Adapter] 动环报文防腐转换失败. topic={}, payload={}, error={}", topic, payload, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 多别名自适应提取数值
     */
    private BigDecimal extractDecimal(JSONObject primary, JSONObject fallback, String... keys) {
        for (String key : keys) {
            BigDecimal val = primary.getBigDecimal(key);
            if (val != null) {
                return val.setScale(2, RoundingMode.HALF_UP);
            }
        }
        if (primary != fallback) {
            for (String key : keys) {
                BigDecimal val = fallback.getBigDecimal(key);
                if (val != null) {
                    return val.setScale(2, RoundingMode.HALF_UP);
                }
            }
        }
        return null;
    }

    /**
     * 物理量程极值过滤与校验
     */
    private BigDecimal sanitize(BigDecimal val, BigDecimal min, BigDecimal max, String label, String rackCode) {
        if (val == null) {
            return null;
        }
        if (val.compareTo(min) < 0 || val.compareTo(max) > 0) {
            log.warn("[ACL-Adapter] 异常脏数据越界拦截: 机柜=[{}], 指标=[{}], 测量值=[{}], 允许安全量程=[{} ~ {}]",
                    rackCode, label, val, min, max);
            return null;
        }
        return val;
    }
}
