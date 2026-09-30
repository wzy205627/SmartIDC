# 《智维云 (SmartIDC)》阶段 4.5：Web REST/SSE 流式推屏、主管在线审批恢复与长期记忆自进化闭环详细执行方案

> **本阶段核心目标**：  
> 1. **大屏沉浸式交互与非阻塞挂起**：提供 `POST /api/v1/aiops/diagnose/stream` SSE 长连接接口，实时分发思考推理（`thinking` 打字机）、工具调用（`tool` 拓扑探查状态）与高危挂起审批卡片（`hitl_interrupt`），挂起后物理计算线程毫秒级释放归还线程池，服务端**零线程阻塞、零 Thread.sleep()**；  
> 2. **主管一键决策与 StateGraph 无损唤醒**：提供 `POST /api/v1/aiops/ticket/resume` 接口，通过 `Redisson` 分布式互斥锁防重防并发，唤醒线程基于 `traceId` / `threadId` 从 Redis 加载 Checkpoint 快照，驱动工作流无损走完 Node 4 (`Action_Execution_Node`) 并生成归档回执；  
> 3. **工业级三大防御性细节落地**：
>    - **SSE 客户端断开与心跳保活**：15 秒定时 ping 注释帧，客户端断开仅注销连接，绝不中断后台图执行与 Redis 快照落库；
>    - **主管驳回（Reject）与参数覆盖（Override）分支**：支持主管在线驳回或修正倒闸参数，驳回安全跳过高危硬件动作直接作废工单；
>    - **长期记忆异步自进化沉淀**：工单恢复执行成功后，通过 Spring `@Async` 事件解耦触发 RCA 成功病历沉淀至 pgvector 向量库。

---

## 一、 整体交互时序与全生命周期流转图

```
[数字孪生大屏/前端]                       [Web Controller / Service]             [StateGraph / RedisSaver]
       │                                             │                                      │
       │── 1. POST /diagnose/stream (开启排障) ──────>│                                      │
       │                                             │── 启动异步任务: runWorkflowAsync() ──>│
       │<── 2. 建立 SSE 长连接 (注册 SseSession) ────┤                                      │
       │<── 3. event: thinking (实时推流打字机) ──────┤<── AgentScope Hook 拦截推理 Token ───│
       │<── 4. event: tool (拓扑探查进度状态) ────────┤<── Tool 执行前后拦截通知 ─────────────│
       │                                             │                                      │ (遇高危跳闸故障)
       │                                             │                                      │ 进入 APPROVAL_SUSPEND_NODE
       │                                             │<── 触发 interruptAfter，快照落库 Redis │ 释放计算线程，归还线程池！
       │<── 5. event: hitl_interrupt (推送审批卡) ───┤                                      │
       │   (展示 Markdown 报告与【批准/驳回】按钮)    │                                      │
       │                                             │                                      │
       │   === 人类主管现场核对 (持续数分钟至数小时，后端零线程阻塞，资源零挂死) ===          │
       │                                             │                                      │
       │── 6. POST /ticket/resume (点击【批准】) ────>│                                      │
       │                                             │── Redisson 获取工单互斥锁 (防并发) ──┐ │
       │                                             │── compiledGraph.invoke(resumeConfig) ┼>│ 从 Redis 还原 Checkpoint
       │                                             │                                      │ 继续向下驱动 Node 4 幂等执行
       │                                             │<── 返回最终 State (COMPLETED) ───────┴─│
       │                                             │── 发布 AioPsTicketResolvedEvent ─────┐ │
       │<── 7. 返回 R.ok(ActionExecutionResultDTO) ──┤                                      │ │
       │                                             │                                      ▼ │
       │                                             │── @Async 异步监听器提炼病历 ─────────>│ 写入 pgvector 长期记忆库
```

---

## 二、 核心任务深度拆解与技术落地设计

### 任务 1：接口契约模型与 DTO 定义

