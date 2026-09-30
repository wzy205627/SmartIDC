package com.smartidc.biz.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

/**
 * 运维工单全生命周期画像视图对象
 */
@Schema(description = "运维工单全生命周期画像视图对象")
public class WorkTicketDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "工单ID", example = "1")
    private Long ticketId;

    @Schema(description = "工单流水号", example = "TK-20260919-001")
    private String ticketNo;

    @Schema(description = "租户ID", example = "000000")
    private String tenantId;

    @Schema(description = "关联告警事件ID", example = "17")
    private Long alarmId;

    @Schema(description = "关联机柜ID", example = "3")
    private Long rackId;

    @Schema(description = "机柜编号", example = "A-03")
    private String rackCode;

    @Schema(description = "机房区域名称", example = "华东01-A区")
    private String roomName;

    @Schema(description = "工单标题", example = "[紧急排障] A-03 机柜高温越限现场排查")
    private String title;

    @Schema(description = "工单类型", example = "ALARM_REPAIR")
    private String ticketType;

    @Schema(description = "状态: 0-待分配, 1-已指派, 2-排障中, 6-待复核, 7-已办结", example = "1")
    private Integer status;

    @Schema(description = "状态中文标识", example = "已指派现场处理")
    private String statusLabel;

    @Schema(description = "当前指派工程师ID", example = "101")
    private Long operatorId;

    @Schema(description = "当前指派工程师姓名", example = "张工")
    private String operatorName;

    @Schema(description = "审批主管ID", example = "1")
    private Long approverId;

    @Schema(description = "审批主管姓名", example = "李主管")
    private String approverName;

    @Schema(description = "AIOps 检查点快照ID", example = "chk_123")
    private String checkpointId;

    @Schema(description = "排障 SOP 指导建议")
    private String sopGuide;

    @Schema(description = "现场处置记录与排障反馈")
    private String processNotes;

    @Schema(description = "创建时间", example = "2026-09-19 14:20:00")
    private String createTime;

    @Schema(description = "更新时间", example = "2026-09-19 14:25:00")
    private String updateTime;

    @Schema(description = "办结时间", example = "2026-09-19 14:40:00")
    private String finishTime;

    @Schema(description = "关联告警等级", example = "CRITICAL")
    private String alarmLevel;

    @Schema(description = "关联告警类型", example = "TEMP_HIGH")
    private String alarmType;

    @Schema(description = "关联告警越限采样值", example = "38.80℃")
    private String alarmMetricValue;

    public WorkTicketDetailVO() {
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getTicketNo() {
        return ticketNo;
    }

    public void setTicketNo(String ticketNo) {
        this.ticketNo = ticketNo;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Long getAlarmId() {
        return alarmId;
    }

    public void setAlarmId(Long alarmId) {
        this.alarmId = alarmId;
    }

    public Long getRackId() {
        return rackId;
    }

    public void setRackId(Long rackId) {
        this.rackId = rackId;
    }

    public String getRackCode() {
        return rackCode;
    }

    public void setRackCode(String rackCode) {
        this.rackCode = rackCode;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
    }

    public String getApproverName() {
        return approverName;
    }

    public void setApproverName(String approverName) {
        this.approverName = approverName;
    }

    public String getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(String checkpointId) {
        this.checkpointId = checkpointId;
    }

    public String getSopGuide() {
        return sopGuide;
    }

    public void setSopGuide(String sopGuide) {
        this.sopGuide = sopGuide;
    }

    public String getProcessNotes() {
        return processNotes;
    }

    public void setProcessNotes(String processNotes) {
        this.processNotes = processNotes;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public String getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(String finishTime) {
        this.finishTime = finishTime;
    }

    public String getAlarmLevel() {
        return alarmLevel;
    }

    public void setAlarmLevel(String alarmLevel) {
        this.alarmLevel = alarmLevel;
    }

    public String getAlarmType() {
        return alarmType;
    }

    public void setAlarmType(String alarmType) {
        this.alarmType = alarmType;
    }

    public String getAlarmMetricValue() {
        return alarmMetricValue;
    }

    public void setAlarmMetricValue(String alarmMetricValue) {
        this.alarmMetricValue = alarmMetricValue;
    }
}
