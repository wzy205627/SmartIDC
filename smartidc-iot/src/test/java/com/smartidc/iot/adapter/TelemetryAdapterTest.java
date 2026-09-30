package com.smartidc.iot.adapter;

import com.smartidc.iot.adapter.impl.TelemetryAdapterImpl;
import com.smartidc.iot.domain.dto.TelemetryMetricDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 动环物联防腐层 (ACL Adapter) 单元测试
 */
class TelemetryAdapterTest {

    private TelemetryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TelemetryAdapterImpl();
    }

    @Test
    @DisplayName("标准报文与Topic解析转换测试")
    void testStandardPayloadAndTopicExtraction() {
        String topic = "/sys/smartidc/tenant-01/rack/A-03/telemetry";
        String payload = "{\"temperature\": 25.5, \"humidity\": 50.0, \"voltage\": 220.0, \"currentAmp\": 10.0, \"powerKw\": 2.2}";

        TelemetryMetricDTO dto = adapter.convert(topic, payload);

        assertNotNull(dto);
        assertEquals("tenant-01", dto.getTenantId());
        assertEquals("A-03", dto.getRackCode());
        assertEquals(new BigDecimal("25.50"), dto.getTemperature());
        assertEquals(new BigDecimal("50.00"), dto.getHumidity());
        assertEquals(new BigDecimal("220.00"), dto.getVoltage());
        assertEquals(new BigDecimal("10.00"), dto.getCurrentAmp());
        assertEquals(new BigDecimal("2.20"), dto.getPowerKw());
        assertNotNull(dto.getSampleTimestamp());
    }

    @Test
    @DisplayName("多别名与下划线蛇形命名字段自适应映射测试")
    void testMultiAliasMapping() {
        String topic = "/sys/smartidc/000000/rack/B-05/telemetry";
        String payload = "{\"temp_c\": 26.8, \"rh\": 48.3, \"volt\": 223.5, \"curr\": 15.2, \"power_kw\": 3.40}";

        TelemetryMetricDTO dto = adapter.convert(topic, payload);

        assertNotNull(dto);
        assertEquals("B-05", dto.getRackCode());
        assertEquals(new BigDecimal("26.80"), dto.getTemperature());
        assertEquals(new BigDecimal("48.30"), dto.getHumidity());
        assertEquals(new BigDecimal("223.50"), dto.getVoltage());
        assertEquals(new BigDecimal("15.20"), dto.getCurrentAmp());
        assertEquals(new BigDecimal("3.40"), dto.getPowerKw());
    }

    @Test
    @DisplayName("嵌套 metrics 结构报文解析测试")
    void testNestedMetricsStructure() {
        String topic = "/sys/smartidc/000000/rack/C-01/telemetry";
        String payload = "{\"deviceKey\": \"PDU-001\", \"metrics\": {\"temp\": 23.4, \"hum\": 55.1, \"v\": 219.0, \"a\": 8.5, \"p\": 1.86}}";

        TelemetryMetricDTO dto = adapter.convert(topic, payload);

        assertNotNull(dto);
        assertEquals("C-01", dto.getRackCode());
        assertEquals("PDU-001", dto.getDeviceKey());
        assertEquals(new BigDecimal("23.40"), dto.getTemperature());
        assertEquals(new BigDecimal("55.10"), dto.getHumidity());
        assertEquals(new BigDecimal("219.00"), dto.getVoltage());
        assertEquals(new BigDecimal("8.50"), dto.getCurrentAmp());
        assertEquals(new BigDecimal("1.86"), dto.getPowerKw());
    }

    @Test
    @DisplayName("物理安全量程防御与非法脏数据清洗测试")
    void testPhysicalRangeSanitization() {
        String topic = "/sys/smartidc/000000/rack/A-01/telemetry";
        // 异常数据: 温度 150℃ (超上限 100), 湿度 120% (超 100%), 电压 600V (超 500V), 电流 -5A (低于 0)
        String payload = "{\"temperature\": 150.0, \"humidity\": 120.0, \"voltage\": 600.0, \"currentAmp\": -5.0, \"powerKw\": 5.0}";

        TelemetryMetricDTO dto = adapter.convert(topic, payload);

        assertNotNull(dto);
        assertNull(dto.getTemperature(), "超上限温度应被置为 null 拦截");
        assertNull(dto.getHumidity(), "超上限湿度应被置为 null 拦截");
        assertNull(dto.getVoltage(), "超上限电压应被置为 null 拦截");
        assertNull(dto.getCurrentAmp(), "负数电流应被置为 null 拦截");
        assertEquals(new BigDecimal("5.00"), dto.getPowerKw(), "合规指标正常保留");
    }

    @Test
    @DisplayName("空报文及畸形报文容错兜底测试")
    void testEmptyAndMalformedPayload() {
        assertNull(adapter.convert("/test/topic", null));
        assertNull(adapter.convert("/test/topic", ""));
        assertNull(adapter.convert("/test/topic", "not-a-json-payload"));
    }
}