#### 1.1 排障流式启动请求 DTO：[DiagnoseStreamRequestDTO.java](file:///d:/SmartIDC/smartidc-aiops/src/main/java/com/smartidc/aiops/domain/dto/DiagnoseStreamRequestDTO.java)
- **文件路径**：`smartidc-aiops/src/main/java/com/smartidc/aiops/domain/dto/DiagnoseStreamRequestDTO.java`
- **字段规范**：
  - `rackCode`: 目标机柜编码（如 `RACK-A01`，必填）
  - `faultSymptom`: 故障表象描述（如 `精密空调压缩机跳闸过温告警`，必填）
  - `tenantId`: 多租户隔离 ID（默认 `000000`）
  - `traceId`: 全链路追踪号（可选，未传时自动生成 `trace-xxxx`）

```java
package com.smartidc.aiops.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

@Schema(description = "智能排障 SSE 流式诊断请求")
public class DiagnoseStreamRequestDTO implements Serializable {
    @Schema(description = "目标机柜编码", example = "RACK-A01", requiredMode = Schema.RequiredMode.REQUIRED)
    private String rackCode;

    @Schema(description = "故障表象描述", example = "精密空调冷机压缩机跳闸过温告警", requiredMode = Schema.RequiredMode.REQUIRED)
    private String faultSymptom;

    @Schema(description = "租户编号", example = "000000")
    private String tenantId = "000000";

    @Schema(description = "全链路追踪 ID", example = "trace-7a1b9f2c")
    private String traceId;

    public DiagnoseStreamRequestDTO() {}

    public String getRackCode() { return rackCode; }
    public void setRackCode(String rackCode) { this.rackCode = rackCode; }
    public String getFaultSymptom() { return faultSymptom; }
    public void setFaultSymptom(String faultSymptom) { this.faultSymptom = faultSymptom; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
}
```

#### 1.2 主管在线决策与恢复请求 DTO：[ResumeActionRequestDTO.java](file:///d:/SmartIDC/smartidc-aiops/src/main/java/com/smartidc/aiops/domain/dto/ResumeActionRequestDTO.java)
- **文件路径**：`smartidc-aiops/src/main/java/com/smartidc/aiops/domain/dto/ResumeActionRequestDTO.java`
- **字段规范**：
  - `ticketId`: 关联的运维工单编号（必填，用于 Redisson 加锁）
  - `traceId`: 工作流线程 ID（必填，用于精准匹配 Redis 中的 Checkpoint 快照）
  - `decision`: 决策类型，枚举值 `APPROVE`（核准恢复执行）或 `REJECT`（驳回作废）（必填）
  - `approvalComment`: 主管审批批注（如：“现场已核实 CRAC-A-02 处于冷备就绪，同意倒闸”）
  - `approverUser`: 审批人用户名（如 `supervisor_zhang`）
  - `overrideParams`: 可选参数覆盖 Map（主管若修改倒闸设备编号或风机转速，优先使用覆盖参数）

```java
package com.smartidc.aiops.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Map;

@Schema(description = "主管审批恢复执行请求")
public class ResumeActionRequestDTO implements Serializable {
    @Schema(description = "关联的运维工单号", example = "30041", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long ticketId;

    @Schema(description = "工作流线程 ID / Trace ID", example = "trace-7a1b9f2c", requiredMode = Schema.RequiredMode.REQUIRED)
    private String traceId;

    @Schema(description = "决策类型: APPROVE (批准执行) / REJECT (驳回作废)", example = "APPROVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String decision;

    @Schema(description = "审批意见批注", example = "现场已确认CRAC-A-02冷机待机就绪，同意倒闸切换回路。")
    private String approvalComment;

    @Schema(description = "审批人", example = "supervisor_zhang")
    private String approverUser;

    @Schema(description = "可选参数覆盖 (如修改目标设备或风速)")
    private Map<String, Object> overrideParams;

    public ResumeActionRequestDTO() {}

    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getApprovalComment() { return approvalComment; }
    public void setApprovalComment(String approvalComment) { this.approvalComment = approvalComment; }
    public String getApproverUser() { return approverUser; }
    public void setApproverUser(String approverUser) { this.approverUser = approverUser; }
    public Map<String, Object> getOverrideParams() { return overrideParams; }
    public void setOverrideParams(Map<String, Object> overrideParams) { this.overrideParams = overrideParams; }
}
```

