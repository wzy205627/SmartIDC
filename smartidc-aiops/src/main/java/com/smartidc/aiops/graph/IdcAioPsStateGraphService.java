package com.smartidc.aiops.graph;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.smartidc.aiops.domain.dto.ActionExecutionResultDTO;
import com.smartidc.aiops.domain.dto.RcaReportDTO;
import com.smartidc.aiops.domain.dto.SopRecommendationDTO;
import com.smartidc.aiops.service.IdcRcaAgentService;
import com.smartidc.aiops.service.IdcSopAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 外层 Spring AI Alibaba StateGraph 工作流编排与风险审计定级服务 (对标 implementation_plan4.4.md)
 * 采用 条件路由边 (addConditionalEdges) + 专职审批挂起网关 (APPROVAL_SUSPEND_NODE) 拓扑解耦：
 * 1. 低危自愈秒级直达 ACTION_EXECUTION_NODE ➔ END，零阻塞；
 * 2. 高危任务分流至专职挂起桩 APPROVAL_SUSPEND_NODE，触发 RedisSaver 快照持久化后安全挂起并释放线程；
 * 3. Node 4 (ACTION_EXECUTION_NODE) 具备幂等防重与“双重人格”（低危自愈 / 阶段4.5审批后唤醒恢复）。
 */
@Service
public class IdcAioPsStateGraphService {

    private static final Logger log = LoggerFactory.getLogger(IdcAioPsStateGraphService.class);

    public static final String NODE_RCA = "RCA_NODE";
    public static final String NODE_SOP = "SOP_NODE";
    public static final String NODE_RISK_AUDIT = "RISK_AUDIT_NODE";
    public static final String NODE_APPROVAL_SUSPEND = "APPROVAL_SUSPEND_NODE";
    public static final String NODE_ACTION_EXECUTION = "ACTION_EXECUTION_NODE";

    private final IdcRcaAgentService rcaAgentService;
    private final IdcSopAgentService sopAgentService;
    private final RedisSaver redisSaver;
    private final CompiledGraph workflowGraph;

    @Autowired
    public IdcAioPsStateGraphService(
            IdcRcaAgentService rcaAgentService,
            IdcSopAgentService sopAgentService,
            RedisSaver redisSaver) {
        this.rcaAgentService = rcaAgentService;
        this.sopAgentService = sopAgentService;
        this.redisSaver = redisSaver;
        this.workflowGraph = buildWorkflowGraph();
    }

    private CompiledGraph buildWorkflowGraph() {
        try {
            StateGraph graph = new StateGraph("idcAioPsWorkflow", () -> {
                Map<String, KeyStrategy> strategies = new HashMap<>();
                strategies.put(StateKeys.ALARM_INPUT, new ReplaceStrategy());
                strategies.put(StateKeys.RACK_CODE, new ReplaceStrategy());
                strategies.put(StateKeys.FAULT_SYMPTOM, new ReplaceStrategy());
                strategies.put(StateKeys.TENANT_ID, new ReplaceStrategy());
                strategies.put(StateKeys.TRACE_ID, new ReplaceStrategy());
                strategies.put(StateKeys.RCA_REPORT, new ReplaceStrategy());
                strategies.put(StateKeys.SOP_RECOMMENDATION, new ReplaceStrategy());
                strategies.put(StateKeys.RISK_AUDIT_DECISION, new ReplaceStrategy());
                strategies.put(StateKeys.STATUS, new ReplaceStrategy());
                strategies.put(StateKeys.EXECUTION_RESULT, new ReplaceStrategy());
                strategies.put(StateKeys.APPROVAL_DECISION, new ReplaceStrategy());
                strategies.put(StateKeys.APPROVAL_COMMENT, new ReplaceStrategy());
                strategies.put(StateKeys.OVERRIDE_PARAMS, new ReplaceStrategy());
                return strategies;
            });

            // 1. 注册五大节点
            graph.addNode(NODE_RCA, node_async((NodeAction) this::executeRcaNode));
            graph.addNode(NODE_SOP, node_async((NodeAction) this::executeSopNode));
            graph.addNode(NODE_RISK_AUDIT, node_async((NodeAction) this::executeRiskAuditNode));
            graph.addNode(NODE_APPROVAL_SUSPEND, node_async((NodeAction) this::executeApprovalSuspendNode));
            graph.addNode(NODE_ACTION_EXECUTION, node_async((NodeAction) this::executeActionExecutionNode));

            // 2. 串联分析诊断主干
            graph.addEdge(START, NODE_RCA);
            graph.addEdge(NODE_RCA, NODE_SOP);
            graph.addEdge(NODE_SOP, NODE_RISK_AUDIT);

            // 3. 核心条件分支边：高低危精准分流
            graph.addConditionalEdges(
                    NODE_RISK_AUDIT,
                    edge_async((EdgeAction) this::riskRouteAction),
                    Map.of(
                            NODE_APPROVAL_SUSPEND, NODE_APPROVAL_SUSPEND,
                            NODE_ACTION_EXECUTION, NODE_ACTION_EXECUTION
                    )
            );

            // 4. 挂起桩在 4.5 阶段唤醒后流转至受控执行节点
            graph.addEdge(NODE_APPROVAL_SUSPEND, NODE_ACTION_EXECUTION);
            graph.addEdge(NODE_ACTION_EXECUTION, END);

            // 5. 编译：将 interruptAfter 仅锁定在 APPROVAL_SUSPEND_NODE，低危任务零中断！
            return graph.compile(CompileConfig.builder()
                    .saverConfig(SaverConfig.builder().register(redisSaver).build())
                    .interruptAfter(NODE_APPROVAL_SUSPEND)
                    .build());
        } catch (Exception e) {
            log.error("❌ 编译 StateGraph 工作流异常: {}", e.getMessage(), e);
            throw new RuntimeException("StateGraph 编译失败", e);
        }
    }

