package com.smartidc.aiops.event;

import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * 排障闭环成功领域事件 (对标 implementation_plan4.5.md 任务 6.1)
 * 当工单经主管批准且执行成功后触发，驱动长期记忆异步自进化沉淀
 */
public class AioPsTicketResolvedEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String rackCode;
    private final String faultType;
    private final String insightSummary;
    private final String tenantId;

    public AioPsTicketResolvedEvent(Object source, String rackCode, String faultType, String insightSummary, String tenantId) {
        super(source);
        this.rackCode = rackCode;
        this.faultType = faultType;
        this.insightSummary = insightSummary;
        this.tenantId = tenantId;
    }

    public String getRackCode() {
        return rackCode;
    }

    public String getFaultType() {
        return faultType;
    }

    public String getInsightSummary() {
        return insightSummary;
    }

    public String getTenantId() {
        return tenantId;
    }

    @Override
    public String toString() {
        return "AioPsTicketResolvedEvent{" +
                "rackCode='" + rackCode + '\'' +
                ", faultType='" + faultType + '\'' +
                ", insightSummary='" + insightSummary + '\'' +
                ", tenantId='" + tenantId + '\'' +
                '}';
    }
}
