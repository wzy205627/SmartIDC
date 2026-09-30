package com.smartidc.aiops;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.smartidc.aiops.domain.dto.ActionExecutionResultDTO;
import com.smartidc.aiops.domain.dto.DiagnoseStreamRequestDTO;
import com.smartidc.aiops.domain.dto.ResumeActionRequestDTO;
import com.smartidc.aiops.graph.IdcAioPsStateGraphService;
import com.smartidc.aiops.graph.StateKeys;
import com.smartidc.aiops.memory.LongTermMemoryStore;
import com.smartidc.aiops.service.AiOpsDiagnoseService;
import com.smartidc.common.exception.ServiceException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 智维云 (SmartIDC) 阶段 4.5：Web REST/SSE 流式推屏、主管在线审批恢复与长期记忆自进化闭环集成测试
 * 对标 implementation_plan4.5.md 任务 7
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase4WebAndHitlResumeIntegrationTest {

    @Autowired
    private AiOpsDiagnoseService diagnoseService;

    @Autowired
    private IdcAioPsStateGraphService stateGraphService;

    @Autowired
    private RedisSaver redisSaver;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private LongTermMemoryStore longTermMemoryStore;

    @Test
    @Order(1)
    @DisplayName("测试用例 1：低危 SSE 自愈闭环断言 (testLowRiskSseSelfHealingFlow)")
    public void testLowRiskSseSelfHealingFlow() throws InterruptedException {
        String traceId = "trace-sse-low-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("🚀 [测试用例 1] 启动低危机柜 RACK-B01 SSE 流式自愈测试, traceId: " + traceId);

        DiagnoseStreamRequestDTO request = new DiagnoseStreamRequestDTO(
                "RACK-B01",
                "单机柜微热告警自愈巡检",
                "000000",
                traceId
        );

        diagnoseService.startStreamDiagnosis(request);

        // 轮询等待异步工作流执行完毕并闭环落库
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(traceId).build();
        Checkpoint checkpoint = null;
        for (int i = 0; i < 60; i++) {
            Optional<Checkpoint> cpOpt = redisSaver.get(runnableConfig);
            if (cpOpt.isPresent() && "COMPLETED".equals(cpOpt.get().getState().get(StateKeys.STATUS))) {
                checkpoint = cpOpt.get();
                break;
            }
            Thread.sleep(1000);
        }

        Assertions.assertNotNull(checkpoint, "低危自愈任务应在 60 秒内正常完成并落库 Checkpoint");
        Map<String, Object> state = checkpoint.getState();
        Assertions.assertEquals("COMPLETED", state.get(StateKeys.STATUS), "低危自愈任务状态必须为 COMPLETED");
        Assertions.assertEquals("AUTO_APPROVED", state.get(StateKeys.RISK_AUDIT_DECISION), "风控决策必须为 AUTO_APPROVED");

        Object execResultObj = state.get(StateKeys.EXECUTION_RESULT);
        Assertions.assertNotNull(execResultObj, "必须生成动作执行回执");
        System.out.println("✅ 测试用例 1 通过：低危 SSE 自愈闭环流转成功，无挂起中断！");
    }

    @Test
    @Order(2)
    @DisplayName("测试用例 2：高危挂起与主管在线批准唤醒闭环 (testHighRiskSuspendAndSupervisorApproveResume)")
    public void testHighRiskSuspendAndSupervisorApproveResume() throws InterruptedException {
        String traceId = "trace-hitl-approve-" + UUID.randomUUID().toString().substring(0, 8);
        Long ticketId = 70001L;
        System.out.println("🚀 [测试用例 2] 启动高危机柜 RACK-A01 挂起与主管在线唤醒测试, traceId: " + traceId);

        // 步骤 A: 启动流式排障，高危场景停靠在 APPROVAL_SUSPEND_NODE
        DiagnoseStreamRequestDTO request = new DiagnoseStreamRequestDTO(
                "RACK-A01",
                "精密空调压缩机跳闸过温告警",
                "000000",
                traceId
        );
        diagnoseService.startStreamDiagnosis(request);

        // 轮询等待工作流进入挂起状态
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(traceId).build();
        Checkpoint suspendedCheckpoint = null;
        for (int i = 0; i < 60; i++) {
            Optional<Checkpoint> cpOpt = redisSaver.get(runnableConfig);
            if (cpOpt.isPresent() && IdcAioPsStateGraphService.NODE_APPROVAL_SUSPEND.equals(cpOpt.get().getNodeId())) {
                suspendedCheckpoint = cpOpt.get();
                break;
            }
            Thread.sleep(1000);
        }

        Assertions.assertNotNull(suspendedCheckpoint, "高危任务必须在 60 秒内停靠在 APPROVAL_SUSPEND_NODE 挂起桩");
        Assertions.assertEquals("HITL_SUSPENDED", suspendedCheckpoint.getState().get(StateKeys.STATUS), "挂起状态必须为 HITL_SUSPENDED");
        Assertions.assertNull(suspendedCheckpoint.getState().get(StateKeys.EXECUTION_RESULT), "挂起状态严禁偷跑 Node 4 执行回执");
        System.out.println("✅ 成功拦截高危操作并停靠在 APPROVAL_SUSPEND_NODE，准备唤醒...");

        // 步骤 B: 模拟主管调用 /api/v1/aiops/ticket/resume 批准恢复执行
        ResumeActionRequestDTO resumeRequest = new ResumeActionRequestDTO(
                ticketId,
                traceId,
                "APPROVE",
                "现场已核实 CRAC-A-02 处于冷备就绪状态，同意执行倒闸切换",
                "supervisor_zhang",
                null
        );

        ActionExecutionResultDTO executionResult = diagnoseService.resumeTicket(resumeRequest);

        // 步骤 C: 断言恢复执行结果
        Assertions.assertNotNull(executionResult, "唤醒恢复执行必须返回执行回执");
        Assertions.assertEquals("SUCCESS", executionResult.getExecutionStatus(), "恢复执行动作状态必须为 SUCCESS");
        Assertions.assertTrue(executionResult.getReceiptMessage().contains("主管核准执行"), "回执消息必须包含主管核准执行标识");
        System.out.println("✅ 主管批准恢复执行完成, 回执: " + executionResult.getReceiptMessage());

        // 步骤 D: 校验 Redis 快照最终状态为 COMPLETED
        Optional<Checkpoint> finalCpOpt = redisSaver.get(runnableConfig);
        Assertions.assertTrue(finalCpOpt.isPresent());
        Assertions.assertEquals("COMPLETED", finalCpOpt.get().getState().get(StateKeys.STATUS), "恢复执行后最终状态必须为 COMPLETED");

        // 步骤 E: 验证长期记忆异步自进化沉淀
        Thread.sleep(2000); // 稍作等待给 @Async 事件监听器写入 pgvector
        String insights = longTermMemoryStore.retrieveHistoricalInsights("RACK-A01", "过温跳闸", "000000");
        System.out.println("🧠 长期记忆库召回检验: " + insights);
        Assertions.assertTrue(insights.contains("历史案例参考"), "长期记忆库应成功召回新沉淀的病历经验");

        System.out.println("🎉 [测试用例 2 通过] 高危挂起 ➔ 主管在线批准 ➔ StateGraph 无损恢复 ➔ 长期记忆自进化全闭环成功！");
    }

    @Test
    @Order(3)
    @DisplayName("测试用例 3：主管驳回安全拦截断言 (testSupervisorRejectBranch)")
    public void testSupervisorRejectBranch() {
        String traceId = "trace-hitl-reject-" + UUID.randomUUID().toString().substring(0, 8);
        Long ticketId = 70002L;
        System.out.println("🚀 [测试用例 3] 启动高危任务主管在线驳回测试, traceId: " + traceId);

        // 1. 直接触发工作流到达挂起点
        OverAllState suspendState = stateGraphService.runWorkflow(
                "RACK-A01",
                "精密空调压缩机跳闸过温告警",
                "000000",
                traceId
        );
        Assertions.assertEquals("HITL_SUSPENDED", suspendState.value(StateKeys.STATUS, String.class).orElse(null));

        // 2. 主管驳回操作
        ResumeActionRequestDTO rejectRequest = new ResumeActionRequestDTO(
                ticketId,
                traceId,
                "REJECT",
                "现场排查发现备用冷机管路阀门异常，驳回本次倒闸操作",
                "supervisor_li",
                null
        );

        ActionExecutionResultDTO rejectResult = diagnoseService.resumeTicket(rejectRequest);

        // 3. 断言驳回回执与状态
        Assertions.assertNotNull(rejectResult);
        Assertions.assertEquals("REJECTED", rejectResult.getExecutionStatus(), "驳回动作执行状态必须为 REJECTED");
        Assertions.assertEquals("REJECTED_BY_SUPERVISOR", rejectResult.getActionName(), "动作名称应标记为 REJECTED_BY_SUPERVISOR");
        Assertions.assertTrue(rejectResult.getReceiptMessage().contains("主管已驳回"), "回执应包含主管已驳回提示");

        // 4. 断言 Redis 中 Checkpoint 状态为 REJECTED
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(traceId).build();
        Optional<Checkpoint> rejectCpOpt = redisSaver.get(runnableConfig);
        Assertions.assertTrue(rejectCpOpt.isPresent());
        Assertions.assertEquals("REJECTED", rejectCpOpt.get().getState().get(StateKeys.STATUS), "最终状态必须为 REJECTED");

        System.out.println("🎉 [测试用例 3 通过] 主管驳回安全拦截成功，安全跳过硬件下发，工单置为已作废！");
    }

    @Test
    @Order(4)
    @DisplayName("测试用例 4：Redisson 工单互斥锁并发防重断言 (testRedissonConcurrentLock)")
    public void testRedissonConcurrentLock() throws InterruptedException {
        String traceId = "trace-lock-" + UUID.randomUUID().toString().substring(0, 8);
        Long ticketId = 888888L;
        System.out.println("🚀 [测试用例 4] 启动 Redisson 工单互斥锁并发防重测试, ticketId: " + ticketId);

        // 1. 初始化挂起任务
        stateGraphService.runWorkflow(
                "RACK-A01",
                "精密空调压缩机跳闸过温告警",
                "000000",
                traceId
        );

        // 2. 模拟另一线程已预先获取到该 ticketId 的互斥锁
        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch releaseLockLatch = new CountDownLatch(1);
        Thread lockHolderThread = new Thread(() -> {
            RLock otherLock = redissonClient.getLock("aiops:ticket:lock:" + ticketId);
            otherLock.lock(10, TimeUnit.SECONDS);
            lockAcquiredLatch.countDown();
            try {
                releaseLockLatch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            } finally {
                if (otherLock.isHeldByCurrentThread()) {
                    otherLock.unlock();
                }
            }
        });
        lockHolderThread.start();
        boolean acquired = lockAcquiredLatch.await(5, TimeUnit.SECONDS);
        Assertions.assertTrue(acquired, "测试辅助线程必须成功持有工单锁");

        try {
            // 3. 此时发起审批，断言因锁已被其他线程持有而立即抛出 ServiceException
            ResumeActionRequestDTO request = new ResumeActionRequestDTO(
                    ticketId,
                    traceId,
                    "APPROVE",
                    "并发审批测试",
                    "supervisor_concurrent",
                    null
            );

            ServiceException ex = Assertions.assertThrows(ServiceException.class, () -> {
                diagnoseService.resumeTicket(request);
            }, "其他线程持有锁期间再次审批必须抛出 ServiceException");

            Assertions.assertTrue(ex.getMessage().contains("正在被其他值班长处理中"), "必须返回友好的互斥提示信息");
            System.out.println("✅ 成功拦截重复审批，异常提示: " + ex.getMessage());
        } finally {
            releaseLockLatch.countDown();
            lockHolderThread.join(2000);
        }

        // 4. 释放锁后，再次审批应能顺利恢复
        ResumeActionRequestDTO validRequest = new ResumeActionRequestDTO(
                ticketId,
                traceId,
                "APPROVE",
                "释放锁后正常审批",
                "supervisor_zhang",
                null
        );
        ActionExecutionResultDTO successResult = diagnoseService.resumeTicket(validRequest);
        Assertions.assertEquals("SUCCESS", successResult.getExecutionStatus(), "锁释放后应能正常恢复执行成功");

        System.out.println("🎉 [测试用例 4 通过] Redisson 分布式锁防重防并发拦截 100% 生效！");
    }
}