    /**
     * 条件路由边决策逻辑
     */
    public String riskRouteAction(OverAllState state) {
        SopRecommendationDTO sop = state.value(StateKeys.SOP_RECOMMENDATION, SopRecommendationDTO.class).orElse(null);
        String riskLevel = "LOW";
        if (sop != null && sop.getControlFlow() != null && sop.getControlFlow().getRiskLevel() != null) {
            riskLevel = sop.getControlFlow().getRiskLevel().trim().toUpperCase();
        }

        if ("CRITICAL".equals(riskLevel) || "HIGH".equals(riskLevel)) {
            log.warn("⚠️ [条件路由边] 风险等级评估为 [{}]，精准分流至专职审批挂起桩 APPROVAL_SUSPEND_NODE", riskLevel);
            return NODE_APPROVAL_SUSPEND;
        }

        log.info("🟢 [条件路由边] 风险等级评估为 [{}]，直接分流至自愈执行节点 ACTION_EXECUTION_NODE", riskLevel);
        return NODE_ACTION_EXECUTION;
    }

    /**
     * Node 1: RCA 根因推导节点
     */
    private Map<String, Object> executeRcaNode(OverAllState state) {
        String rackCode = state.value(StateKeys.RACK_CODE, String.class).orElse("RACK-A01");
        String faultSymptom = state.value(StateKeys.FAULT_SYMPTOM, String.class).orElse("过温告警");
        String tenantId = state.value(StateKeys.TENANT_ID, String.class).orElse("000000");
        String traceId = state.value(StateKeys.TRACE_ID, String.class).orElseGet(() -> UUID.randomUUID().toString());

        log.info("📊 [StateGraph::Node1-RCA] 启动 RCA 根因推导, rackCode: {}, traceId: {}", rackCode, traceId);
        RcaReportDTO rcaReport = rcaAgentService.diagnoseRack(rackCode, faultSymptom, tenantId, traceId);

        Map<String, Object> update = new HashMap<>();
        update.put(StateKeys.RCA_REPORT, rcaReport);
        return update;
    }

    /**
     * Node 2: SOP 双通道推荐节点
     */
    private Map<String, Object> executeSopNode(OverAllState state) {
        RcaReportDTO rcaReport = state.value(StateKeys.RCA_REPORT, RcaReportDTO.class).orElse(null);
        String tenantId = state.value(StateKeys.TENANT_ID, String.class).orElse("000000");
        String traceId = state.value(StateKeys.TRACE_ID, String.class).orElseGet(() -> UUID.randomUUID().toString());

        log.info("📋 [StateGraph::Node2-SOP] 启动 SOP 双通道匹配推荐, targetRack: {}, traceId: {}",
                rcaReport != null ? rcaReport.getTargetRack() : "UNKNOWN", traceId);
        SopRecommendationDTO sopRecommendation = sopAgentService.recommendSop(rcaReport, tenantId, traceId);

        Map<String, Object> update = new HashMap<>();
        update.put(StateKeys.SOP_RECOMMENDATION, sopRecommendation);
        return update;
    }

