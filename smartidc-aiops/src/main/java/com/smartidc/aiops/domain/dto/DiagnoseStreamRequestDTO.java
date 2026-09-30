package com.smartidc.aiops.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.io.Serial;
import java.io.Serializable;

/**
 * 排障流式启动请求 DTO (对标 implementation_plan4.5.md 任务 1.1)
 */
@Schema(description = "排障流式启动请求")
public class DiagnoseStreamRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "目标机柜编码", example = "RACK-A01", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "机柜编码不能为空")
    private String rackCode;

    @Schema(description = "故障表象描述", example = "精密空调压缩机跳闸过温告警", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "故障表象描述不能为空")
    private String faultSymptom;

    @Schema(description = "多租户隔离 ID", example = "000000", defaultValue = "000000")
    private String tenantId = "000000";

    @Schema(description = "全链路追踪号", example = "trace-20240521-001")
    private String traceId;

    public DiagnoseStreamRequestDTO() {
    }

    public DiagnoseStreamRequestDTO(String rackCode, String faultSymptom) {
        this.rackCode = rackCode;
        this.faultSymptom = faultSymptom;
    }

    public DiagnoseStreamRequestDTO(String rackCode, String faultSymptom, String tenantId, String traceId) {
        this.rackCode = rackCode;
        this.faultSymptom = faultSymptom;
        this.tenantId = (tenantId != null && !tenantId.isBlank()) ? tenantId : "000000";
        this.traceId = traceId;
    }

    public String getRackCode() {
        return rackCode;
    }

    public void setRackCode(String rackCode) {
        this.rackCode = rackCode;
    }

    public String getFaultSymptom() {
        return faultSymptom;
    }

    public void setFaultSymptom(String faultSymptom) {
        this.faultSymptom = faultSymptom;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    @Override
    public String toString() {
        return "DiagnoseStreamRequestDTO{" +
                "rackCode='" + rackCode + '\'' +
                ", faultSymptom='" + faultSymptom + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", traceId='" + traceId + '\'' +
                '}';
    }
}
