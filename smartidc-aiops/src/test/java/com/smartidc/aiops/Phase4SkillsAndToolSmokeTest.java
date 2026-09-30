package com.smartidc.aiops;

import com.smartidc.aiops.bridge.AgentScopeToolBridge;
import com.smartidc.aiops.service.SopSkillLoaderService;
import com.smartidc.aiops.service.SopVectorStoreService;
import com.smartidc.aiops.service.TopologyInspectionService;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

/**
 * 智维云 (SmartIDC) 阶段 4.2：运维 Skills 规程库、拓扑探查服务与工具桥接集成冒烟测试
 * 对标 implementation_plan4.2.md 步骤 6
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase4SkillsAndToolSmokeTest {

    @Autowired
    private SopSkillLoaderService sopSkillLoaderService;

    @Autowired
    private SopVectorStoreService sopVectorStoreService;

    @Autowired
    private TopologyInspectionService topologyInspectionService;

    @Autowired
    private AgentScopeToolBridge agentScopeToolBridge;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("pgVectorJdbcTemplate")
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    @Order(1)
    @DisplayName("测试用例 1：验证 3 套 SOP Skills 规程结构化切片与全量元数据注水入库")
    public void testSopSkillLoaderService() {
        // 清理历史测试残留，保证 SOP Skills 切片入库纯净度
        jdbcTemplate.execute("TRUNCATE TABLE vector_store;");

        int loadedChunks = sopSkillLoaderService.loadAndEmbedAllSkills();
        System.out.println("✅ SOP Skills 入库完成，切片总数: " + loadedChunks);
        // 3 个 SOP 文件，每个包含 3 个二级步骤与 1 个前置前言，分块总数 >= 9
        Assertions.assertTrue(loadedChunks >= 9, "SOP 分块切片总数应大于等于 9 个");
    }

    @Test
    @Order(2)
    @DisplayName("测试用例 2：短 Query 弹性相似度检索测试（minSimilarity=0.50）与语义注水前缀验证")
    public void testElasticSimilarityRetrieval() {
        // 短 Query 检索
        String query = "压缩机跳闸";
        List<Document> results = sopVectorStoreService.retrieveTopSopGuide(query, 3, 0.50);

        Assertions.assertFalse(results.isEmpty(), "在弹性相似度 0.50 阈值下，短 Query 应当成功召回规程");
        Document topDoc = results.get(0);
        String text = topDoc.getText();
        System.out.println("✅ 命中文档内容: " + text);

        // 验证准确召回《冷机跳闸与备用回路切换规程》且包含语义注水前缀与元数据
        Assertions.assertTrue(text.startsWith("【规程名称: 冷机跳闸与备用回路切换规程"), "文档首行应当注入标准化语义前缀");
        Assertions.assertTrue(topDoc.getMetadata().containsKey("sop_code"), "元数据必须包含 sop_code");
        Assertions.assertEquals("PRECISION_AC", topDoc.getMetadata().get("device_type"), "设备类型元数据应当正确继承");
    }

    @Test
    @Order(3)
    @DisplayName("测试用例 3：动环拓扑探查高仿真 Mock 数据结构与高热联动验证")
    public void testTopologyInspectionMock() {
        // 1. 查询高热 A 区机柜
        Map<String, Object> hotRackMetrics = topologyInspectionService.queryAdjacentRackMetrics("RACK-A01");
        Assertions.assertNotNull(hotRackMetrics);
        Assertions.assertEquals("HOTSPOT_ALARM", hotRackMetrics.get("thermal_status"));
        Assertions.assertTrue(hotRackMetrics.containsKey("adjacent_racks"));
        List<?> adjacentRacks = (List<?>) hotRackMetrics.get("adjacent_racks");
        Assertions.assertFalse(adjacentRacks.isEmpty(), "相邻机柜列表不应为空");

        // 2. 查询供电与空调回路拓扑
        Map<String, Object> powerInfo = topologyInspectionService.getPowerTopologyInfo("RACK-A01");
        Assertions.assertNotNull(powerInfo);
        Assertions.assertEquals("OPEN", powerInfo.get("bus_tie_switch"), "A区高热场景下母联开关应为 OPEN");
        Assertions.assertEquals("TRIPPED", powerInfo.get("cooling_status"), "冷机状态应为 TRIPPED 跳闸");
        System.out.println("✅ 拓扑探查高仿真 Mock 验证通过: " + powerInfo);
    }

    @Test
    @Order(4)
    @DisplayName("测试用例 4：AgentScope 工具桥接工厂构建、工具暴露与异常自愈拦截验证")
    public void testAgentScopeToolBridge() {
        // 1. 构建 Toolkit
        Toolkit toolkit = agentScopeToolBridge.buildAgentToolkit("trace-smoke-001", "000000");
        Assertions.assertNotNull(toolkit, "AgentScope Toolkit 构建不应为空");

        // 2. 验证工具名称暴露完整
        Assertions.assertTrue(toolkit.getToolNames().contains("queryAdjacentRackMetrics"), "应包含 queryAdjacentRackMetrics 工具");
        Assertions.assertTrue(toolkit.getToolNames().contains("getPowerTopologyInfo"), "应包含 getPowerTopologyInfo 工具");
        Assertions.assertTrue(toolkit.getToolNames().contains("retrieveSopGuide"), "应包含 retrieveSopGuide 工具");

        // 3. 验证异常自愈拦截（抛出异常时返回自愈友好文本而非打崩框架）
        Object safeResult = agentScopeToolBridge.safeExecute("trace-smoke-001", "000000", () -> {
            throw new NullPointerException("机柜编码不存在或为空");
        });
        Assertions.assertNotNull(safeResult);
        String resultStr = safeResult.toString();
        System.out.println("✅ 异常拦截自愈提示: " + resultStr);
        Assertions.assertTrue(resultStr.contains("【工具探查提示】"), "遭遇异常应返回自愈友好提示语");
        Assertions.assertTrue(resultStr.contains("机柜编码不存在或为空"), "提示语应包含具体异常原因以引导模型自愈");
    }
}