    /**
     * Node 3: 纯策略风控审计节点 (纯计算，绝不在此挂起)
     */
    private Map<String, Object> executeRiskAuditNode(OverAllState state) {
        SopRecommendationDTO sopRec = state.value(StateKeys.SOP_RECOMMENDATION, SopRecommendationDTO.class).orElse(null);
        String riskLevel = "LOW";
        if (sopRec != null && sopRec.getControlFlow() != null && sopRec.getControlFlow().getRiskLevel() != null) {
            riskLevel = sopRec.getControlFlow().getRiskLevel().trim().toUpperCase();
        }

        String decision;
        String status;
        if ("CRITICAL".equals(riskLevel) || "HIGH".equals(riskLevel)) {
            decision = "NEED_APPROVAL";
            status = "PENDING_APPROVAL";
        } else {
            decision = "AUTO_APPROVED";
            status = "AUTO_APPROVED";
        }

        log.info("🛡️ [StateGraph::Node3-RiskAudit] 纯策略风控审计完成, riskLevel: {}, decision: {}, status: {}",
                riskLevel, decision, status);

        Map<String, Object> update = new HashMap<>();
        update.put(StateKeys.RISK_AUDIT_DECISION, decision);
        update.put(StateKeys.STATUS, status);
        return update;
    }

    /**
     * 专职审批挂起桩 (配置 interruptAfter，承接高危停靠并落盘快照)
     */
    private Map<String, Object> executeApprovalSuspendNode(OverAllState state) {
        String traceId = state.value(StateKeys.TRACE_ID, String.class).orElse("N/A");
        String riskDecision = state.value(StateKeys.RISK_AUDIT_DECISION, String.class).orElse("NEED_APPROVAL");
        log.warn("⚠️ [StateGraph::ApprovalSuspendNode] 拦截到高危操作，当前流程进入挂起桩！traceId: {}, decision: {}", traceId, riskDecision);

        Map<String, Object> update = new HashMap<>();
        update.put(StateKeys.STATUS, "HITL_SUSPENDED");
        return update;
    }

