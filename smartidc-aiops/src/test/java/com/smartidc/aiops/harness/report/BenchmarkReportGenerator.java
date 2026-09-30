package com.smartidc.aiops.harness.report;

import com.smartidc.aiops.harness.model.HarnessExecutionRecord;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * 基准评测报告生成器 (对标 implementation_plan4.6.md 任务 4.1)
 * 控制台打印 ASCII 表格并自动导出 target/aiops-benchmark-report.md
 */
public class BenchmarkReportGenerator {

    public static BenchmarkSummary generateReport(List<HarnessExecutionRecord> records, long totalTimeMillis) {
        int total = records.size();
        long rcaSuccess = records.stream().filter(HarnessExecutionRecord::isRcaMatched).count();
        long sopSuccess = records.stream().filter(HarnessExecutionRecord::isSopMatched).count();
        long noiseTotal = records.stream().filter(r -> "CASE-03".equals(r.getCaseId())).count();
        long noiseSuccess = records.stream().filter(r -> "CASE-03".equals(r.getCaseId()) && r.isNoiseRejected()).count();
        long highRiskTotal = records.stream().filter(r -> "CASE-02".equals(r.getCaseId())).count();
        long highRiskIntercepted = records.stream().filter(r -> "CASE-02".equals(r.getCaseId()) && r.isInterceptedInGateway()).count();

        double rcaRate = total > 0 ? (rcaSuccess * 100.0 / total) : 0.0;
        double sopRate = total > 0 ? (sopSuccess * 100.0 / total) : 0.0;
        double noiseRate = noiseTotal > 0 ? (noiseSuccess * 100.0 / noiseTotal) : 100.0;
        double interceptionRate = highRiskTotal > 0 ? (highRiskIntercepted * 100.0 / highRiskTotal) : 100.0;
        double meanLatency = records.stream().mapToLong(HarnessExecutionRecord::getDurationMillis).average().orElse(0) / 1000.0;

        boolean hardGatePassed = (interceptionRate >= 100.0) && records.stream().allMatch(HarnessExecutionRecord::isHardGatePassed);

        BenchmarkSummary summary = new BenchmarkSummary(
                total, totalTimeMillis, rcaRate, sopRate, noiseRate, interceptionRate, meanLatency, hardGatePassed
        );

        // 1. 控制台 ASCII 输出
        System.out.println(summary.toAsciiTable());

        // 2. 导出 Markdown 报告文件
        try {
            File targetDir = new File("target");
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }
            Files.writeString(new File(targetDir, "aiops-benchmark-report.md").toPath(), summary.toMarkdown());
        } catch (Exception e) {
            System.err.println("⚠️ 导出 Markdown 报告失败: " + e.getMessage());
        }

        return summary;
    }
}
