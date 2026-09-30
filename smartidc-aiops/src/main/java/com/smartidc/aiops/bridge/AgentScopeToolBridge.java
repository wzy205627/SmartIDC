package com.smartidc.aiops.bridge;

import com.smartidc.aiops.service.SopVectorStoreService;
import com.smartidc.aiops.tool.TopologyInspectionTools;
import com.smartidc.framework.tenant.TenantContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Spring AI @Tool 到 AgentScope Toolkit 的桥接工厂
 * 提供 MDC TraceId 恢复、多租户上下文隔离与异常自愈拦截包装
 */
@Component
public class AgentScopeToolBridge {

    private static final Logger log = LoggerFactory.getLogger(AgentScopeToolBridge.class);

    private final TopologyInspectionTools topologyTools;
    private final SopVectorStoreService sopVectorStoreService;

    public AgentScopeToolBridge(TopologyInspectionTools topologyTools, SopVectorStoreService sopVectorStoreService) {
        this.topologyTools = topologyTools;
        this.sopVectorStoreService = sopVectorStoreService;
    }

    /**
     * 针对单次排障会话，构建隔离且带安全防御代理的 AgentScope Toolkit
     *
     * @param traceId  链路追踪 ID
     * @param tenantId 租户 ID
     * @return 注入安全拦截代理的 AgentScope Toolkit
     */
    public Toolkit buildAgentToolkit(String traceId, String tenantId) {
        Toolkit toolkit = new Toolkit();
        // 注册拓扑探查与弹性 SOP 检索工具适配器（集成 Trace/租户透传与异常防御）
        toolkit.registerTool(new AgentScopeOamToolWrapper(traceId, tenantId, topologyTools, sopVectorStoreService, this));
        return toolkit;
    }

    /**
     * 防御性执行代理：透传 Trace/租户上下文 + 异常自愈语义包装
     */
    public Object safeExecute(String traceId, String tenantId, Supplier<Object> action) {
        try {
            if (traceId != null) MDC.put("trace_id", traceId);
            if (tenantId != null) TenantContext.setTenantId(tenantId);

            return action.get();
        } catch (Exception e) {
            log.warn("⚠️ [AgentScopeToolBridge] 内部工具执行异常被安全捕获: {}", e.getMessage());
            return "【工具探查提示】: 无法获取目标数据 (" + e.getMessage() + ")，请确认参数格式 (如 RACK-A01) 或更换探查手段。";
        } finally {
            MDC.remove("trace_id");
            TenantContext.clear();
        }
    }

    /**
     * 适配 AgentScope 规约的工具包装类
     */
    public static class AgentScopeOamToolWrapper {
        private final String traceId;
        private final String tenantId;
        private final TopologyInspectionTools topologyTools;
        private final SopVectorStoreService sopVectorStoreService;
        private final AgentScopeToolBridge bridge;

        public AgentScopeOamToolWrapper(
                String traceId,
                String tenantId,
                TopologyInspectionTools topologyTools,
                SopVectorStoreService sopVectorStoreService,
                AgentScopeToolBridge bridge) {
            this.traceId = traceId;
            this.tenantId = tenantId;
            this.topologyTools = topologyTools;
            this.sopVectorStoreService = sopVectorStoreService;
            this.bridge = bridge;
        }

        @Tool(name = "queryAdjacentRackMetrics", description = "根据机柜编码查询同机房同风道相邻机柜的实时温湿度指标与告警状态")
        public String queryAdjacentRackMetrics(@ToolParam(name = "rackCode", description = "目标机柜物理编码，如 RACK-A01") String rackCode) {
            return String.valueOf(bridge.safeExecute(traceId, tenantId, () -> topologyTools.queryAdjacentRackMetrics(rackCode)));
        }

        @Tool(name = "getPowerTopologyInfo", description = "根据机柜编码查询供电母线、PDU及精密空调回路拓扑状态")
        public String getPowerTopologyInfo(@ToolParam(name = "rackCode", description = "目标机柜物理编码，如 RACK-A01") String rackCode) {
            return String.valueOf(bridge.safeExecute(traceId, tenantId, () -> topologyTools.getPowerTopologyInfo(rackCode)));
        }

        @Tool(name = "retrieveSopGuide", description = "根据故障现象或排障动作描述检索匹配最相关的 IDC 应急 SOP 预案")
        public Object retrieveSopGuide(@ToolParam(name = "query", description = "故障现象或排障动作描述") String query) {
            return bridge.safeExecute(traceId, tenantId, () -> sopVectorStoreService.retrieveTopSopGuide(query, 3, 0.50));
        }
    }
}
