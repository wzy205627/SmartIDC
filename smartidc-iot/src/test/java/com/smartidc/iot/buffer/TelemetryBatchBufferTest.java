package com.smartidc.iot.buffer;

import com.smartidc.iot.domain.dto.TelemetryMetricDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TelemetryBatchBuffer 削峰缓冲池单元测试
 */
class TelemetryBatchBufferTest {

    private TelemetryBatchBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new TelemetryBatchBuffer(1000);
    }

    @Test
    @DisplayName("基础入队与容量计算测试")
    void testOfferAndSize() {
        assertEquals(0, buffer.size());

        TelemetryMetricDTO dto = new TelemetryMetricDTO();
        dto.setRackCode("A-03");
        dto.setTemperature(new BigDecimal("24.50"));

        assertTrue(buffer.offer(dto));
        assertEquals(1, buffer.size());

        assertFalse(buffer.offer(null));
        assertEquals(1, buffer.size());
    }

    @Test
    @DisplayName("双触发机制：满100条数量阈值触发测试")
    void testCountThresholdTrigger() {
        // 压入 99 条，数量阈值 100，未满且未超时 -> false
        for (int i = 0; i < 99; i++) {
            TelemetryMetricDTO dto = new TelemetryMetricDTO();
            dto.setRackCode("A-" + (i % 6 + 1));
            buffer.offer(dto);
        }
        assertFalse(buffer.shouldFlush(100, 10000L));

        // 再压入第 100 条 -> 达到数量阈值，立即触发 true
        TelemetryMetricDTO dto100 = new TelemetryMetricDTO();
        dto100.setRackCode("A-01");
        buffer.offer(dto100);

        assertTrue(buffer.shouldFlush(100, 10000L));
    }

    @Test
    @DisplayName("双触发机制：时间阈值触发测试")
    void testTimeThresholdTrigger() throws InterruptedException {
        TelemetryMetricDTO dto = new TelemetryMetricDTO();
        dto.setRackCode("B-01");
        buffer.offer(dto);

        // 仅有 1 条，未达到数量阈值 100；指定 50ms 超时阈值，等待 60ms 后应触发
        assertFalse(buffer.shouldFlush(100, 5000L));

        Thread.sleep(60);
        assertTrue(buffer.shouldFlush(100, 50L));
    }

    @Test
    @DisplayName("批量排干出队与数量重置测试")
    void testDrainBatch() {
        for (int i = 0; i < 50; i++) {
            TelemetryMetricDTO dto = new TelemetryMetricDTO();
            dto.setRackCode("C-0" + (i % 5 + 1));
            buffer.offer(dto);
        }
        assertEquals(50, buffer.size());

        // 单次最多抽取 20 条
        List<TelemetryMetricDTO> batch1 = buffer.drain(20);
        assertEquals(20, batch1.size());
        assertEquals(30, buffer.size());

        // 再次抽取 50 条 (当前剩余 30 条)
        List<TelemetryMetricDTO> batch2 = buffer.drain(50);
        assertEquals(30, batch2.size());
        assertEquals(0, buffer.size());
    }

    @Test
    @DisplayName("容量边界与防 OOM 丢弃保护测试")
    void testCapacityProtection() {
        TelemetryBatchBuffer smallBuffer = new TelemetryBatchBuffer(5);

        for (int i = 0; i < 5; i++) {
            TelemetryMetricDTO dto = new TelemetryMetricDTO();
            dto.setRackCode("D-01");
            assertTrue(smallBuffer.offer(dto));
        }

        assertEquals(5, smallBuffer.size());

        // 第 6 条入队，应被有界队列拦截丢弃并返回 false，杜绝内存溢出
        TelemetryMetricDTO overflowDto = new TelemetryMetricDTO();
        overflowDto.setRackCode("D-01");
        assertFalse(smallBuffer.offer(overflowDto));
        assertEquals(5, smallBuffer.size());
    }
}
