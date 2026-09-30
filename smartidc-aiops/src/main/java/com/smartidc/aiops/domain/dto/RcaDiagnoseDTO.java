package com.smartidc.aiops.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

/**
 * AIOps 智能诊断请求 DTO (对标 SDS.md 5.2 节)
 */
@Schema(description = "AIOps 智能诊断请求参数")
public class RcaDiagnoseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "告警事件主键ID", example = "9021")
    private Long alarmId;

    @Schema(description = "运维人员提问或现场描述", example = "机柜 A-03 发生过温告警，请帮我分析根因并给出紧急处理建议。")
    private String userQuery;

    public RcaDiagnoseDTO() {
    }

    public Long getAlarmId() {
        return alarmId;
    }

    public void setAlarmId(Long alarmId) {
        this.alarmId = alarmId;
    }

    public String getUserQuery() {
        return userQuery;
    }

    public void setUserQuery(String userQuery) {
        this.userQuery = userQuery;
    }
}
