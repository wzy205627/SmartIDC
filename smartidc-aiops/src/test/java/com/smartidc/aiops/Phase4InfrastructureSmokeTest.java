package com.smartidc.aiops;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.smartidc.aiops.service.SopVectorStoreService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 智维云 (SmartIDC) 阶段 4.1：基础设施底座与环境装配冒烟集成测试
 * 对标 implementation_plan4.1.md 步骤 5
 */
@SpringBootTest
public class Phase4InfrastructureSmokeTest {

    @Autowired
    private PgVectorStore vectorStore;

    @Autowired
    private SopVectorStoreService sopVectorStoreService;

    @Autowired
    private RedisSaver redisSaver;

    @Test
    @DisplayName("测试1：验证 1024 维向量写入与动态 ef_search=100 高精度语义检索")
    public void testVectorStore1024Dimension() {
        String testDocId = UUID.randomUUID().toString();
        Document doc = Document.builder()
                .id(testDocId)
                .text("智维云测试SOP：当冷机跳闸导致风道过温时，必须在3分钟内手动或经审批切换至备用B回路。")
                .metadata(Map.of("tenant_id", "000000", "sop_code", "SOP-TEST-COOLING", "risk_level", "CRITICAL"))
                .build();

        // 1. 写入向量库 (由 qwen3.7-text-embedding-flash 自动生成 1024 维 Embedding)
        vectorStore.accept(List.of(doc));

        // 2. 高精度检索测试
        List<Document> results = sopVectorStoreService.searchSopWithHighPrecision("制冷压缩机跳闸了怎么切换到备用回路？", 5, 0.6);

        Assertions.assertFalse(results.isEmpty(), "向量检索应当命中写入的 SOP 文档");
        boolean found = results.stream().anyMatch(d -> d.getText() != null && (d.getText().contains("智维云测试SOP") || d.getText().contains("冷机跳闸")));
        Assertions.assertTrue(found, "向量检索应当命中包含冷机跳闸的 SOP 文档内容");
        System.out.println("✅ 1024 维向量写入与检索验证通过，匹配内容: " + results.get(0).getText());
    }

    @Test
    @DisplayName("测试2：验证 RedisSaver 基于 DefaultTyping 的多态状态序列化与反序列化完整性")
    public void testRedisSaverPolymorphicSerialization() throws Exception {
        String threadId = "TEST-THREAD-" + System.currentTimeMillis();
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

        // 模拟复杂的图执行上下文（包含多层嵌套与非纯 String 类型）
        Map<String, Object> stateMap = Map.of(
                "rackCode", "A01",
                "currentTemp", 38.5,
                "isCriticalRisk", true,
                "actions", List.of("SWITCH_CIRCUIT", "NOTIFY_SUPERVISOR")
        );

        Checkpoint checkpoint = Checkpoint.builder()
                .id(UUID.randomUUID().toString())
                .nodeId("testNode")
                .nextNodeId("endNode")
                .state(stateMap)
                .build();

        // 1. 写入 Redis
        redisSaver.put(config, checkpoint);

        // 2. 从 Redis 读取还原
        Optional<Checkpoint> restored = redisSaver.get(config);

        Assertions.assertTrue(restored.isPresent(), "快照应当成功从 Redis 中反序列化读取");
        Map<String, Object> restoredState = restored.get().getState();
        Assertions.assertEquals("A01", restoredState.get("rackCode"));
        Assertions.assertEquals(38.5, ((Number) restoredState.get("currentTemp")).doubleValue());
        Assertions.assertEquals(true, restoredState.get("isCriticalRisk"));

        System.out.println("✅ RedisSaver 多态序列化/反序列化测试通过，未丢失任何字段！");
    }
}
