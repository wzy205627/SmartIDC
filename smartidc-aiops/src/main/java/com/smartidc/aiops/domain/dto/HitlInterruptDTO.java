package com.smartidc.aiops.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人机在环 (HITL) 流程挂起与审批事件 DTO (对标 SDS.md 5.2 节)
 */
@Schema(description = "HITL 人机在环中断挂起事件")
public class HitlInterruptDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "流程状态", example = "SUSPENDED")
    private String status;

    @Schema(description = "关联的运维工单ID", example = "30041")
    private Long ticketId;

    @Schema(description = "Spring AI Alibaba Graph 运行时状态快照ID", example = "chk_idc_7a1b9f")
    private String checkpointId;

    @Schema(description = "大模型给出的建议控制动作", example = "建议调用备用冷机回路并临时限制机柜 A-03 非核心负载")
    private String recommendedAction;

    @Schema(description = "需要审批的角色", example = "ROLE_IDC_SUPERVISOR")
    private String approverRole;

    @Schema(description = "摘要说明", example = "该操作涉及冷机启停控制与配电参数下发，流程已自动挂起，已推送审批卡片至值班主管。")
    private String summary;

    public HitlInterruptDTO() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(String checkpointId) {
        this.checkpointId = checkpointId;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public String getApproverRole() {
        return approverRole;
    }

    public void setApproverRole(String approverRole) {
        this.approverRole = approverRole;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