#### 1.3 SSE 推流统一事件载体 DTO：[DiagnoseStreamEventDTO.java](file:///d:/SmartIDC/smartidc-aiops/src/main/java/com/smartidc/aiops/domain/dto/DiagnoseStreamEventDTO.java)
- **文件路径**：`smartidc-aiops/src/main/java/com/smartidc/aiops/domain/dto/DiagnoseStreamEventDTO.java`
- **事件结构**：
  - `eventType`: `thinking` | `tool` | `hitl_interrupt` | `completed` | `error` | `ping`
  - `traceId`: 追踪号
  - `payload`: 动态数据对象（文本增量、工具参数、`HitlInterruptDTO` 或 `ActionExecutionResultDTO`）
  - `timestamp`: 毫秒时间戳

---

### 任务 2：SSE 连接生命周期与心跳保活管理器 (`AiOpsSseSessionManager`)

创建 SSE 会话管理器：`smartidc-aiops/src/main/java/com/smartidc/aiops/bridge/AiOpsSseSessionManager.java`

#### 核心职责与防御性设计：
1. **多连接并发注册**：使用 `ConcurrentHashMap<String, SseEmitter>` 管理活跃会话，`key = traceId`；
2. **连接生命周期回调防泄漏**：
   - 监听 `emitter.onCompletion(...)`、`emitter.onTimeout(...)`、`emitter.onError(...)`，确保从 Map 中移除并安全关闭；
3. **15 秒心跳保活机制**：
   - 利用 Spring `@Scheduled(fixedDelay = 15000)` 扫描活跃会话，向客户端发送 `:ping\n\n` 注释帧，防止 Nginx / 浏览器 60 秒超时断开；
4. **客户端意外断开容错防崩溃**：
   - 发送时捕获 `IOException` / `ClientAbortException`，仅注销该会话，**绝不向上抛出异常打断后台 StateGraph 异步执行和 Redis 快照存储**。

```java
package com.smartidc.aiops.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiOpsSseSessionManager {
    private static final Logger log = LoggerFactory.getLogger(AiOpsSseSessionManager.class);
    private final Map<String, SseEmitter> sessionMap = new ConcurrentHashMap<>();

    public SseEmitter createSession(String traceId, long timeoutMillis) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        sessionMap.put(traceId, emitter);

        emitter.onCompletion(() -> {
            log.info("🔌 [SSE Session] 正常完成并关闭, traceId: {}", traceId);
            sessionMap.remove(traceId);
        });
        emitter.onTimeout(() -> {
            log.warn("⏰ [SSE Session] 连接超时自动释放, traceId: {}", traceId);
            sessionMap.remove(traceId);
        });
        emitter.onError(e -> {
            log.warn("⚠️ [SSE Session] 客户端异常中断 (如刷新浏览器), traceId: {}, error: {}", traceId, e.getMessage());
            sessionMap.remove(traceId);
        });

        return emitter;
    }

    public void sendEvent(String traceId, String eventName, Object data) {
        SseEmitter emitter = sessionMap.get(traceId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            log.warn("⚠️ 向客户端推送 SSE 事件失败 (客户端可能已断开), traceId: {}, event: {}", traceId, eventName);
            sessionMap.remove(traceId);
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void sendHeartbeat() {
        if (sessionMap.isEmpty()) return;
        sessionMap.forEach((traceId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                sessionMap.remove(traceId);
            }
        });
    }

    public void closeSession(String traceId) {
        SseEmitter emitter = sessionMap.remove(traceId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception ignored) {}
        }
    }
}
```

---

### 任务 3：扩展 StateGraph 恢复执行能力 (`resumeWorkflow`)

