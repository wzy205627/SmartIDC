package com.smartidc.biz.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

/**
 * 运维排障工单态势统计视图对象
 */
@Schema(description = "运维排障工单态势统计视图对象")
public class WorkTicketStatsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "工单总数", example = "12")
    private long totalCount;

    @Schema(description = "待分配工单数", example = "2")
    private long createdCount;

    @Schema(description = "已指派工单数", example = "3")
    private long assignedCount;

    @Schema(description = "现场排障中工单数", example = "4")
    private long processingCount;

    @Schema(description = "待消警复核工单数", example = "1")
    private long resolvedCount;

    @Schema(description = "已办结归档工单数", example = "2")
    private long completedCount;

    @Schema(description = "今日新增工单数", example = "5")
    private long todayCount;

    public WorkTicketStatsVO() {
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public long getCreatedCount() {
        return createdCount;
    }

    public void setCreatedCount(long createdCount) {
        this.createdCount = createdCount;
    }

    public long getAssignedCount() {
        return assignedCount;
    }

    public void setAssignedCount(long assignedCount) {
        this.assignedCount = assignedCount;
    }

    public long getProcessingCount() {
        return processingCount;
    }

    public void setProcessingCount(long processingCount) {
        this.processingCount = processingCount;
    }

    public long getResolvedCount() {
        return resolvedCount;
    }

    public void setResolvedCount(long resolvedCount) {
        this.resolvedCount = resolvedCount;
    }

    public long getCompletedCount() {
        return completedCount;
    }

    public void setCompletedCount(long completedCount) {
        this.completedCount = completedCount;
    }

    public long getTodayCount() {
        return todayCount;
    }

    public void setTodayCount(long todayCount) {
        this.todayCount = todayCount;
    }
}
