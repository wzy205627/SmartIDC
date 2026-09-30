package com.smartidc.biz.engine.alarm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 空间关联聚合抑制引擎单元测试
 */
class SpatialDeduplicationTest {

    private SpatialDeduplicationEngine dedupEngine;

    @BeforeEach
    void setUp() {
        dedupEngine = new SpatialDeduplicationEngine();
        dedupEngine.setWindowMs(5000L); // 5秒测试窗口
        dedupEngine.setClusterRackThreshold(3); // 3台机柜聚合
    }

    @Test
    @DisplayName("单机柜告警：未达空间聚合阈值，保持独立告警")
    void testSingleRackNoAggregation() {
        SpatialDeduplicationEngine.DeduplicationDecision d1 =
                dedupEngine.evaluate("华东01-A区", "A-01", "TEMP_HIGH");
        assertFalse(d1.isAggregated());
        assertEquals(1, d1.getInvolvedRacks().size());

        SpatialDeduplicationEngine.DeduplicationDecision d2 =
                dedupEngine.evaluate("华东01-A区", "A-02", "TEMP_HIGH");
        assertFalse(d2.isAggregated());
        assertEquals(2, d2.getInvolvedRacks().size());
    }

    @Test
    @DisplayName("集群性越限：同一机房3台机柜同类越限，触发空间关联聚合与主告警升格")
    void testClusterRacksAggregation() {
        String room = "华东01-A区";
        String type = "TEMP_HIGH";

        dedupEngine.evaluate(room, "A-01", type);
        dedupEngine.evaluate(room, "A-02", type);

        // 第 3 台机柜进入同一机房窗口
        SpatialDeduplicationEngine.DeduplicationDecision d3 =
                dedupEngine.evaluate(room, "A-03", type);

        assertTrue(d3.isAggregated(), "满3台机柜必须触发空间关联聚合");
        assertEquals(3, d3.getInvolvedRacks().size());
        assertTrue(d3.getInvolvedRacks().contains("A-01"));
        assertTrue(d3.getInvolvedRacks().contains("A-02"));
        assertTrue(d3.getInvolvedRacks().contains("A-03"));
        assertNotNull(d3.getMasterSummary());
        assertTrue(d3.getMasterSummary().contains("[空间聚合主告警]"));
        assertTrue(d3.getMasterSummary().contains("华东01-A区"));
    }
}
