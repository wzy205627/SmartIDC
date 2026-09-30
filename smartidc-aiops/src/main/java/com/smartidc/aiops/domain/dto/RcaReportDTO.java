package com.smartidc.aiops.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * RCA 根因推导报告 DTO (对标 implementation_plan4.3.md 任务 1.1)
 */
@Schema(description = "RCA 根因推导诊断报告")
public class RcaReportDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "故障标识ID", example = "FAULT-A01-OVERHEAT-001")
    @JsonProperty("fault_id")
    @JsonAlias({"faultId", "fault_id"})
    private String faultId;

    @Schema(description = "目标机柜编码", example = "RACK-A01")
    @JsonProperty("target_rack")
    @JsonAlias({"targetRack", "target_rack"})
    private String targetRack;

    @Schema(description = "根因类型", example = "COOLING_FAILURE")
    @JsonProperty("root_cause_type")
    @JsonAlias({"rootCauseType", "root_cause_type"})
    private String rootCauseType;

    @Schema(description = "根因详细摘要", example = "A01机柜由于相邻冷机跳闸导致送风受阻，回风温度持续攀升")
    @JsonProperty("root_cause_summary")
    @JsonAlias({"rootCauseSummary", "root_cause_summary"})
    private String rootCauseSummary;

    @Schema(description = "受波及关联机柜列表")
    @JsonProperty("affected_racks")
    @JsonAlias({"affectedRacks", "affected_racks"})
    private List<String> affectedRacks = new ArrayList<>();

    @Schema(description = "诊断置信度 (0.0~1.0)", example = "0.95")
    @JsonProperty("confidence")
    @JsonAlias({"confidence"})
    private Double confidence;

    @Schema(description = "证据链条")
    @JsonProperty("evidence_chain")
    @JsonAlias({"evidenceChain", "evidence_chain"})
    private List<String> evidenceChain = new ArrayList<>();

    public RcaReportDTO() {
    }

    public RcaReportDTO(String faultId, String targetRack, String rootCauseType, String rootCauseSummary,
                        List<String> affectedRacks, Double confidence, List<String> evidenceChain) {
        this.faultId = faultId;
        this.targetRack = targetRack;
        this.rootCauseType = rootCauseType;
        this.rootCauseSummary = rootCauseSummary;
        this.affectedRacks = affectedRacks;
        this.confidence = confidence;
        this.evidenceChain = evidenceChain;
    }

    public String getFaultId() {
        return faultId;
    }

    public void setFaultId(String faultId) {
        this.faultId = faultId;
    }

    public String getTargetRack() {
        return targetRack;
    }

    public void setTargetRack(String targetRack) {
        this.targetRack = targetRack;
    }

    public String getRootCauseType() {
        return rootCauseType;
    }

    public void setRootCauseType(String rootCauseType) {
        this.rootCauseType = rootCauseType;
    }

    public String getRootCauseSummary() {
        return rootCauseSummary;
    }

    public void setRootCauseSummary(String rootCauseSummary) {
        this.rootCauseSummary = rootCauseSummary;
    }

    public List<String> getAffectedRacks() {
        return affectedRacks;
    }

    public void setAffectedRacks(List<String> affectedRacks) {
        this.affectedRacks = affectedRacks;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public List<String> getEvidenceChain() {
        return evidenceChain;
    }

    public void setEvidenceChain(List<String> evidenceChain) {
        this.evidenceChain = evidenceChain;
    }

    @Override
    public String toString() {
        return "RcaReportDTO{" +
                "faultId='" + faultId + '\'' +
                ", targetRack='" + targetRack + '\'' +
                ", rootCauseType='" + rootCauseType + '\'' +
                ", rootCauseSummary='" + rootCauseSummary + '\'' +
                ", affectedRacks=" + affectedRacks +
                ", confidence=" + confidence +
                ", evidenceChain=" + evidenceChain +
                '}';
    }
}
