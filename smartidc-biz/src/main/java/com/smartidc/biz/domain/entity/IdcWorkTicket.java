package com.smartidc.biz.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 运维排障与 AIOps 智能工单实体 (idc_work_ticket)
 */
@TableName("idc_work_ticket")
@Schema(description = "运维排障与 AIOps 智能工单实体")
public class IdcWorkTicket implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "工单ID", example = "1")
    private Long ticketId;

    @Schema(description = "工单流水编号", example = "TK-20260919-001")
    private String ticketNo;

    @Schema(description = "租户ID", example = "000000")
    private String tenantId;

    @Schema(description = "关联的告警事件ID", example = "17")
    private Long alarmId;

    @Schema(description = "关联机柜ID", example = "3")
    private Long rackId;

    @Schema(description = "关联机柜编码", example = "A-03")
    private String rackCode;

    @Schema(description = "工单标题", example = "[紧急排障] A-03 机柜高温越限现场排查")
    private String title;

    @Schema(description = "工单类型: ALARM_REPAIR(故障排障), ROUTINE_CHECK(常规巡检), ASSET_MOVE(资产移机)", example = "ALARM_REPAIR")
    private String ticketType;

    @Schema(description = "状态: 0-待分配, 1-已指派, 2-排障中, 3-挂起待审批, 4-已批准, 5-已拒绝, 6-已解决待复核, 7-已办结", example = "1")
    private Integer status;

    @Schema(description = "当前指派运维工程师ID", example = "101")
    private Long operatorId;

    @Schema(description = "当前指派运维工程师姓名", example = "张工")
    private String operatorName;

    @Schema(description = "审批主管ID", example = "1")
    private Long approverId;

    @Schema(description = "审批主管姓名", example = "李主管")
    private String approverName;

    @Schema(description = "Spring AI Alibaba Graph 运行时状态快照ID", example = "chk_987654")
    private String checkpointId;

    @Schema(description = "排障 SOP 指导建议")
    private String sopGuide;

    @Schema(description = "现场处置记录与排障反馈")
    private String processNotes;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "办结时间")
    private LocalDateTime finishTime;

    @Schema(description = "MinIO 照片对象存储 Key")
    private String evidenceObjectKey;

    @Schema(description = "存证照片 SHA-256 哈希防篡改指纹")
    private String evidenceHash;

    @Schema(description = "机架权威物理位置拓扑")
    private String rackLocation;

    @Schema(description = "服务端消警权威时间戳")
    private LocalDateTime resolveTime;

    public IdcWorkTicket() {
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    public String getEvidenceObjectKey() {
        return evidenceObjectKey;
    }

    public void setEvidenceObjectKey(String evidenceObjectKey) {
        this.evidenceObjectKey = evidenceObjectKey;
    }

    public String getEvidenceHash() {
        return evidenceHash;
    }

    public void setEvidenceHash(String evidenceHash) {
        this.evidenceHash = evidenceHash;
    }

    public String getRackLocation() {
        return rackLocation;
    }

    public void setRackLocation(String rackLocation) {
        this.rackLocation = rackLocation;
    }

    public LocalDateTime getResolveTime() {
        return resolveTime;
    }

    public void setResolveTime(LocalDateTime resolveTime) {
        this.resolveTime = resolveTime;
    }
}
