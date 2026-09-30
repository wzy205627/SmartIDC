package com.smartidc.aiops;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartidc.aiops.domain.dto.ActionExecutionResultDTO;
import com.smartidc.aiops.domain.dto.RcaReportDTO;
import com.smartidc.aiops.domain.dto.SopRecommendationDTO;
import com.smartidc.aiops.graph.IdcAioPsStateGraphService;
import com.smartidc.aiops.graph.StateKeys;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 智维云 (SmartIDC) 阶段 4.4：StateGraph 专用审批挂起网关、条件路由分流与 RedisSaver 物理断点自动化测试
 * 对标 implementation_plan4.4.md 任务 4
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase4StateGraphWorkflowTest {

    @Autowired
    private IdcAioPsStateGraphService stateGraphService;

    @Autowired
    private RedisSaver redisSaver;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @Order(1)
    @DisplayName("测试用例 1：低危自愈闭环断言 (testLowRiskSelfHealingFlow)")
    public void testLowRiskSelfHealingFlow() {
        String traceId = "trace-low-risk-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("🚀 [测试用例 1] 启动低危机柜 RACK-B01 自愈流转测试, traceId: " + traceId);

        // 执行工作流：机柜 RACK-B01 单机温升微热场景
        OverAllState finalState = stateGraphService.runWorkflow("RACK-B01", "单机柜微热告警", "000000", traceId);

        // 1. 工作流必须正常返回 State
        Assertions.assertNotNull(finalState, "工作流必须正常返回 State");

        // 2. 状态流转断言：低危任务直达 COMPLETED
        String status = finalState.value(StateKeys.STATUS, String.class).orElse(null);
        System.out.println("✅ 低危任务最终状态: " + status);
        Assertions.assertEquals("COMPLETED", status, "低危自愈任务必须顺利完成，状态为 COMPLETED");

        // 3. 风控决策断言：无需审批，自动通过
        String riskDecision = finalState.value(StateKeys.RISK_AUDIT_DECISION, String.class).orElse(null);
        System.out.println("✅ 风控审计决策: " + riskDecision);
        Assertions.assertEquals("AUTO_APPROVED", riskDecision, "低危自愈任务决策应为 AUTO_APPROVED");

        // 4. 执行回执存在性与内容断言 (Node 4 自动下发)
        Optional<ActionExecutionResultDTO> resultOpt = finalState.value(StateKeys.EXECUTION_RESULT, ActionExecutionResultDTO.class);
        Assertions.assertTrue(resultOpt.isPresent(), "低危任务必须生成动作执行回执");
        ActionExecutionResultDTO result = resultOpt.get();
        System.out.println("✅ 动作执行回执: " + result);

        Assertions.assertEquals("SUCCESS", result.getExecutionStatus(), "自愈动作执行状态必须为 SUCCESS");
        Assertions.assertNotNull(result.getExecutionId(), "必须包含唯一执行流水号");
        Assertions.assertTrue(result.getReceiptMessage().contains("自愈"), "回执消息中应包含自愈相关描述");

        System.out.println("🎉 [测试用例 1 通过] 低危自愈秒级畅通直达 END，零中断挂起！");
    }

    @Test
    @Order(2)
    @DisplayName("测试用例 2：高危精准挂起与 RedisSaver 快照断言 (testHighRiskPrecisionSuspendAndRedisSnapshot)")
    public void testHighRiskPrecisionSuspendAndRedisSnapshot() {
        String traceId = "trace-high-risk-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("🚀 [测试用例 2] 启动高危机柜 RACK-A01 审批挂起与快照测试, traceId: " + traceId);

        // 执行工作流：机柜 RACK-A01 冷机压缩机跳闸高危场景
        OverAllState finalState = stateGraphService.runWorkflow(
                "RACK-A01",
                "精密空调冷机压缩机跳闸过温告警",
                "000000",
                traceId
        );

        Assertions.assertNotNull(finalState, "工作流必须返回挂起状态 State");

        // 1. 执行阻断断言：高危操作严禁在未经审批前偷跑 Node 4
        Optional<ActionExecutionResultDTO> resultOpt = finalState.value(StateKeys.EXECUTION_RESULT, ActionExecutionResultDTO.class);
        Assertions.assertFalse(resultOpt.isPresent(), "高危任务挂起时，绝不能产生动作执行回执 (严禁偷跑 Node 4)！");

        // 2. 内存状态断言：状态应为 HITL_SUSPENDED，决策为 NEED_APPROVAL
        String status = finalState.value(StateKeys.STATUS, String.class).orElse(null);
        String riskDecision = finalState.value(StateKeys.RISK_AUDIT_DECISION, String.class).orElse(null);
        System.out.println("✅ 高危任务挂起状态: " + status + ", 决策: " + riskDecision);
        Assertions.assertEquals("HITL_SUSPENDED", status, "高危任务状态必须为 HITL_SUSPENDED");
        Assertions.assertEquals("NEED_APPROVAL", riskDecision, "高危任务风控决策必须为 NEED_APPROVAL");

        // 3. RedisSaver 物理快照存在性断言
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(traceId).build();
        Optional<Checkpoint> checkpointOpt = redisSaver.get(runnableConfig);
        Assertions.assertTrue(checkpointOpt.isPresent(), "Redis 中必须成功持久化 Checkpoint 状态快照！");

        Checkpoint checkpoint = checkpointOpt.get();
        System.out.println("✅ 成功从 Redis 读取到 Checkpoint 快照, id: " + checkpoint.getId()
                + ", nodeId: " + checkpoint.getNodeId() + ", nextNodeId: " + checkpoint.getNextNodeId());

        // 4. 物理节点停靠断言
        Assertions.assertEquals(IdcAioPsStateGraphService.NODE_APPROVAL_SUSPEND, checkpoint.getNodeId(),
                "快照当前停靠节点必须严格锁定在 APPROVAL_SUSPEND_NODE 专职挂起桩");
        Assertions.assertEquals(IdcAioPsStateGraphService.NODE_ACTION_EXECUTION, checkpoint.getNextNodeId(),
                "快照下一步待唤醒执行节点必须为 ACTION_EXECUTION_NODE");

        // 5. RedisSaver 序列化完整性断言 (反序列化零字段丢失)
        Map<String, Object> stateMap = checkpoint.getState();
        Assertions.assertNotNull(stateMap, "快照中的 State 数据结构不应为空");

        // 校验 RCA 根因报告反序列化保真
        Object rcaObj = stateMap.get(StateKeys.RCA_REPORT);
        Assertions.assertNotNull(rcaObj, "快照必须包含 RCA 诊断报告");
        RcaReportDTO rcaReport;
        if (rcaObj instanceof RcaReportDTO) {
            rcaReport = (RcaReportDTO) rcaObj;
        } else {
            rcaReport = OBJECT_MAPPER.convertValue(rcaObj, RcaReportDTO.class);
        }
        System.out.println("✅ Redis 快照中反序列化还原 RCA: " + rcaReport);
        Assertions.assertEquals("RACK-A01", rcaReport.getTargetRack(), "RCA 目标机柜必须精准还原为 RACK-A01");
        Assertions.assertNotNull(rcaReport.getRootCauseType(), "RCA 根因类型不能为空");
        Assertions.assertFalse(rcaReport.getEvidenceChain().isEmpty(), "RCA 证据链不能为空");

        // 校验 SOP 双通道预案反序列化保真
        Object sopObj = stateMap.get(StateKeys.SOP_RECOMMENDATION);
        Assertions.assertNotNull(sopObj, "快照必须包含 SOP 双通道建议");
        SopRecommendationDTO sopRec;
        if (sopObj instanceof SopRecommendationDTO) {
            sopRec = (SopRecommendationDTO) sopObj;
        } else {
            sopRec = OBJECT_MAPPER.convertValue(sopObj, SopRecommendationDTO.class);
        }
        System.out.println("✅ Redis 快照中反序列化还原 SOP: " + sopRec);
        Assertions.assertNotNull(sopRec.getControlFlow(), "SOP 机器控制流必须完整存在");
        Assertions.assertTrue("CRITICAL".equalsIgnoreCase(sopRec.getControlFlow().getRiskLevel())
                        || "HIGH".equalsIgnoreCase(sopRec.getControlFlow().getRiskLevel()),
                "SOP 风险等级必须为 CRITICAL 或 HIGH");
        Assertions.assertNotNull(sopRec.getDisplayView(), "SOP 人类渲染视图不能为空");

        System.out.println("🎉 [测试用例 2 通过] 高危精准拦截至 APPROVAL_SUSPEND_NODE，Redis 快照落库且反序列化 100% 完整！");
    }
}
