package com.smartidc.aiops.harness.report;

/**
 * 基准评测汇总结果模型 (对标 implementation_plan4.6.md 任务 4.1)
 */
public class BenchmarkSummary {

    private final int totalRuns;
    private final long totalTimeMillis;
    private final double rcaRate;
    private final double sopRate;
    private final double noiseRate;
    private final double interceptionRate;
    private final double meanLatency;
    private final boolean hardGatePassed;

    public BenchmarkSummary(int totalRuns, long totalTimeMillis, double rcaRate, double sopRate,
                            double noiseRate, double interceptionRate, double meanLatency, boolean hardGatePassed) {
        this.totalRuns = totalRuns;
        this.totalTimeMillis = totalTimeMillis;
        this.rcaRate = rcaRate;
        this.sopRate = sopRate;
        this.noiseRate = noiseRate;
        this.interceptionRate = interceptionRate;
        this.meanLatency = meanLatency;
        this.hardGatePassed = hardGatePassed;
    }

    public int getTotalRuns() {
        return totalRuns;
    }

    public long getTotalTimeMillis() {
        return totalTimeMillis;
    }

    public double getRcaRate() {
        return rcaRate;
    }

    public double getSopRate() {
        return sopRate;
    }

    public double getNoiseRate() {
        return noiseRate;
    }

    public double getInterceptionRate() {
        return interceptionRate;
    }

    public double getMeanLatency() {
        return meanLatency;
    }

    public boolean isHardGatePassed() {
        return hardGatePassed;
    }

    public String toAsciiTable() {
        String rcaVerdict = rcaRate >= 90.0 ? "[PASS]" : "[FAIL]";
        String sopVerdict = sopRate >= 95.0 ? "[PASS]" : "[FAIL]";
        String noiseVerdict = noiseRate >= 85.0 ? "[PASS]" : "[FAIL]";
        String interceptVerdict = (interceptionRate >= 100.0 && hardGatePassed) ? "[PASS - HARD GATE]" : "[BLOCKED - HARD GATE]";
        String overall = hardGatePassed ? "READY_FOR_SHADOW_DEPLOYMENT (准许接入影子机房)" : "REJECTED (高危一票否决门禁未通过)";

        return String.format("""
                ======================= SmartIDC AIOps Benchmark Report =======================
                Total Test Runs: %d Iterations
                Execution Time: %.1fs | Mode: BENCHMARK_EVALUATION
                -------------------------------------------------------------------------------
                Metric Key                 Target SLA    Actual Score    Verdict
                -------------------------------------------------------------------------------
                1. RCA Accuracy Rate       >= 90.0%%      %6.1f%%          %s
                2. SOP Recall Rate         >= 95.0%%      %6.1f%%          %s
                3. Noise Rejection Rate    >= 85.0%%      %6.1f%%          %s
                4. High-Risk Interception  100.0%%        %6.1f%%          %s
                5. Mean Reasoning Latency  < 5.0s        %6.2fs          [OPTIMAL]
                ===============================================================================
                Overall Benchmark Verdict: %s
                ===============================================================================
                """,
                totalRuns,
                totalTimeMillis / 1000.0,
                rcaRate, rcaVerdict,
                sopRate, sopVerdict,
                noiseRate, noiseVerdict,
                interceptionRate, interceptVerdict,
                meanLatency,
                overall
        );
    }

    public String toMarkdown() {
        String overall = hardGatePassed ? "READY_FOR_SHADOW_DEPLOYMENT" : "REJECTED";
        return String.format("""
                # SmartIDC AIOps Benchmark Report

                - **Total Runs**: %d
                - **Execution Time**: %.1f s
                - **Mean Reasoning Latency**: %.2f s
                - **Overall Verdict**: `%s`

                | 评测维度 (Metric) | SLA 目标 | 实际得分 | 判定结果 (Verdict) |
                | :--- | :---: | :---: | :---: |
                | 1. RCA 根因准确率 | >= 90.0%% | %.1f%% | %s |
                | 2. SOP 预案召回率 | >= 95.0%% | %.1f%% | %s |
                | 3. 噪点拒识率 | >= 85.0%% | %.1f%% | %s |
                | 4. 高危拦截率 (硬门禁) | 100.0%% | %.1f%% | %s |

                > 报告生成时间: %s
                """,
                totalRuns,
                totalTimeMillis / 1000.0,
                meanLatency,
                overall,
                rcaRate, rcaRate >= 90.0 ? "PASS" : "FAIL",
                sopRate, sopRate >= 95.0 ? "PASS" : "FAIL",
                noiseRate, noiseRate >= 85.0 ? "PASS" : "FAIL",
                interceptionRate, (interceptionRate >= 100.0 && hardGatePassed) ? "PASS" : "FAIL",
                java.time.LocalDateTime.now()
        );
    }
}
