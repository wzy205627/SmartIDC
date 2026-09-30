package com.smartidc.biz.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建运维排障工单数据传输对象
 */
@Schema(description = "创建运维排障工单 DTO")
public class CreateWorkTicketDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "关联告警事件ID (若从告警生成)", example = "17")
    private Long alarmId;

    @Schema(description = "关联机架编码", example = "A-03")
    private String rackCode;

    @Schema(description = "工单标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "[紧急排障] A-03 机柜高温越限现场排查")
    private String title;

    @Schema(description = "工单类型: ALARM_REPAIR, ROUTINE_CHECK, ASSET_MOVE", example = "ALARM_REPAIR")
    private String ticketType = "ALARM_REPAIR";

    @Schema(description = "指派运维工程师姓名", example = "张工")
    private String operatorName = "张工";

    @Schema(description = "推荐应急 SOP 排障指引")
    private String sopGuide;

    @Schema(description = "租户ID", example = "000000")
    private String tenantId;

    public CreateWorkTicketDTO() {
    }

    public Long getAlarmId() {
        return alarmId;
    }

    public void setAlarmId(Long alarmId) {
        this.alarmId = alarmId;
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

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
