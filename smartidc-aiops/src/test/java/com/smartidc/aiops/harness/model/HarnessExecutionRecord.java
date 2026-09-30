package com.smartidc.aiops.harness.model;

/**
 * 单次基准评测执行记录模型 (对标 implementation_plan4.6.md 任务 1.2)
 */
public class HarnessExecutionRecord {

    private String caseId;
    private String caseName;
    private String traceId;
    private long durationMillis;
    private String actualRootCauseType;
    private Double confidence;
    private boolean rcaMatched;
    private String actualRiskLevel;
    private boolean sopMatched;
    private boolean noiseRejected;
    private boolean interceptedInGateway;
    private boolean hardGatePassed;
    private String errorMessage;

    public HarnessExecutionRecord() {
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getCaseName() {
        return caseName;
    }

    public void setCaseName(String caseName) {
        this.caseName = caseName;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public String getActualRootCauseType() {
        return actualRootCauseType;
    }

    public void setActualRootCauseType(String actualRootCauseType) {
        this.actualRootCauseType = actualRootCauseType;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public boolean isRcaMatched() {
        return rcaMatched;
    }

    public void setRcaMatched(boolean rcaMatched) {
        this.rcaMatched = rcaMatched;
    }

    public String getActualRiskLevel() {
        return actualRiskLevel;
    }

    public void setActualRiskLevel(String actualRiskLevel) {
        this.actualRiskLevel = actualRiskLevel;
    }

    public boolean isSopMatched() {
        return sopMatched;
    }

    public void setSopMatched(boolean sopMatched) {
        this.sopMatched = sopMatched;
    }

    public boolean isNoiseRejected() {
        return noiseRejected;
    }

    public void setNoiseRejected(boolean noiseRejected) {
        this.noiseRejected = noiseRejected;
    }

    public boolean isInterceptedInGateway() {
        return interceptedInGateway;
    }

    public void setInterceptedInGateway(boolean interceptedInGateway) {
        this.interceptedInGateway = interceptedInGateway;
    }

    public boolean isHardGatePassed() {
        return hardGatePassed;
    }

    public void setHardGatePassed(boolean hardGatePassed) {
        this.hardGatePassed = hardGatePassed;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "HarnessExecutionRecord{" +
                "caseId='" + caseId + '\'' +
                ", caseName='" + caseName + '\'' +
                ", traceId='" + traceId + '\'' +
                ", durationMillis=" + durationMillis +
                ", actualRootCauseType='" + actualRootCauseType + '\'' +
                ", confidence=" + confidence +
                ", rcaMatched=" + rcaMatched +
                ", actualRiskLevel='" + actualRiskLevel + '\'' +
                ", sopMatched=" + sopMatched +
                ", noiseRejected=" + noiseRejected +
                ", interceptedInGateway=" + interceptedInGateway +
                ", hardGatePassed=" + hardGatePassed +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
