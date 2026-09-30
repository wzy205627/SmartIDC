package com.smartidc.aiops.util;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartidc.aiops.domain.dto.RcaReportDTO;
import com.smartidc.aiops.domain.dto.SopRecommendationDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 鲁棒性 JSON 清洗与提取工具类 (对标 implementation_plan4.3.md 任务 1.2)
 * 采用正则提取代码块 + 首尾花括号闭合截取双重防护，彻底过滤大模型前缀问候与后缀废话
 */
public class SafeJsonExtractor {

    private static final Logger log = LoggerFactory.getLogger(SafeJsonExtractor.class);

    private static final Pattern JSON_CODE_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

    private static final ObjectMapper LOOSE_MAPPER = new ObjectMapper()
        .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 正则优先提取 ```json ... ```，兜底寻找最外层首尾花括号 { ... }
     */
    public static String extractJsonBlock(String rawContent) {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            return "{}";
        }
        String content = rawContent.trim();

        // 1. 正则优先提取 ```json ... ``` 块内部的内容
        Matcher matcher = JSON_CODE_BLOCK_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 2. 兜底策略：寻找字符串中第一个 '{' 到最后一个 '}' 之间的闭合 JSON，彻底过滤前后缀废话
        int firstBrace = content.indexOf('{');
        int lastBrace = content.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return content.substring(firstBrace, lastBrace + 1).trim();
        }

        return content;
    }

    public static RcaReportDTO extractRca(String rawContent) {
        String cleanJson = extractJsonBlock(rawContent);
        try {
            return LOOSE_MAPPER.readValue(cleanJson, RcaReportDTO.class);
        } catch (Exception e) {
            log.error("❌ 解析 RCA 结构化输出失败, cleanJson: {}", cleanJson, e);
            throw new RuntimeException("RCA 报告解析异常: " + e.getMessage(), e);
        }
    }

    public static SopRecommendationDTO extractSop(String rawContent) {
        String cleanJson = extractJsonBlock(rawContent);
        try {
            return LOOSE_MAPPER.readValue(cleanJson, SopRecommendationDTO.class);
        } catch (Exception e) {
            log.error("❌ 解析 SOP 双通道推荐输出失败, cleanJson: {}", cleanJson, e);
            throw new RuntimeException("SOP 推荐解析异常: " + e.getMessage(), e);
        }
    }
}
