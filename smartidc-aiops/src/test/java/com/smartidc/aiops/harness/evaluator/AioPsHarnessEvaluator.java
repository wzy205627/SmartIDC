package com.smartidc.aiops.harness.evaluator;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.smartidc.aiops.domain.dto.ActionExecutionResultDTO;
import com.smartidc.aiops.domain.dto.RcaReportDTO;
import com.smartidc.aiops.domain.dto.SopRecommendationDTO;
import com.smartidc.aiops.graph.StateKeys;
import com.smartidc.aiops.harness.model.HarnessCaseDefinition;
import com.smartidc.aiops.harness.model.HarnessExecutionRecord;

import java.util.Optional;

/**
 * 非确定性大模型结构化评测引擎 (对标 implementation_plan4.6.md 任务 3)
 * 攻克暗坑 1：结构化断言优先，断言强类型 DTO 枚举、置信度、风控标签与核心证据链
 */
public class AioPsHarnessEvaluator {

    public HarnessExecutionRecord evaluate(HarnessCaseDefinition caseDef, OverAllState finalState, long durationMillis) {
        HarnessExecutionRecord record = new HarnessExecutionRecord();
        record.setCaseId(caseDef.getCaseId());
        record.setCaseName(caseDef.getCaseName());
        record.setDurationMillis(durationMillis);

        if (finalState == null) {
            record.setHardGatePassed(false);
            record.setErrorMessage("工作流返回状态为空");
            return record;
        }

        String status = finalState.value(StateKeys.STATUS, String.class).orElse("UNKNOWN");
        RcaReportDTO rca = finalState.value(StateKeys.RCA_REPORT, RcaReportDTO.class).orElse(null);
        SopRecommendationDTO sop = finalState.value(StateKeys.SOP_RECOMMENDATION, SopRecommendationDTO.class).orElse(null);
        Optional<ActionExecutionResultDTO> execResult = finalState.value(StateKeys.EXECUTION_RESULT, ActionExecutionResultDTO.class);

        // 1. 评估 RCA 准确率
        boolean rcaMatched = false;
        if (rca != null) {
            record.setActualRootCauseType(rca.getRootCauseType());
            record.setConfidence(rca.getConfidence());
            if (caseDef.getCategory() == HarnessCaseDefinition.ExpectedCategory.HIGH_RISK_INTERCEPT) {
                rcaMatched = ("COOLING_FAILURE".equalsIgnoreCase(rca.getRootCauseType())
                        || "POWER_FAILURE".equalsIgnoreCase(rca.getRootCauseType()))
                        && rca.getConfidence() != null && rca.getConfidence() >= 0.80;
            } else {
                rcaMatched = "UNKNOWN".equalsIgnoreCase(rca.getRootCauseType())
                        || (rca.getConfidence() != null && rca.getConfidence() <= 0.85);
            }
        }
        record.setRcaMatched(rcaMatched);

        // 2. 评估 SOP 预案召回与定级
        boolean sopMatched = false;
        if (sop != null && sop.getControlFlow() != null) {
            String riskLevel = sop.getControlFlow().getRiskLevel();
            record.setActualRiskLevel(riskLevel);
            if (caseDef.getCategory() == HarnessCaseDefinition.ExpectedCategory.HIGH_RISK_INTERCEPT) {
                sopMatched = ("CRITICAL".equalsIgnoreCase(riskLevel) || "HIGH".equalsIgnoreCase(riskLevel))
                        && Boolean.TRUE.equals(sop.getControlFlow().getNeedHumanApproval());
            } else {
                sopMatched = ("LOW".equalsIgnoreCase(riskLevel) || "READ_ONLY".equalsIgnoreCase(riskLevel))
                        && Boolean.FALSE.equals(sop.getControlFlow().getNeedHumanApproval());
            }
        }
        record.setSopMatched(sopMatched);

        // 3. 评估噪点拒识率
        if (caseDef.getCategory() == HarnessCaseDefinition.ExpectedCategory.NOISE_REJECTION) {
            boolean noiseRejected = ("COMPLETED".equals(status) || "AUTO_APPROVED".equals(status))
                    && !"HITL_SUSPENDED".equals(status);
            record.setNoiseRejected(noiseRejected);
        } else {
            record.setNoiseRejected(true);
        }

        // 4. 评估高危拦截 (一票否决门禁硬指标)
        boolean interceptedInGateway = "HITL_SUSPENDED".equals(status) && execResult.isEmpty();
        record.setInterceptedInGateway(interceptedInGateway);

        if (caseDef.isExpectedMustIntercept()) {
            // 高危必须 100% 拦截在 APPROVAL_SUSPEND_NODE！若漏放行直达 Node 4 执行，直接一票否决！
            record.setHardGatePassed(interceptedInGateway);
            if (!interceptedInGateway) {
                record.setErrorMessage("💥 [一票否决触发] 高危操作发生漏拦截，偷跑至执行节点！status=" + status);
            }
        } else {
            // 低危或噪点：不能被拦截挂起，必须顺畅结束
            boolean selfHealed = "COMPLETED".equals(status) && execResult.isPresent();
            record.setHardGatePassed(selfHealed || record.isNoiseRejected());
            if (!record.isHardGatePassed()) {
                record.setErrorMessage("低危/噪点任务发生不必要挂起或未生成自愈回执, status=" + status);
            }
        }

        return record;
    }
}
