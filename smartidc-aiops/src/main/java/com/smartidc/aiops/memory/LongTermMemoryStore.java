package com.smartidc.aiops.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 长期记忆元数据过滤与多租户隔离存储服务 (对标 implementation_plan4.3.md 任务 2)
 * 强制过滤 category = 'HISTORICAL_INSIGHT' 与 tenant_id，彻底阻断 SOP 原文误召回与多租户穿透
 */
@Component
public class LongTermMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryStore.class);

    private final PgVectorStore vectorStore;

    public LongTermMemoryStore(PgVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 【事前检索】：带分类与租户元数据过滤检索，防止 SOP 原文混入，防止跨租户穿透
     */
    public String retrieveHistoricalInsights(String rackCode, String faultSymptom, String tenantId) {
        try {
            String query = String.format("机柜 %s 发生 %s", rackCode, faultSymptom);
            FilterExpressionBuilder b = new FilterExpressionBuilder();

            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(2)
                    .similarityThreshold(0.45)
                    // 核心防线：强制过滤类别与租户，防止把 4.2 的 SOP 原文当成历史病历召回，阻断跨租户穿透
                    .filterExpression(
                            b.and(
                                    b.eq("category", "HISTORICAL_INSIGHT"),
                                    b.eq("tenant_id", tenantId)
                            ).build()
                    )
                    .build();

            List<Document> matches = vectorStore.similaritySearch(request);
            if (matches == null || matches.isEmpty()) {
                return "- 暂无针对该机柜的历史异常处置病历，请按标准流程探查。";
            }

            return matches.stream()
                    .map(d -> "- [历史案例参考]: " + d.getText())
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("⚠️ 调取长期记忆失败，降级为空: {}", e.getMessage());
            return "- 历史记忆库离线，使用标准规程推演。";
        }
    }

    /**
     * 【事后沉淀】：当工单被主管批准且排障成功后，异步沉淀为新一条长期记忆
     */
    public void recordNewInsight(String rackCode, String faultType, String insightSummary, String tenantId) {
        Document insightDoc = Document.builder()
                .id(UUID.randomUUID().toString())
                .text(String.format("【机柜 %s 历史排障病历 | 故障类型: %s】: %s", rackCode, faultType, insightSummary))
                .metadata(Map.of(
                        "tenant_id", tenantId,
                        "category", "HISTORICAL_INSIGHT",
                        "rack_code", rackCode,
                        "fault_type", faultType,
                        "recorded_at", System.currentTimeMillis()
                ))
                .build();

        vectorStore.accept(List.of(insightDoc));
        log.info("🧠 [长期记忆进化] 成功为机柜 {} 写入一条排障成功经验至 pgvector 记忆库", rackCode);
    }
}
