package com.smartidc.aiops.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SopSkillLoaderService {

    private static final Logger log = LoggerFactory.getLogger(SopSkillLoaderService.class);
    private static final Pattern YAML_FRONTMATTER_PATTERN = Pattern.compile("^---\\s*\\r?\\n(.*?)\\r?\\n---\\s*\\r?\\n(.*)$", Pattern.DOTALL);

    private final PgVectorStore vectorStore;

    public SopSkillLoaderService(PgVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 自动扫描 classpath:skills/*.md 规程文件并执行结构化入库
     */
    public int loadAndEmbedAllSkills() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:skills/*.md");
            List<Document> allDocuments = new ArrayList<>();

            for (Resource resource : resources) {
                String content;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    content = reader.lines().collect(Collectors.joining("\n"));
                }
                List<Document> docs = parseMarkdownToDocuments(content, resource.getFilename());
                allDocuments.addAll(docs);
            }

            if (!allDocuments.isEmpty()) {
                vectorStore.accept(allDocuments);
                log.info("✅ 成功完成 {} 份 SOP Skills 规程的结构化切片与向量入库，总分块数: {}", resources.length, allDocuments.size());
            }
            return allDocuments.size();
        } catch (Exception e) {
            log.error("❌ SOP Skills 规程加载失败", e);
            throw new RuntimeException("SOP Skills 加载异常: " + e.getMessage(), e);
        }
    }

    public List<Document> parseMarkdownToDocuments(String fullContent, String fileName) {
        Matcher matcher = YAML_FRONTMATTER_PATTERN.matcher(fullContent);
        Map<String, Object> yamlMeta = new HashMap<>();
        String markdownBody = fullContent;

        if (matcher.find()) {
            String yamlContent = matcher.group(1);
            markdownBody = matcher.group(2);
            Yaml yaml = new Yaml();
            yamlMeta = yaml.load(yamlContent);
        }

        String sopTitle = String.valueOf(yamlMeta.getOrDefault("title", fileName));
        String deviceType = String.valueOf(yamlMeta.getOrDefault("device_type", "GENERIC"));
        String riskLevel = String.valueOf(yamlMeta.getOrDefault("risk_level", "LOW"));
        String sopCode = String.valueOf(yamlMeta.getOrDefault("skill_id", fileName));

        // 按 Markdown 二级标题 "## " 进行结构化切割
        String[] sections = markdownBody.split("(?m)^##\\s+");
        List<Document> documentList = new ArrayList<>();

        for (int i = 0; i < sections.length; i++) {
            String sectionText = sections[i].trim();
            if (sectionText.isEmpty()) continue;

            String stepTitle = "概述/前置规范";
            String stepBody = sectionText;
            int firstLineBreak = sectionText.indexOf("\n");
            if (firstLineBreak > 0 && i > 0) { // 第一个块通常是前言
                stepTitle = sectionText.substring(0, firstLineBreak).trim();
                stepBody = sectionText.substring(firstLineBreak).trim();
            }

            // 1. 语义注水前缀：拼接关键业务特征，强化 Embedding 稠密关联
            String enrichedContent = String.format(
                "【规程名称: %s | 适用设备: %s | 风险等级: %s | 当前步骤: %s】\n\n%s",
                sopTitle, deviceType, riskLevel, stepTitle, stepBody
            );

            // 2. 元数据全量继承沉淀
            Map<String, Object> metadata = new HashMap<>(yamlMeta);
            metadata.put("sop_code", sopCode);
            metadata.put("title", sopTitle);
            metadata.put("device_type", deviceType);
            metadata.put("risk_level", riskLevel);
            metadata.put("step_title", stepTitle);
            metadata.put("chunk_index", i);
            metadata.put("tenant_id", "000000"); // 默认平台全量可用

            Document doc = Document.builder()
                .id(UUID.randomUUID().toString())
                .text(enrichedContent)
                .metadata(metadata)
                .build();

            documentList.add(doc);
        }

        return documentList;
    }
}
