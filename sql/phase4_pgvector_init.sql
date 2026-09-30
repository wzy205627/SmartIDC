-- ==============================================================================
-- 智维云 (SmartIDC) 阶段 4.1：PostgreSQL 16 + pgvector 1024 维 HNSW 索引初始化 DDL
-- 对应模型: qwen3.7-text-embedding-flash (1024 维)
-- ==============================================================================

-- 1. 开启 vector 与 uuid 扩展插件
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. 创建 Spring AI 标准向量表 (明确声明 1024 维度)
CREATE TABLE IF NOT EXISTS vector_store (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    content TEXT NOT NULL,
    metadata JSONB NOT NULL,
    embedding VECTOR(1024) NOT NULL -- 严格对应 qwen3.7-text-embedding-flash
);

-- 3. 构建 HNSW 高性能近似近邻索引 (显式配置超参 m=16, ef_construction=64)
CREATE INDEX IF NOT EXISTS idx_vector_store_embedding_hnsw 
ON vector_store USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 4. 构建元数据 GIN 索引 (支持按租户 tenant_id、风险等级快速联合过滤)
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_gin 
ON vector_store USING gin (metadata);