在 [IdcAioPsStateGraphService.java](file:///d:/SmartIDC/smartidc-aiops/src/main/java/com/smartidc/aiops/graph/IdcAioPsStateGraphService.java) 中增加从 RedisSaver Checkpoint 恢复执行的方法：

#### 3.1 增加主管决策状态键处理：
- 在 [StateKeys.java](file:///d:/SmartIDC/smartidc-aiops/src/main/java/com/smartidc/aiops/graph/StateKeys.java) 中扩展：
  - `APPROVAL_DECISION`: 主管决策 (`APPROVE` / `REJECT`)
  - `APPROVAL_COMMENT`: 主管审批批注
  - `OVERRIDE_PARAMS`: 覆盖参数 Map

#### 3.2 改造 `executeActionExecutionNode` 增强驳回与覆盖分支：
```java
private Map<String, Object> executeActionExecutionNode(OverAllState state) {
    String traceId = state.value(StateKeys.TRACE_ID, String.class).orElse("N/A");
    String status = state.value(StateKeys.STATUS, String.class).orElse("AUTO_APPROVED");
    String approvalDecision = state.value("approval_decision", String.class).orElse("APPROVE");
    SopRecommendationDTO sop = state.value(StateKeys.SOP_RECOMMENDATION, SopRecommendationDTO.class).orElse(null);

    // 1. 防御分支：主管驳回 (REJECT) 逻辑
    if ("REJECT".equalsIgnoreCase(approvalDecision)) {
        log.warn("🛑 [StateGraph::Node4] 主管已驳回该高危倒闸操作，安全跳过硬件下发，工单作废！traceId: {}", traceId);
        ActionExecutionResultDTO rejectResult = new ActionExecutionResultDTO();
        rejectResult.setExecutionId("REJECT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        rejectResult.setTicketId(System.currentTimeMillis() % 1000000L);
        rejectResult.setActionName("REJECTED_BY_SUPERVISOR");
        rejectResult.setTargetDevice(state.value(StateKeys.RACK_CODE, String.class).orElse("UNKNOWN"));
        rejectResult.setExecutionStatus("REJECTED");
        rejectResult.setExecutedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        rejectResult.setReceiptMessage("[主管已驳回] 操作未通过审批，倒闸动作已取消，工单置为已作废。");

        Map<String, Object> update = new HashMap<>();
        update.put(StateKeys.EXECUTION_RESULT, rejectResult);
        update.put(StateKeys.STATUS, "REJECTED");
        return update;
    }

    // 2. 幂等性防御：若已有 SUCCESS 回执直接跳过
    Optional<ActionExecutionResultDTO> existingResult = state.value(StateKeys.EXECUTION_RESULT, ActionExecutionResultDTO.class);
    if (existingResult.isPresent() && "SUCCESS".equals(existingResult.get().getExecutionStatus())) {
        log.info("🔁 [StateGraph::Node4] 命中幂等防护，已有执行成功回执，直接返回, traceId: {}", traceId);
        return Map.of();
    }

    // 3. 正常执行：优先读取主管传入的 overrideParams
    @SuppressWarnings("unchecked")
    Map<String, Object> overrideParams = (Map<String, Object>) state.value("override_params", Map.class).orElse(null);
    String actionName = (sop != null && sop.getControlFlow() != null) ? sop.getControlFlow().getActionName() : "ADJUST_FAN_SPEED";
    String targetDevice = (sop != null && sop.getControlFlow() != null) ? sop.getControlFlow().getTargetDevice() : state.value(StateKeys.RACK_CODE, String.class).orElse("UNKNOWN");

    if (overrideParams != null && overrideParams.containsKey("target_device")) {
        targetDevice = String.valueOf(overrideParams.get("target_device"));
        log.info("✏️ [StateGraph::Node4] 应用主管覆盖参数 targetDevice: {}", targetDevice);
    }

    String receiptMessage;
    if ("HITL_SUSPENDED".equals(status) || "PENDING_APPROVAL".equals(status)) {
        receiptMessage = String.format("[主管核准执行] 设备 %s 已完成高危倒闸动作: %s，系统指标回稳", targetDevice, actionName);
    } else {
        receiptMessage = String.format("[低危自愈闭环] 设备 %s 已自动调节参数动作: %s，巡检工单已自动报备", targetDevice, actionName);
    }

    ActionExecutionResultDTO executionResult = new ActionExecutionResultDTO();
    executionResult.setExecutionId("EXEC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    executionResult.setTicketId(System.currentTimeMillis() % 1000000L);
    executionResult.setActionName(actionName);
    executionResult.setTargetDevice(targetDevice);
    executionResult.setExecutionStatus("SUCCESS");
    executionResult.setExecutedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    executionResult.setReceiptMessage(receiptMessage);

    Map<String, Object> update = new HashMap<>();
    update.put(StateKeys.EXECUTION_RESULT, executionResult);
    update.put(StateKeys.STATUS, "COMPLETED");
    return update;
}
```

#### 3.3 编写无损恢复接口方法：`resumeWorkflow`
```java
/**
 * 唤醒挂起的工作流继续走完 Node 4
 *
 * @param traceId        工作流 ThreadId
 * @param decision       主管决策 (APPROVE / REJECT)
 * @param comment        主管审批批注
 * @param overrideParams 可选覆盖参数
 * @return 最终恢复执行后的 State
 */
public OverAllState resumeWorkflow(String traceId, String decision, String comment, Map<String, Object> overrideParams) {
    log.info("▶️ [StateGraph::Resume] 收到唤醒请求, traceId: {}, decision: {}", traceId, decision);
    RunnableConfig resumeConfig = RunnableConfig.builder().threadId(traceId).build();

    try {
        // 1. 更新注入主管审批决策与参数至 Checkpoint
        Map<String, Object> resumeInput = new HashMap<>();
        resumeInput.put("approval_decision", decision);
        if (comment != null) resumeInput.put("approval_comment", comment);
        if (overrideParams != null) resumeInput.put("override_params", overrideParams);

        // 2. 核心唤醒：调用 updateState 写入决策后，传入 null input 驱动从挂起点继续流转
        workflowGraph.updateState(resumeConfig, resumeInput);
        return workflowGraph.invoke((Map<String, Object>) null, resumeConfig)
                .orElseThrow(() -> new RuntimeException("StateGraph 恢复执行未返回最终状态"));
    } catch (Exception e) {
        log.error("❌ 唤醒 StateGraph 工作流异常, traceId: {}", traceId, e);
        throw new RuntimeException("StateGraph 唤醒执行异常: " + e.getMessage(), e);
    }
}
```

---

### 任务 4：业务编排服务与 Redisson 工单互斥锁 (`AiOpsDiagnoseService`)

创建服务类：`smartidc-aiops/src/main/java/com/smartidc/aiops/service/AiOpsDiagnoseService.java`

#### 核心流程编排代码骨架：
```java
package com.smartidc.aiops.service;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.smartidc.aiops.bridge.AiOpsSseSessionManager;
import com.smartidc.aiops.domain.dto.ActionExecutionResultDTO;
import com.smartidc.aiops.domain.dto.DiagnoseStreamRequestDTO;
import com.smartidc.aiops.domain.dto.HitlInterruptDTO;
import com.smartidc.aiops.domain.dto.ResumeActionRequestDTO;
import com.smartidc.aiops.domain.dto.SopRecommendationDTO;
import com.smartidc.aiops.event.AioPsTicketResolvedEvent;
import com.smartidc.aiops.graph.IdcAioPsStateGraphService;
import com.smartidc.aiops.graph.StateKeys;
import com.smartidc.common.exception.ServiceException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class AiOpsDiagnoseService {
    private static final Logger log = LoggerFactory.getLogger(AiOpsDiagnoseService.class);

    private final IdcAioPsStateGraphService stateGraphService;
    private final AiOpsSseSessionManager sseSessionManager;
    private final RedissonClient redissonClient;
    private final ApplicationEventPublisher eventPublisher;

    public AiOpsDiagnoseService(
            IdcAioPsStateGraphService stateGraphService,
            AiOpsSseSessionManager sseSessionManager,
            RedissonClient redissonClient,
            ApplicationEventPublisher eventPublisher) {
        this.stateGraphService = stateGraphService;
        this.sseSessionManager = sseSessionManager;
        this.redissonClient = redissonClient;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 开启流式排障：建立 SSE 连接并提交后台异步计算
     */
    public SseEmitter startStreamDiagnosis(DiagnoseStreamRequestDTO request) {
        String traceId = (request.getTraceId() != null && !request.getTraceId().isEmpty())
                ? request.getTraceId()
                : "trace-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. 建立 10 分钟超时的 SSE Emitter
        SseEmitter emitter = sseSessionManager.createSession(traceId, 600000L);

        // 2. 异步提交工作流计算，不阻塞当前 Web MVC 线程
        CompletableFuture.runAsync(() -> {
            try {
                // 推送开始排障事件
                sseSessionManager.sendEvent(traceId, "thinking", "正在拉取机柜动环拓扑时序数据并分析根因...");

                OverAllState finalState = stateGraphService.runWorkflow(
                        request.getRackCode(),
                        request.getFaultSymptom(),
                        request.getTenantId(),
                        traceId
                );

                String status = finalState.value(StateKeys.STATUS, String.class).orElse("UNKNOWN");

                if ("HITL_SUSPENDED".equals(status)) {
                    // 高危挂起：组装 HitlInterruptDTO 并推送审批卡片
                    SopRecommendationDTO sop = finalState.value(StateKeys.SOP_RECOMMENDATION, SopRecommendationDTO.class).orElse(null);
                    HitlInterruptDTO interruptDTO = new HitlInterruptDTO();
                    interruptDTO.setStatus("SUSPENDED");
                    interruptDTO.setTicketId(System.currentTimeMillis() % 1000000L);
                    interruptDTO.setCheckpointId(traceId);
                    interruptDTO.setApproverRole("ROLE_IDC_SUPERVISOR");

                    if (sop != null) {
                        interruptDTO.setRecommendedAction(sop.getControlFlow() != null ? sop.getControlFlow().getActionName() : "HIGH_RISK_ACTION");
                        interruptDTO.setSummary(sop.getDisplayView()); // 完整 Markdown
                    }

                    log.warn("📢 [SSE 推送] 高危任务已安全挂起，向前端推送审批卡片, traceId: {}", traceId);
                    sseSessionManager.sendEvent(traceId, "hitl_interrupt", interruptDTO);
                } else if ("COMPLETED".equals(status)) {
                    // 低危自愈：推送完成回执
                    ActionExecutionResultDTO result = finalState.value(StateKeys.EXECUTION_RESULT, ActionExecutionResultDTO.class).orElse(null);
                    log.info("🎉 [SSE 推送] 低危自愈全闭环，向前端推送完成结果, traceId: {}", traceId);
                    sseSessionManager.sendEvent(traceId, "completed", result);
                    sseSessionManager.closeSession(traceId);
                }
            } catch (Exception e) {
                log.error("❌ 异步执行 StateGraph 异常, traceId: {}", traceId, e);
                sseSessionManager.sendEvent(traceId, "error", "排障执行异常: " + e.getMessage());
                sseSessionManager.closeSession(traceId);
            }
        });

        return emitter;
    }

    /**
     * 主管审批决策与唤醒恢复 (Redisson 分布式锁防重)
     */
    public ActionExecutionResultDTO resumeTicket(ResumeActionRequestDTO request) {
        String lockKey = "aiops:ticket:lock:" + request.getTicketId();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取互斥锁，等待 0 秒，持锁 10 秒，杜绝多人同时点击审批
            if (!lock.tryLock(0, 10, TimeUnit.SECONDS)) {
                throw new ServiceException("该工单正在被其他值班长处理中，请勿重复操作！");
            }

            log.info("🔒 [Redisson] 成功获取工单锁: {}, 执行审批恢复", lockKey);

            // 唤醒 StateGraph 从挂起点继续流转 Node 4
            OverAllState resumedState = stateGraphService.resumeWorkflow(
                    request.getTraceId(),
                    request.getDecision(),
                    request.getApprovalComment(),
                    request.getOverrideParams()
            );

            ActionExecutionResultDTO executionResult = resumedState.value(StateKeys.EXECUTION_RESULT, ActionExecutionResultDTO.class)
                    .orElseThrow(() -> new ServiceException("恢复执行未生成动作回执"));

            // 若批准且执行成功，异步发布成功事件，沉淀为长期记忆
            if ("APPROVE".equalsIgnoreCase(request.getDecision()) && "SUCCESS".equals(executionResult.getExecutionStatus())) {
                eventPublisher.publishEvent(new AioPsTicketResolvedEvent(
                        this,
                        resumedState.value(StateKeys.RACK_CODE, String.class).orElse("UNKNOWN"),
                        resumedState.value(StateKeys.FAULT_SYMPTOM, String.class).orElse("FAULT"),
                        executionResult.getReceiptMessage(),
                        resumedState.value(StateKeys.TENANT_ID, String.class).orElse("000000")
                ));
            }

            // 通知前端 SSE 大屏完成
            sseSessionManager.sendEvent(request.getTraceId(), "completed", executionResult);
            sseSessionManager.closeSession(request.getTraceId());

            return executionResult;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("获取审批互斥锁被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("🔓 [Redisson] 释放工单锁: {}", lockKey);
            }
        }
    }
}
```

---

### 任务 5：Web 控制器实现 (`AiOpsDiagnoseController`)

创建控制器：`smartidc-aiops/src/main/java/com/smartidc/aiops/controller/AiOpsDiagnoseController.java`

```java
package com.smartidc.aiops.controller;

import com.smartidc.aiops.domain.dto.ActionExecutionResultDTO;
import com.smartidc.aiops.domain.dto.DiagnoseStreamRequestDTO;
import com.smartidc.aiops.domain.dto.ResumeActionRequestDTO;
import com.smartidc.aiops.service.AiOpsDiagnoseService;
import com.smartidc.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "AIOps 智能运维诊断与在线审批中枢")
@RestController
@RequestMapping("/api/v1/aiops")
public class AiOpsDiagnoseController {

    private final AiOpsDiagnoseService diagnoseService;

    public AiOpsDiagnoseController(AiOpsDiagnoseService diagnoseService) {
        this.diagnoseService = diagnoseService;
    }

    @Operation(summary = "开启智能排障诊断 (SSE 流式推屏)")
    @PostMapping(value = "/diagnose/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter startDiagnoseStream(@RequestBody DiagnoseStreamRequestDTO request) {
        return diagnoseService.startStreamDiagnosis(request);
    }

    @Operation(summary = "主管审批决策与唤醒恢复执行")
    @PostMapping("/ticket/resume")
    public R<ActionExecutionResultDTO> resumeTicket(@RequestBody ResumeActionRequestDTO request) {
        ActionExecutionResultDTO result = diagnoseService.resumeTicket(request);
        return R.ok(result, "工单审批决策已生效，动作流转完成");
    }
}
```

---

### 任务 6：长期记忆异步自进化事件与监听器

#### 6.1 定义排障闭环成功领域事件：`AioPsTicketResolvedEvent.java`
- **路径**：`smartidc-aiops/src/main/java/com/smartidc/aiops/event/AioPsTicketResolvedEvent.java`
```java
package com.smartidc.aiops.event;

import org.springframework.context.ApplicationEvent;

public class AioPsTicketResolvedEvent extends ApplicationEvent {
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

    public String getRackCode() { return rackCode; }
    public String getFaultType() { return faultType; }
    public String getInsightSummary() { return insightSummary; }
    public String getTenantId() { return tenantId; }
}
```

#### 6.2 编写异步事件监听器：`LongTermMemoryEventListener.java`
- **路径**：`smartidc-aiops/src/main/java/com/smartidc/aiops/event/LongTermMemoryEventListener.java`
- 使用 `@Async` + `@EventListener`，调用 `LongTermMemoryStore.recordNewInsight(...)` 写入 pgvector。

```java
package com.smartidc.aiops.event;

import com.smartidc.aiops.memory.LongTermMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class LongTermMemoryEventListener {
    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryEventListener.class);

    private final LongTermMemoryStore longTermMemoryStore;

    public LongTermMemoryEventListener(LongTermMemoryStore longTermMemoryStore) {
        this.longTermMemoryStore = longTermMemoryStore;
    }

    @Async
    @EventListener
    public void onTicketResolved(AioPsTicketResolvedEvent event) {
        log.info("🧠 [长期记忆自进化] 监听到工单成功闭环事件，开始异步提炼病历, rackCode: {}", event.getRackCode());
        try {
            longTermMemoryStore.recordNewInsight(
                    event.getRackCode(),
                    event.getFaultType(),
                    event.getInsightSummary(),
                    event.getTenantId()
            );
        } catch (Exception e) {
            log.warn("⚠️ 异步沉淀长期记忆异常: {}", e.getMessage());
        }
    }
}
```

---

### 任务 7：阶段 4.5 自动化集成测试 (`Phase4WebAndHitlResumeIntegrationTest`)

创建测试：`smartidc-aiops/src/test/java/com/smartidc/aiops/Phase4WebAndHitlResumeIntegrationTest.java`

#### 必须覆盖的 4 个核心断言场景：
1. **用例 1: 低危 SSE 自愈闭环断言 (`testLowRiskSseSelfHealingFlow`)**
   - 请求 `POST /api/v1/aiops/diagnose/stream` (B01 机柜微热)；
   - 收集 SSE 事件流，断言收到 `thinking` 与 `completed` 事件；
   - 断言执行状态为 `SUCCESS`，回执中含自愈信息，无挂起中断。
2. **用例 2: 高危挂起与主管在线批准唤醒闭环 (`testHighRiskSuspendAndSupervisorApproveResume`)**
   - 步骤 A：请求流式排障（A01 冷机跳闸），断言收到 `hitl_interrupt` 审批卡片；
   - 步骤 B：断言 Redis 中保存了停靠在 `APPROVAL_SUSPEND_NODE` 的快照；
   - 步骤 C：模拟主管调用 `POST /api/v1/aiops/ticket/resume`（`decision = "APPROVE"`）；
   - 步骤 D：断言工单状态从 `HITL_SUSPENDED` 流转为 `COMPLETED`，Node 4 执行回执成功生成；
   - 步骤 E：断言 pgvector 中异步写入了新的 `category = 'HISTORICAL_INSIGHT'` 长期记忆。
3. **用例 3: 主管驳回安全拦截断言 (`testSupervisorRejectBranch`)**
   - 对挂起工单发起 `decision = "REJECT"`；
   - 断言恢复后状态为 `REJECTED`，回执记录“主管已驳回”，未对设备执行任何硬件操作。
4. **用例 4: Redisson 工单互斥锁并发防重断言 (`testRedissonConcurrentLock`)**
   - 模拟两名主管使用不同线程同时提交相同的 `ticketId` 审批；
   - 断言一个线程成功获取锁并恢复，另一个线程立即抛出 `ServiceException: 该工单正在被其他值班长处理中`，彻底杜绝双重倒闸事故。

---

## 三、 阶段 4.5 验收标准清单

- [ ] **SSE 流式接口上线**：`POST /api/v1/aiops/diagnose/stream` 正常建立连接并支持打字机推屏与挂起通知。
- [ ] **高危审批卡片精准推送**：Markdown 人类视图与动作参数通过 `event: hitl_interrupt` 完整推至前端。
- [ ] **服务端零线程阻塞**：挂起后 Worker 线程立即归还线程池，服务端无挂起阻塞线程。
- [ ] **Redisson 互斥锁生效**：并发调用审批接口时成功拦截重复提交，抛出明确提示。
- [ ] **StateGraph 唤醒恢复无损**：主管批准后从 Redis 快照加载，无缝完成 Node 4 幂等执行。
- [ ] **主管驳回分支健全**：驳回操作能够安全终止硬件下发并将工单标记为 `REJECTED`。
- [ ] **心跳与断开防崩溃**：SSE 连接断开不影响后台状态机计算与 Redis 快照落库。
- [ ] **长期记忆异步自进化**：排障成功后异步将病历沉淀至 pgvector，长期记忆库自增更新。
- [ ] **集成测试全绿**：`Phase4WebAndHitlResumeIntegrationTest` 4 大测试场景 100% 通过。
