package com.smartidc.aiops.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

/**
 * 动作执行回执契约 DTO (对标 implementation_plan4.4.md 任务 1.2)
 * 记录 Node 4 (Action_Execution_Node) 的受控执行结果与流水回执
 */
@Schema(description = "动作受控执行回执")
public class ActionExecutionResultDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "唯一执行流水号", example = "EXEC-7A1B2C3D")
    @JsonProperty("execution_id")
    @JsonAlias({"executionId", "execution_id"})
    private String executionId;

    @Schema(description = "关联的运维工单编号", example = "30041")
    @JsonProperty("ticket_id")
    @JsonAlias({"ticketId", "ticket_id"})
    private Long ticketId;

    @Schema(description = "执行动作编码", example = "ADJUST_FAN_SPEED")
    @JsonProperty("action_name")
    @JsonAlias({"actionName", "action_name"})
    private String actionName;

    @Schema(description = "目标设备编码", example = "FAN-CAB-01")
    @JsonProperty("target_device")
    @JsonAlias({"targetDevice", "target_device"})
    private String targetDevice;

    @Schema(description = "执行状态 (SUCCESS / FAILED / SIMULATED_ENGAGED)", example = "SUCCESS")
    @JsonProperty("execution_status")
    @JsonAlias({"executionStatus", "execution_status"})
    private String executionStatus;

    @Schema(description = "执行时间戳 (ISO格式)", example = "2026-09-21T19:30:00")
    @JsonProperty("executed_at")
    @JsonAlias({"executedAt", "executed_at"})
    private String executedAt;

    @Schema(description = "详细执行回执消息", example = "[自愈成功] 冷通道天窗风机转速已上调至 80%，出风温度下降至 24.5℃")
    @JsonProperty("receipt_message")
    @JsonAlias({"receiptMessage", "receipt_message"})
    private String receiptMessage;

    public ActionExecutionResultDTO() {
    }

    public ActionExecutionResultDTO(String executionId, Long ticketId, String actionName, String targetDevice,
                                    String executionStatus, String executedAt, String receiptMessage) {
        this.executionId = executionId;
        this.ticketId = ticketId;
        this.actionName = actionName;
        this.targetDevice = targetDevice;
        this.executionStatus = executionStatus;
        this.executedAt = executedAt;
        this.receiptMessage = receiptMessage;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getActionName() {
        return actionName;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    public String getTargetDevice() {
        return targetDevice;
    }

    public void setTargetDevice(String targetDevice) {
        this.targetDevice = targetDevice;
    }

    public String getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(String executionStatus) {
        this.executionStatus = executionStatus;
    }

    public String getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(String executedAt) {
        this.executedAt = executedAt;
    }

    public String getReceiptMessage() {
        return receiptMessage;
    }

    public void setReceiptMessage(String receiptMessage) {
        this.receiptMessage = receiptMessage;
    }

    @Override
    public String toString() {
        return "ActionExecutionResultDTO{" +
                "executionId='" + executionId + '\'' +
                ", ticketId=" + ticketId +
                ", actionName='" + actionName + '\'' +
                ", targetDevice='" + targetDevice + '\'' +
                ", executionStatus='" + executionStatus + '\'' +
                ", executedAt='" + executedAt + '\'' +
                ", receiptMessage='" + receiptMessage + '\'' +
                '}';
    }
}
