package com.smartidc.aiops.harness.replay;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartidc.aiops.domain.dto.ActionExecutionResultDTO;
import com.smartidc.aiops.domain.dto.RcaReportDTO;
import com.smartidc.aiops.domain.dto.SopRecommendationDTO;
import com.smartidc.aiops.graph.StateKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 离线录制与重放管理器 (对标 implementation_plan4.6.md 任务 2.2)
 * 支持通过 -Dsmartidc.harness.replay-mode=true 切换离线模式，加速 CI/CD 构建且不依赖外部 API
 */
public class ReplayFixtureManager {

    private static final Logger log = LoggerFactory.getLogger(ReplayFixtureManager.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static boolean isReplayMode() {
        return Boolean.parseBoolean(System.getProperty("smartidc.harness.replay-mode", "false"));
    }

    public static OverAllState loadFixtureState(String caseId) {
        String fixtureFileName = switch (caseId) {
            case "CASE-01" -> "fixture-case01.json";
            case "CASE-02" -> "fixture-case02.json";
            case "CASE-03" -> "fixture-case03.json";
            default -> throw new IllegalArgumentException("未知 Case ID: " + caseId);
        };

        try (InputStream is = ReplayFixtureManager.class.getResourceAsStream("/fixtures/" + fixtureFileName)) {
            if (is == null) {
                throw new IllegalStateException("未找到 Fixture 文件: /fixtures/" + fixtureFileName);
            }

            Map<String, Object> rawMap = OBJECT_MAPPER.readValue(is, new TypeReference<>() {});
            Map<String, Object> stateData = new HashMap<>();

            stateData.put(StateKeys.RACK_CODE, rawMap.get("rack_code"));
            stateData.put(StateKeys.STATUS, rawMap.get("status"));
            stateData.put(StateKeys.RISK_AUDIT_DECISION, rawMap.get("risk_audit_decision"));

            if (rawMap.containsKey("rca_report") && rawMap.get("rca_report") != null) {
                RcaReportDTO rca = OBJECT_MAPPER.convertValue(rawMap.get("rca_report"), RcaReportDTO.class);
                stateData.put(StateKeys.RCA_REPORT, rca);
            }

            if (rawMap.containsKey("sop_recommendation") && rawMap.get("sop_recommendation") != null) {
                SopRecommendationDTO sop = OBJECT_MAPPER.convertValue(rawMap.get("sop_recommendation"), SopRecommendationDTO.class);
                stateData.put(StateKeys.SOP_RECOMMENDATION, sop);
            }

            if (rawMap.containsKey("execution_result") && rawMap.get("execution_result") != null) {
                ActionExecutionResultDTO exec = OBJECT_MAPPER.convertValue(rawMap.get("execution_result"), ActionExecutionResultDTO.class);
                stateData.put(StateKeys.EXECUTION_RESULT, exec);
            }

            log.info("📼 [Replay Mode] 成功加载离线 Fixture: {}, caseId: {}", fixtureFileName, caseId);
            return new OverAllState(stateData);
        } catch (Exception e) {
            log.error("❌ 读取 Fixture 失败: {}", fixtureFileName, e);
            throw new RuntimeException("读取离线 Fixture 异常: " + e.getMessage(), e);
        }
    }
}