    /**
     * Node 4: 动作受控执行桩 (具备幂等防重、主管驳回拦截与参数覆盖能力)
     */
    private Map<String, Object> executeActionExecutionNode(OverAllState state) {
        String traceId = state.value(StateKeys.TRACE_ID, String.class).orElse("N/A");
        String status = state.value(StateKeys.STATUS, String.class).orElse("AUTO_APPROVED");
        String approvalDecision = state.value(StateKeys.APPROVAL_DECISION, String.class).orElse("APPROVE");
        SopRecommendationDTO sop = state.value(StateKeys.SOP_RECOMMENDATION, SopRecommendationDTO.class).orElse(null);

        // 1. 防御分支：主管驳回 (REJECT) 逻辑
        if ("REJECT".equalsIgnoreCase(approvalDecision)) {
            log.warn("🛑 [StateGraph::Node4] 主管已驳回该高危倒闸操作，安全跳过硬件下发，工单作废！traceId: {}", traceId);
            ActionExecutionResultDTO rejectResult = new ActionExecutionResultDTO();
            rejectResult.setExecutionId("REJECT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            rejectResult.setTicketId(System.currentTimeMillis() % 1000000L);
            rejectResult.setActionName("REJECTED_BY_SUPERVISOR");
            rejectResult.setTargetDevice(state.value(StateKeys.RACK_CODE, String.class).orElse("UNKNOWN"));
            rejectResult.setExecutionStatus("REJECTED");
            rejectResult.setExecutedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            rejectResult.setReceiptMessage("[主管已驳回] 操作未通过审批，倒闸动作已取消，工单置为已作废。");

            Map<String, Object> update = new HashMap<>();
            update.put(StateKeys.EXECUTION_RESULT, rejectResult);
            update.put(StateKeys.STATUS, "REJECTED");
            return update;
        }

        // 2. 幂等性防御：检查是否已有执行成功的回执
        Optional<ActionExecutionResultDTO> existingResult = state.value(StateKeys.EXECUTION_RESULT, ActionExecutionResultDTO.class);
        if (existingResult.isPresent() && "SUCCESS".equals(existingResult.get().getExecutionStatus())) {
            log.info("🔁 [StateGraph::ActionExecutionNode] 检测到已存在成功执行回执，跳过重复执行 (幂等防护), traceId: {}", traceId);
            return Map.of();
        }

        log.info("⚡ [StateGraph::ActionExecutionNode] 进入受控执行逻辑, status: {}, traceId: {}", status, traceId);

        // 3. 正常执行：优先读取主管传入的 overrideParams
        @SuppressWarnings("unchecked")
        Map<String, Object> overrideParams = (Map<String, Object>) state.value(StateKeys.OVERRIDE_PARAMS, Map.class).orElse(null);

        String actionName;
        String targetDevice;

        if (sop != null && sop.getControlFlow() != null) {
            actionName = sop.getControlFlow().getActionName();
            targetDevice = sop.getControlFlow().getTargetDevice();
        } else {
            actionName = "ADJUST_FAN_SPEED";
            targetDevice = state.value(StateKeys.RACK_CODE, String.class).orElse("UNKNOWN");
        }

        if (overrideParams != null && overrideParams.containsKey("target_device")) {
            targetDevice = String.valueOf(overrideParams.get("target_device"));
            log.info("✏️ [StateGraph::Node4] 应用主管覆盖参数 targetDevice: {}", targetDevice);
        }

        String receiptMessage;
        if ("HITL_SUSPENDED".equals(status) || "PENDING_APPROVAL".equals(status)) {
            // 时刻 B: 主管审批后恢复执行
            receiptMessage = String.format("[主管核准执行] 设备 %s 已完成高危倒闸动作: %s，系统指标回稳", targetDevice, actionName);
        } else {
            // 时刻 A: 低危自动自愈下发
            receiptMessage = String.format("[低危自愈闭环] 设备 %s 已自动调节参数动作: %s，巡检工单已自动报备", targetDevice, actionName);
        }

        ActionExecutionResultDTO executionResult = new ActionExecutionResultDTO();
        executionResult.setExecutionId("EXEC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        executionResult.setTicketId(System.currentTimeMillis() % 1000000L);
        executionResult.setActionName(actionName);
        executionResult.setTargetDevice(targetDevice);
        executionResult.setExecutionStatus("SUCCESS");
        executionResult.setExecutedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        executionResult.setReceiptMessage(receiptMessage);

        Map<String, Object> update = new HashMap<>();
        update.put(StateKeys.EXECUTION_RESULT, executionResult);
        update.put(StateKeys.STATUS, "COMPLETED");
        return update;
    }

    /**
     * 唤醒挂起的工作流继续走完 Node 4 (对标 implementation_plan4.5.md 任务 3.3)
     *
     * @param traceId        工作流 ThreadId
     * @param decision       主管决策 (APPROVE / REJECT)
     * @param comment        主管审批批注
     * @param overrideParams 可选覆盖参数
     * @return 最终恢复执行后的 State
     */
    public OverAllState resumeWorkflow(String traceId, String decision, String comment, Map<String, Object> overrideParams) {
        log.info("▶️ [StateGraph::Resume] 收到唤醒请求, traceId: {}, decision: {}", traceId, decision);
        RunnableConfig resumeConfig = RunnableConfig.builder().threadId(traceId).build();

        try {
            // 1. 更新注入主管审批决策与参数至 Checkpoint
            Map<String, Object> resumeInput = new HashMap<>();
            resumeInput.put(StateKeys.APPROVAL_DECISION, decision);
            if (comment != null) {
                resumeInput.put(StateKeys.APPROVAL_COMMENT, comment);
            }
            if (overrideParams != null) {
                resumeInput.put(StateKeys.OVERRIDE_PARAMS, overrideParams);
            }

            // 2. 核心唤醒：调用 3 参数 updateState 写入决策并指定当前挂起节点 NODE_APPROVAL_SUSPEND
            RunnableConfig updatedConfig = workflowGraph.updateState(resumeConfig, resumeInput, NODE_APPROVAL_SUSPEND);
            return workflowGraph.invoke((Map<String, Object>) null, updatedConfig)
                    .orElseThrow(() -> new RuntimeException("StateGraph 恢复执行未返回最终状态"));
        } catch (Exception e) {
            log.error("❌ 唤醒 StateGraph 工作流异常, traceId: {}", traceId, e);
            throw new RuntimeException("StateGraph 唤醒执行异常: " + e.getMessage(), e);
        }
    }

    /**
     * 触发执行完整 AIOps 排障流程
     */
    public OverAllState runWorkflow(String rackCode, String faultSymptom, String tenantId, String traceId) {
        Map<String, Object> initialInput = new HashMap<>();
        initialInput.put(StateKeys.RACK_CODE, rackCode);
        initialInput.put(StateKeys.FAULT_SYMPTOM, faultSymptom);
        initialInput.put(StateKeys.TENANT_ID, tenantId);
        initialInput.put(StateKeys.TRACE_ID, traceId);

        RunnableConfig config = RunnableConfig.builder().threadId(traceId).build();

        try {
            return workflowGraph.invoke(initialInput, config)
                    .orElseThrow(() -> new RuntimeException("StateGraph 执行未返回最终状态"));
        } catch (Exception e) {
            log.error("❌ 执行 StateGraph 工作流异常, rackCode: {}, traceId: {}", rackCode, traceId, e);
            throw new RuntimeException("StateGraph 执行异常: " + e.getMessage(), e);
        }
    }

    public OverAllState runWorkflow(String rackCode, String faultSymptom) {
        String traceId = "flow-trace-" + UUID.randomUUID().toString().substring(0, 8);
        return runWorkflow(rackCode, faultSymptom, "000000", traceId);
    }
}
