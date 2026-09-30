package com.smartidc.aiops.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * SOP 双通道推荐决策 DTO (对标 implementation_plan4.3.md 任务 1.1)
 * 包含机器控制通道 (control_flow) 与人类高保真展示通道 (display_view)
 */
@Schema(description = "SOP 应急处置双通道推荐决策")
public class SopRecommendationDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "机器控制流契约")
    @JsonProperty("control_flow")
    @JsonAlias({"controlFlow", "control_flow"})
    private ControlFlowDTO controlFlow;

    @Schema(description = "人类高保真 Markdown 渲染视图")
    @JsonProperty("display_view")
    @JsonAlias({"displayView", "display_view"})
    private String displayView;

    public SopRecommendationDTO() {
    }

    public SopRecommendationDTO(ControlFlowDTO controlFlow, String displayView) {
        this.controlFlow = controlFlow;
        this.displayView = displayView;
    }

    public ControlFlowDTO getControlFlow() {
        return controlFlow;
    }

    public void setControlFlow(ControlFlowDTO controlFlow) {
        this.controlFlow = controlFlow;
    }

    public String getDisplayView() {
        return displayView;
    }

    public void setDisplayView(String displayView) {
        this.displayView = displayView;
    }

    /**
     * 机器控制流 DTO
     */
    @Schema(description = "机器控制流决策")
    public static class ControlFlowDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "是否需要人工审批", example = "true")
        @JsonProperty("need_human_approval")
        @JsonAlias({"needHumanApproval", "need_human_approval"})
        private Boolean needHumanApproval;

        @Schema(description = "风险等级 (CRITICAL / HIGH / LOW / READ_ONLY)", example = "CRITICAL")
        @JsonProperty("risk_level")
        @JsonAlias({"riskLevel", "risk_level"})
        private String riskLevel;

        @Schema(description = "SOP 规程编码", example = "SOP-COOLING-SWITCH-01")
        @JsonProperty("sop_code")
        @JsonAlias({"sopCode", "sop_code"})
        private String sopCode;

        @Schema(description = "具体动作编码", example = "SWITCH_TO_BACKUP_CIRCUIT")
        @JsonProperty("action_name")
        @JsonAlias({"actionName", "action_name"})
        private String actionName;

        @Schema(description = "目标设备标识", example = "AC-PRECISION-01")
        @JsonProperty("target_device")
        @JsonAlias({"targetDevice", "target_device"})
        private String targetDevice;

        @Schema(description = "动作执行参数")
        @JsonProperty("parameters")
        private Map<String, Object> parameters = new HashMap<>();

        public ControlFlowDTO() {
        }

        public ControlFlowDTO(Boolean needHumanApproval, String riskLevel, String sopCode,
                              String actionName, String targetDevice, Map<String, Object> parameters) {
            this.needHumanApproval = needHumanApproval;
            this.riskLevel = riskLevel;
            this.sopCode = sopCode;
            this.actionName = actionName;
            this.targetDevice = targetDevice;
            this.parameters = parameters;
        }

        public Boolean getNeedHumanApproval() {
            return needHumanApproval;
        }

        public void setNeedHumanApproval(Boolean needHumanApproval) {
            this.needHumanApproval = needHumanApproval;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }

        public String getSopCode() {
            return sopCode;
        }

        public void setSopCode(String sopCode) {
            this.sopCode = sopCode;
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

        public Map<String, Object> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, Object> parameters) {
            this.parameters = parameters;
        }

        @Override
        public String toString() {
            return "ControlFlowDTO{" +
                    "needHumanApproval=" + needHumanApproval +
                    ", riskLevel='" + riskLevel + '\'' +
                    ", sopCode='" + sopCode + '\'' +
                    ", actionName='" + actionName + '\'' +
                    ", targetDevice='" + targetDevice + '\'' +
                    ", parameters=" + parameters +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "SopRecommendationDTO{" +
                "controlFlow=" + controlFlow +
                ", displayView='" + displayView + '\'' +
                '}';
    }
}
