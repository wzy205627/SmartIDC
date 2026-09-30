package com.smartidc.aiops.graph;

/**
 * StateGraph 全局状态键常量契约 (对标 implementation_plan4.4.md 任务 1.1)
 * 集中管理工作流所有 Key，规避硬编码字符串
 */
public final class StateKeys {
    private StateKeys() {}

    /** 原始告警输入上下文 DTO / Map */
    public static final String ALARM_INPUT = "alarm_input";
    /** 机柜编码 */
    public static final String RACK_CODE = "rack_code";
    /** 故障表象 */
    public static final String FAULT_SYMPTOM = "fault_symptom";
    /** 租户标识 */
    public static final String TENANT_ID = "tenant_id";
    /** 全链路追踪 ID / Thread ID */
    public static final String TRACE_ID = "trace_id";
    /** Node 1 产出: RCA 根因诊断报告 */
    public static final String RCA_REPORT = "rca_report";
    /** Node 2 产出: SOP 双通道推荐预案 */
    public static final String SOP_RECOMMENDATION = "sop_recommendation";
    /** Node 3 产出: 风险风控审计决策 (AUTO_APPROVED / NEED_APPROVAL) */
    public static final String RISK_AUDIT_DECISION = "risk_audit_decision";
    /** 全局生命周期状态 (RUNNING / PENDING_APPROVAL / HITL_SUSPENDED / COMPLETED) */
    public static final String STATUS = "status";
    /** Node 4 产出: 下发受控执行回执与工单号 */
    public static final String EXECUTION_RESULT = "execution_result";
    /** 主管审批决策 (APPROVE / REJECT) */
    public static final String APPROVAL_DECISION = "approval_decision";
    /** 主管审批批注 */
    public static final String APPROVAL_COMMENT = "approval_comment";
    /** 主管覆盖参数 Map */
    public static final String OVERRIDE_PARAMS = "override_params";
}
