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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 业务编排服务与 Redisson 工单互斥锁中枢 (对标 implementation_plan4.5.md 任务 4)
 * 职责：
 * 1. 负责 SSE 诊断流式推屏生命周期与异步后台 StateGraph 驱动
 * 2. 负责基于 Redisson 分布式锁的主管审批恢复执行与长期记忆事件发布
 */
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
     *
     * @param request 排障请求 DTO
     * @return SseEmitter 实例
     */
    public SseEmitter startStreamDiagnosis(DiagnoseStreamRequestDTO request) {
        String traceId = (request.getTraceId() != null && !request.getTraceId().isBlank())
                ? request.getTraceId()
                : "trace-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. 建立 10 分钟超时的 SSE Emitter
        SseEmitter emitter = sseSessionManager.createSession(traceId, 600000L);

        // 2. 异步提交工作流计算，不阻塞当前 Web MVC 线程，挂起时毫秒级释放线程
        CompletableFuture.runAsync(() -> {
            try {
                // 推送开始排障打字机事件
                sseSessionManager.sendEvent(traceId, "thinking", "正在拉取机柜动环拓扑时序数据并分析根因...");

                OverAllState finalState = stateGraphService.runWorkflow(
                        request.getRackCode(),
                        request.getFaultSymptom(),
                        request.getTenantId() != null ? request.getTenantId() : "000000",
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
                        interruptDTO.setSummary(sop.getDisplayView()); // 完整高保真 Markdown 渲染视图
                    }

                    log.warn("📢 [SSE 推送] 高危任务已安全挂起，向前端推送审批卡片, traceId: {}", traceId);
                    sseSessionManager.sendEvent(traceId, "hitl_interrupt", interruptDTO);
                } else if ("COMPLETED".equals(status)) {
                    // 低危自愈：推送完成回执并关闭会话
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
     *
     * @param request 恢复执行请求 DTO
     * @return 最终动作执行回执
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
