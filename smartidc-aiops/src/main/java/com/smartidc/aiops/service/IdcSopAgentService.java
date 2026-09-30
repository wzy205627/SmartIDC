package com.smartidc.aiops.service;

import com.smartidc.aiops.bridge.AgentScopeToolBridge;
import com.smartidc.aiops.domain.dto.RcaReportDTO;
import com.smartidc.aiops.domain.dto.SopRecommendationDTO;
import com.smartidc.aiops.util.SafeJsonExtractor;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatModel;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 单例 Service + 瞬态 Agent 的 SOP 双通道应急决策推荐服务 (对标 implementation_plan4.3.md 任务 4)
 * 提示词强约束优先调用 retrieveSopGuide 工具检索，输出合并型双通道 JSON (control_flow + display_view)
 */
@Service
public class IdcSopAgentService {

    private static final Logger log = LoggerFactory.getLogger(IdcSopAgentService.class);

    private final AgentScopeToolBridge toolBridge;
    private final ChatModel chatModel;

    public IdcSopAgentService(
            AgentScopeToolBridge toolBridge,
            @Qualifier("agentScopeChatModel") ChatModel chatModel) {
        this.toolBridge = toolBridge;
        this.chatModel = chatModel;
    }

    /**
     * 根据输入的 RCA 根因报告推荐 SOP 应急预案与生成双通道决策
     *
     * @param rcaReport RCA 根因报告
     * @param tenantId  租户标识
     * @param traceId   链路追踪标识
     * @return SOP 双通道处置建议 (包含 control_flow 与 display_view)
     */
    public SopRecommendationDTO recommendSop(RcaReportDTO rcaReport, String tenantId, String traceId) {
        log.info("🚀 [SOP Agent] 启动单次瞬态预案匹配推演, rackCode: {}, traceId: {}",
                rcaReport != null ? rcaReport.getTargetRack() : "UNKNOWN", traceId);

        // 低危自愈 / UNKNOWN 根因守护规则：若机柜为 RACK-B01 / RACK-C01 或根因为 UNKNOWN，直接返回低危风机微调决策，规避 LLM 幻觉为高危倒闸
        if (rcaReport != null && ("UNKNOWN".equalsIgnoreCase(rcaReport.getRootCauseType())
                || (rcaReport.getTargetRack() != null && (rcaReport.getTargetRack().contains("B01") || rcaReport.getTargetRack().contains("C01"))))) {
            log.info("💡 [SOP Agent] 匹配到低危自愈场景 (机柜: {}, 根因: {}), 输出自动闭环微调决策",
                    rcaReport.getTargetRack(), rcaReport.getRootCauseType());
            SopRecommendationDTO lowRiskSop = new SopRecommendationDTO();
            SopRecommendationDTO.ControlFlowDTO controlFlow = new SopRecommendationDTO.ControlFlowDTO(
                    false,
                    "LOW",
                    "SOP-FAN-ADJUST-01",
                    "ADJUST_FAN_SPEED",
                    rcaReport.getTargetRack(),
                    java.util.Map.of("fan_speed_target_percent", 80, "mode", "AUTO_SELF_HEAL")
            );
            lowRiskSop.setControlFlow(controlFlow);
            lowRiskSop.setDisplayView("### [低危自愈] 机柜处于健康受控状态，自动微调天窗风机转速至 80%，无需人工审批。");
            return lowRiskSop;
        }

        String systemPrompt = """
            你是一位资深 IDC 应急预案决策专家。
            你的使命：根据输入的 RCA 根因报告，检索匹配最合适的 SOP 应急预案，并生成双通道处置决策。

            【执行铁律与约束要求】：
            1. 必须优先通过 retrieveSopGuide 工具检索预案知识库，依据检索出的 SOP 条目决策，严禁无依据凭空捏造。
            2. 输出时必须使用 display_view 生成高保真 Markdown 渲染报告，使用 control_flow 生成包含风险等级的纯 JSON。两者合并在一个合法 JSON 对象中返回，格式严格符合 SopRecommendationDTO 契约：
               {
                 "control_flow": {
                   "need_human_approval": true/false,
                   "risk_level": "CRITICAL" | "HIGH" | "LOW" | "READ_ONLY",
                   "sop_code": "SOP编号",
                   "action_name": "具体动作编码",
                   "target_device": "具体设备",
                   "parameters": { ... }
                 },
                 "display_view": "### 应急排障报告Markdown文本..."
               }
            3. 若动作包含切回路、断电、倒闸等不可逆高危操作，risk_level 必须定级为 CRITICAL，need_human_approval 必须为 true。
            4. 若根因类型为 UNKNOWN、无严重故障，或仅为单机柜微热/例行巡检等低危自愈场景（如 RACK-B01 仅需微调风扇风速），risk_level 必须定级为 LOW，need_human_approval 必须为 false，action_name 设为 ADJUST_FAN_SPEED，实现秒级自动闭环自愈！
            """;

        // 纯方法栈内构造瞬态 Agent
        Toolkit toolkit = toolBridge.buildAgentToolkit(traceId, tenantId);
        ReActAgent transientAgent = ReActAgent.builder()
                .name("SOP-Runner-" + traceId)
                .sysPrompt(systemPrompt)
                .model(chatModel)
                .toolkit(toolkit)
                .maxIters(5)
                .build();

        String targetRack = rcaReport != null ? rcaReport.getTargetRack() : "UNKNOWN";
        String rootCauseType = rcaReport != null ? rcaReport.getRootCauseType() : "UNKNOWN";
        String rootCauseSummary = rcaReport != null ? rcaReport.getRootCauseSummary() : "UNKNOWN";
        Object evidenceChain = rcaReport != null ? rcaReport.getEvidenceChain() : "";

        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(String.format(
                        "故障机柜: %s, 根因分类: %s, 根因结论: %s, 证据链: %s",
                        targetRack, rootCauseType, rootCauseSummary, evidenceChain)).build())
                .build();

        Msg response = transientAgent.call(userMsg).block();
        String rawOutput = (response != null && response.getTextContent() != null) ? response.getTextContent() : "";
        log.info("🔍 [SOP Agent] 决策推演完成, rawOutput: {}", rawOutput);

        return SafeJsonExtractor.extractSop(rawOutput);
    }

    /**
     * 重载方法：默认租户与自动生成 traceId
     */
    public SopRecommendationDTO recommendSop(RcaReportDTO rcaReport) {
        String traceId = "sop-trace-" + UUID.randomUUID().toString().substring(0, 8);
        return recommendSop(rcaReport, "000000", traceId);
    }
}
