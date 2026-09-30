package com.smartidc.biz.domain.vo.mobile;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 移动随行端工单全貌详情 VO (纯 Java 规范)
 */
@Schema(description = "移动随行端工单全貌详情视图")
public class MobileTicketDetailVO {

    @Schema(description = "工单ID")
    private Long ticketId;

    @Schema(description = "工单流水编号")
    private String ticketNo;

    @Schema(description = "归属租户ID")
    private String tenantId;

    @Schema(description = "工单标题")
    private String title;

    @Schema(description = "工单类型: ALARM_REPAIR, ROUTINE_CHECK")
    private String ticketType;

    @Schema(description = "状态代码: 0-待分配, 1-已指派, 2-排障中, 3-挂起, 4-已批准, 5-已拒绝, 6-待复核, 7-已办结")
    private Integer status;

    @Schema(description = "状态文本")
    private String statusText;

    @Schema(description = "关联机柜ID")
    private Long rackId;

    @Schema(description = "关联机柜编码")
    private String rackCode;

    @Schema(description = "所属机房名称")
    private String roomName;

    @Schema(description = "机架权威物理位置拓扑")
    private String rackLocation;

    @Schema(description = "关联告警ID")
    private Long alarmId;

    @Schema(description = "告警等级: WARNING, ERROR, CRITICAL")
    private String alarmLevel;

    @Schema(description = "告警类型")
    private String alarmType;

    @Schema(description = "当前责任工程师ID")
    private Long operatorId;

    @Schema(description = "当前责任工程师姓名")
    private String operatorName;

    @Schema(description = "排障 SOP 建议")
    private String sopGuide;

    @Schema(description = "现场处置记录与故障排查说明")
    private String processNotes;

    @Schema(description = "MinIO 现场存证对象 Key")
    private String evidenceObjectKey;

    @Schema(description = "存证照片可访问回显 URL")
    private String evidenceViewUrl;

    @Schema(description = "存证照片 SHA-256 哈希防篡改指纹")
    private String evidenceHash;

    @Schema(description = "服务端权威消警时间戳")
    private LocalDateTime resolveTime;

    @Schema(description = "工单办结时间")
    private LocalDateTime finishTime;

    @Schema(description = "工单创建时间")
    private LocalDateTime createTime;

    @Schema(description = "机柜当前实时进风温度 (℃)")
    private BigDecimal telemetryTemp;

    @Schema(description = "动环指标是否处于安全稳定回温区间 (<=35.0℃)")
    private Boolean isDebounceStable;

    public MobileTicketDetailVO() {
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

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
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

    public String getRackLocation() {
        return rackLocation;
    }

    public void setRackLocation(String rackLocation) {
        this.rackLocation = rackLocation;
    }

    public Long getAlarmId() {
        return alarmId;
    }

    public void setAlarmId(Long alarmId) {
        this.alarmId = alarmId;
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

    public String getEvidenceObjectKey() {
        return evidenceObjectKey;
    }

    public void setEvidenceObjectKey(String evidenceObjectKey) {
        this.evidenceObjectKey = evidenceObjectKey;
    }

    public String getEvidenceViewUrl() {
        return evidenceViewUrl;
    }

    public void setEvidenceViewUrl(String evidenceViewUrl) {
        this.evidenceViewUrl = evidenceViewUrl;
    }

    public String getEvidenceHash() {
        return evidenceHash;
    }

    public void setEvidenceHash(String evidenceHash) {
        this.evidenceHash = evidenceHash;
    }

    public LocalDateTime getResolveTime() {
        return resolveTime;
    }

    public void setResolveTime(LocalDateTime resolveTime) {
        this.resolveTime = resolveTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public BigDecimal getTelemetryTemp() {
        return telemetryTemp;
    }

    public void setTelemetryTemp(BigDecimal telemetryTemp) {
        this.telemetryTemp = telemetryTemp;
    }

    public Boolean getIsDebounceStable() {
        return isDebounceStable;
    }

    public void setIsDebounceStable(Boolean isDebounceStable) {
        this.isDebounceStable = isDebounceStable;
    }
}
