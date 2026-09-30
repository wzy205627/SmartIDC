package com.smartidc.aiops.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 智维云 AIOps - 应急 SOP 向量检索与 Session 级 HNSW 精度调优服务
 * 对标 implementation_plan4.1.md 步骤 4.2
 */
@Service
public class SopVectorStoreService {

    private final PgVectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public SopVectorStoreService(PgVectorStore vectorStore, @Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 高精度检索 SOP 预案 (执行前临时调大 ef_search 提高长尾预案召回率)
     *
     * @param query         运维排障提问或根因现象描述
     * @param topK          返回匹配 TopK 预案条数
     * @param minSimilarity 最低余弦相似度阈值 (0.0 ~ 1.0)
     * @return 匹配命中的 SOP 文档切片列表
     */
    public List<Document> searchSopWithHighPrecision(String query, int topK, double minSimilarity) {
        // 在当前会话内动态设置 HNSW 搜索深度，显著提高长尾预案的召回精度
        jdbcTemplate.execute("SET LOCAL hnsw.ef_search = 100;");

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(minSimilarity)
                .build();

        return vectorStore.similaritySearch(request);
    }

    /**
     * 弹性相似度检索 SOP 预案（对标 implementation_plan4.2.md 任务 4.2）
     * 默认放宽阈值至 0.50，配合 topK=3，防止长篇规程因词频稀释被硬编码 0.75 误拦截
     *
     * @param query 运维排障提问或根因现象描述
     * @param topK 返回匹配条数
     * @param minSimilarity 弹性余弦相似度阈值
     * @return 匹配命中的 SOP 文档切片列表
     */
    public List<Document> retrieveTopSopGuide(String query, int topK, double minSimilarity) {
        return searchSopWithHighPrecision(query, topK, minSimilarity);
    }

    /**
     * 弹性相似度检索 SOP 预案（默认 topK=3, minSimilarity=0.50）
     *
     * @param query 运维排障提问或根因现象描述
     * @return 匹配命中的 SOP 文档切片列表
     */
    public List<Document> retrieveTopSopGuide(String query) {
        return retrieveTopSopGuide(query, 3, 0.50);
    }
}
