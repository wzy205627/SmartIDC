package com.smartidc.aiops.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 主管在线决策与恢复请求 DTO (对标 implementation_plan4.5.md 任务 1.2)
 */
@Schema(description = "主管在线决策与恢复请求")
public class ResumeActionRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "关联的运维工单编号", example = "30041", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "工单编号不能为空")
    private Long ticketId;

    @Schema(description = "工作流线程/追踪 ID", example = "trace-20240521-001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "追踪号不能为空")
    private String traceId;

    @Schema(description = "决策类型 (APPROVE / REJECT)", example = "APPROVE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "审批决策不能为空")
    private String decision;

    @Schema(description = "主管审批批注", example = "现场已核实 CRAC-A-02 处于冷备就绪，同意倒闸")
    private String approvalComment;

    @Schema(description = "审批人用户名", example = "supervisor_zhang")
    private String approverUser;

    @Schema(description = "可选参数覆盖 Map")
    private Map<String, Object> overrideParams = new HashMap<>();

    public ResumeActionRequestDTO() {
    }

    public ResumeActionRequestDTO(Long ticketId, String traceId, String decision) {
        this.ticketId = ticketId;
        this.traceId = traceId;
        this.decision = decision;
    }

    public ResumeActionRequestDTO(Long ticketId, String traceId, String decision, String approvalComment, String approverUser, Map<String, Object> overrideParams) {
        this.ticketId = ticketId;
        this.traceId = traceId;
        this.decision = decision;
        this.approvalComment = approvalComment;
        this.approverUser = approverUser;
        this.overrideParams = (overrideParams != null) ? overrideParams : new HashMap<>();
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getApprovalComment() {
        return approvalComment;
    }

    public void setApprovalComment(String approvalComment) {
        this.approvalComment = approvalComment;
    }

    public String getApproverUser() {
        return approverUser;
    }

    public void setApproverUser(String approverUser) {
        this.approverUser = approverUser;
    }

    public Map<String, Object> getOverrideParams() {
        return overrideParams;
    }

    public void setOverrideParams(Map<String, Object> overrideParams) {
        this.overrideParams = overrideParams;
    }

    @Override
    public String toString() {
        return "ResumeActionRequestDTO{" +
                "ticketId=" + ticketId +
                ", traceId='" + traceId + '\'' +
                ", decision='" + decision + '\'' +
                ", approvalComment='" + approvalComment + '\'' +
                ", approverUser='" + approverUser + '\'' +
                ", overrideParams=" + overrideParams +
                '}';
    }
}
