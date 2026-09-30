package com.smartidc.aiops;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.smartidc.aiops.domain.dto.RcaReportDTO;
import com.smartidc.aiops.domain.dto.SopRecommendationDTO;
import com.smartidc.aiops.graph.IdcAioPsStateGraphService;
import com.smartidc.aiops.memory.LongTermMemoryStore;
import com.smartidc.aiops.service.IdcRcaAgentService;
import com.smartidc.aiops.service.IdcSopAgentService;
import com.smartidc.aiops.service.SopSkillLoaderService;
import com.smartidc.aiops.util.SafeJsonExtractor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

/**
 * 智维云 (SmartIDC) 阶段 4.3：内层 AgentScope 微智能体、外层双节点解耦与双层记忆架构冒烟集成测试
 * 对标 implementation_plan4.3.md 任务 6
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase4MultiAgentWorkflowSmokeTest {

    @Autowired
    private SopSkillLoaderService sopSkillLoaderService;

    @Autowired
    private LongTermMemoryStore longTermMemoryStore;

    @Autowired
    private IdcRcaAgentService idcRcaAgentService;

    @Autowired
    private IdcSopAgentService idcSopAgentService;

    @Autowired
    private IdcAioPsStateGraphService idcAioPsStateGraphService;

    @Test
    @Order(1)
    @DisplayName("测试用例 1：SafeJsonExtractor 正则与首尾花括号截取容错测试")
    public void testSafeJsonExtractor() {
        // 场景 A: 带 Markdown 代码块 ```json ... ``` 且包含前后缀寒暄客套
        String rawWithCodeBlock = """
                好的，已为您排查完毕：

                ```json
                {
                  "fault_id": "F1",
                  "target_rack": "A01"
                }
                ```
                请审阅以上结论。祝您工作顺利！
                """;
        RcaReportDTO rca1 = SafeJsonExtractor.extractRca(rawWithCodeBlock);
        Assertions.assertNotNull(rca1);
        Assertions.assertEquals("F1", rca1.getFaultId());
        Assertions.assertEquals("A01", rca1.getTargetRack());

        // 场景 B: 无代码块标签，仅有首尾花括号 { ... } 且包含前后缀废话
        String rawWithBracesOnly = "经研判得出结论如下：{\"fault_id\":\"F2\",\"target_rack\":\"A02\"} 祝您工作顺利！";
        RcaReportDTO rca2 = SafeJsonExtractor.extractRca(rawWithBracesOnly);
        Assertions.assertNotNull(rca2);
        Assertions.assertEquals("F2", rca2.getFaultId());
        Assertions.assertEquals("A02", rca2.getTargetRack());

        // 场景 C: SOP 双通道推荐 JSON 解析
        String rawSop = """
                ```json
                {
                  "control_flow": {
                    "need_human_approval": true,
                    "risk_level": "CRITICAL",
                    "sop_code": "SOP-COOLING-SWITCH-01",
                    "action_name": "SWITCH_TO_BACKUP_CIRCUIT",
                    "target_device": "AC-PRECISION-01"
                  },
                  "display_view": "### 应急排障处置建议\\n- 建议切换冷机至备用B回路"
                }
                ```
                """;
        SopRecommendationDTO sopRec = SafeJsonExtractor.extractSop(rawSop);
        Assertions.assertNotNull(sopRec);
        Assertions.assertNotNull(sopRec.getControlFlow());
        Assertions.assertTrue(sopRec.getControlFlow().getNeedHumanApproval());
        Assertions.assertEquals("CRITICAL", sopRec.getControlFlow().getRiskLevel());
        Assertions.assertEquals("SOP-COOLING-SWITCH-01", sopRec.getControlFlow().getSopCode());
        Assertions.assertTrue(sopRec.getDisplayView().contains("切换冷机至备用B回路"));
        System.out.println("✅ 测试用例 1 通过：SafeJsonExtractor 正则与首尾花括号双重清洗容错 100% 成功！");
    }

    @Test
    @Order(2)
    @DisplayName("测试用例 2：长期记忆 category 与租户过滤隔离测试")
    public void testLongTermMemoryFilteringAndTenantIsolation() {
        // 1. 确保库中已存在 4.2 的 SOP 规程切片 (若无则加载)
        sopSkillLoaderService.loadAndEmbedAllSkills();

        String testRack = "RACK-TEST-" + UUID.randomUUID().toString().substring(0, 8);
        String testTenantId = "tenant-" + UUID.randomUUID().toString().substring(0, 6);
        String otherTenantId = "other-" + UUID.randomUUID().toString().substring(0, 6);

        // 2. 调用长期记忆检索：由于强制过滤 category='HISTORICAL_INSIGHT' 与 tenant_id，绝不会误召回 SOP 规程原文
        String beforeInsight = longTermMemoryStore.retrieveHistoricalInsights(testRack, "过温跳闸", testTenantId);
        System.out.println("✅ 记忆库检索结果 (写入前): " + beforeInsight);
        Assertions.assertTrue(beforeInsight.contains("暂无针对该机柜的历史异常处置病历"),
                "未录入历史病历时，绝不可把 4.2 的 SOP 规程当做病历误召回");

        // 3. 录入一条新的排障成功病历
        longTermMemoryStore.recordNewInsight(
                testRack,
                "COOLING_FAILURE",
                "冷机压缩机跳闸后，值班人员已手动闭合备用B回路恢复送风",
                testTenantId
        );

        // 4. 使用相同租户再次检索，断言精准召回该历史经验
        String afterInsight = longTermMemoryStore.retrieveHistoricalInsights(testRack, "过温跳闸", testTenantId);
        System.out.println("✅ 记忆库检索结果 (写入后): " + afterInsight);
        Assertions.assertTrue(afterInsight.contains("历史案例参考"), "应当成功召回历史病历");
        Assertions.assertTrue(afterInsight.contains("手动闭合备用B回路"), "召回内容应包含处置经验");

        // 5. 使用其他租户检索，断言多租户物理隔离（无法读取该租户的数据）
        String crossTenantInsight = longTermMemoryStore.retrieveHistoricalInsights(testRack, "过温跳闸", otherTenantId);
        System.out.println("✅ 跨租户检索结果: " + crossTenantInsight);
        Assertions.assertTrue(crossTenantInsight.contains("暂无针对该机柜的历史异常处置病历"),
                "不同租户之间必须严格隔离，不可跨租户穿透");
        System.out.println("✅ 测试用例 2 通过：长期记忆 category 类别强过滤与租户 tenant_id 隔离验证成功！");
    }

    @Test
    @Order(3)
    @DisplayName("测试用例 3：瞬态 Agent 零记忆串线验证")
    public void testTransientAgentZeroMemoryLeak() {
        // 1. 针对 A01 执行诊断
        RcaReportDTO rcaA01 = idcRcaAgentService.diagnoseRack("RACK-A01", "过温跳闸", "000000", "trace-smoke-a01");
        Assertions.assertNotNull(rcaA01);
        Assertions.assertEquals("RACK-A01", rcaA01.getTargetRack());
        System.out.println("✅ A01 诊断结果: " + rcaA01);

        // 2. 连续针对 B01（正常机柜）执行诊断
        RcaReportDTO rcaB01 = idcRcaAgentService.diagnoseRack("RACK-B01", "例行健康巡检", "000000", "trace-smoke-b01");
        Assertions.assertNotNull(rcaB01);
        Assertions.assertEquals("RACK-B01", rcaB01.getTargetRack());
        System.out.println("✅ B01 诊断结果: " + rcaB01);

        // 断言 B01 绝不出现 A01 的残留记忆与幻觉
        Assertions.assertNotEquals(rcaA01.getTargetRack(), rcaB01.getTargetRack());
        Assertions.assertFalse(
                rcaB01.getRootCauseSummary() != null && rcaB01.getRootCauseSummary().contains("A01"),
                "B01 的诊断结论中绝不能出现 A01 的残留记忆或幻觉"
        );
        System.out.println("✅ 测试用例 3 通过：方法栈瞬态 Agent 实例无状态残留，零记忆串线！");
    }

    @Test
    @Order(4)
    @DisplayName("测试用例 4：外层 StateGraph 双节点贯通与定级测试")
    public void testStateGraphEndToEndWorkflow() {
        OverAllState finalState = idcAioPsStateGraphService.runWorkflow(
                "RACK-A01",
                "精密空调压缩机跳闸导致风道过温",
                "000000",
                "trace-workflow-001"
        );

        Assertions.assertNotNull(finalState);
        Assertions.assertTrue(finalState.value("rca_report", RcaReportDTO.class).isPresent(), "全局 State 必须包含 rca_report");
        Assertions.assertTrue(finalState.value("sop_recommendation", SopRecommendationDTO.class).isPresent(), "全局 State 必须包含 sop_recommendation");
        Assertions.assertTrue(finalState.value("status", String.class).isPresent(), "全局 State 必须包含 status");

        String status = finalState.value("status", String.class).get();
        System.out.println("✅ StateGraph 全流程执行完毕，最终决策状态: " + status);
        Assertions.assertTrue("PENDING_APPROVAL".equals(status) || "HITL_SUSPENDED".equals(status),
                "针对高危冷机跳闸操作，状态应定级为 PENDING_APPROVAL 或 HITL_SUSPENDED");
        System.out.println("✅ 测试用例 4 通过：外层 StateGraph RCA ➔ SOP ➔ RiskAudit 流程贯通且正确定级！");
    }
}
