package com.smartidc.aiops.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

/**
 * SSE 推流统一事件载体 DTO (对标 implementation_plan4.5.md 任务 1.3)
 */
@Schema(description = "SSE 推流统一事件载体")
public class DiagnoseStreamEventDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "事件类型 (thinking / tool / hitl_interrupt / completed / error / ping)", example = "thinking")
    private String eventType;

    @Schema(description = "全链路追踪号", example = "trace-20240521-001")
    private String traceId;

    @Schema(description = "动态业务负载对象")
    private Object payload;

    @Schema(description = "事件毫秒时间戳", example = "1716307200000")
    private Long timestamp;

    public DiagnoseStreamEventDTO() {
        this.timestamp = System.currentTimeMillis();
    }

    public DiagnoseStreamEventDTO(String eventType, String traceId, Object payload) {
        this.eventType = eventType;
        this.traceId = traceId;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    public DiagnoseStreamEventDTO(String eventType, String traceId, Object payload, Long timestamp) {
        this.eventType = eventType;
        this.traceId = traceId;
        this.payload = payload;
        this.timestamp = timestamp;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "DiagnoseStreamEventDTO{" +
                "eventType='" + eventType + '\'' +
                ", traceId='" + traceId + '\'' +
                ", payload=" + payload +
                ", timestamp=" + timestamp +
                '}';
    }
}
