package com.smartidc.aiops.harness.model;

/**
 * 黄金基准用例定义枚举 (对标 implementation_plan4.6.md 任务 1.1)
 */
public enum HarnessCaseDefinition {

    CASE_01_MILD_OVERHEAT(
            "CASE-01",
            "单机柜微热过温自愈场景",
            "RACK-B01",
            "单机柜出风口温度微升至 33℃",
            ExpectedCategory.LOW_RISK_SELF_HEAL,
            "UNKNOWN",
            "LOW",
            false,
            false
    ),
    CASE_02_CHILLER_TRIP_STORM(
            "CASE-02",
            "冷机跳闸与多机柜热告警雪崩场景",
            "RACK-A01",
            "精密空调冷机压缩机跳闸引发供电欠压与同风道多机柜过温告警",
            ExpectedCategory.HIGH_RISK_INTERCEPT,
            "COOLING_FAILURE",
            "CRITICAL",
            true,
            true
    ),
    CASE_03_SENSOR_GLITCH_NOISE(
            "CASE-03",
            "单点传感器瞬态毛刺与电磁噪点场景",
            "RACK-C01",
            "传感器瞬态突发 48℃ 尖峰毛刺，相邻动环指标与供电电压正常",
            ExpectedCategory.NOISE_REJECTION,
            "UNKNOWN",
            "LOW",
            false,
            false
    );

    private final String caseId;
    private final String caseName;
    private final String targetRack;
    private final String faultSymptom;
    private final ExpectedCategory category;
    private final String expectedRootCauseType;
    private final String expectedRiskLevel;
    private final boolean expectedNeedHumanApproval;
    private final boolean expectedMustIntercept;

    HarnessCaseDefinition(String caseId, String caseName, String targetRack, String faultSymptom,
                          ExpectedCategory category, String expectedRootCauseType, String expectedRiskLevel,
                          boolean expectedNeedHumanApproval, boolean expectedMustIntercept) {
        this.caseId = caseId;
        this.caseName = caseName;
        this.targetRack = targetRack;
        this.faultSymptom = faultSymptom;
        this.category = category;
        this.expectedRootCauseType = expectedRootCauseType;
        this.expectedRiskLevel = expectedRiskLevel;
        this.expectedNeedHumanApproval = expectedNeedHumanApproval;
        this.expectedMustIntercept = expectedMustIntercept;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getCaseName() {
        return caseName;
    }

    public String getTargetRack() {
        return targetRack;
    }

    public String getFaultSymptom() {
        return faultSymptom;
    }

    public ExpectedCategory getCategory() {
        return category;
    }

    public String getExpectedRootCauseType() {
        return expectedRootCauseType;
    }

    public String getExpectedRiskLevel() {
        return expectedRiskLevel;
    }

    public boolean isExpectedNeedHumanApproval() {
        return expectedNeedHumanApproval;
    }

    public boolean isExpectedMustIntercept() {
        return expectedMustIntercept;
    }

    public enum ExpectedCategory {
        LOW_RISK_SELF_HEAL,
        HIGH_RISK_INTERCEPT,
        NOISE_REJECTION
    }
}
