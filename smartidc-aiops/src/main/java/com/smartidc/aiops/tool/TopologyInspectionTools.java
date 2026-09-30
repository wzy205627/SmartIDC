package com.smartidc.aiops.tool;

import com.alibaba.fastjson2.JSON;
import com.smartidc.aiops.service.TopologyInspectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 动环与供电拓扑探查工具组件
 * 统一采用 Spring AI @Tool 作为单一事实基准暴露，供 AIOps 状态图与 AgentScope 桥接调用
 */
@Component
public class TopologyInspectionTools {

    private static final Logger log = LoggerFactory.getLogger(TopologyInspectionTools.class);

    private final TopologyInspectionService topologyInspectionService;

    public TopologyInspectionTools(TopologyInspectionService topologyInspectionService) {
        this.topologyInspectionService = topologyInspectionService;
    }

    @Tool(name = "queryAdjacentRackMetrics", description = "根据机柜编码查询同机房同风道相邻机柜的实时温湿度指标与告警状态")
    public String queryAdjacentRackMetrics(@ToolParam(description = "目标机柜物理编码，如 RACK-A01") String rackCode) {
        log.info("🛠️ [@Tool queryAdjacentRackMetrics] 触发探查, rackCode: {}", rackCode);
        Map<String, Object> metrics = topologyInspectionService.queryAdjacentRackMetrics(rackCode);
        return JSON.toJSONString(metrics);
    }

    @Tool(name = "getPowerTopologyInfo", description = "根据机柜编码查询供电母线、PDU及精密空调回路拓扑状态")
    public String getPowerTopologyInfo(@ToolParam(description = "目标机柜物理编码，如 RACK-A01") String rackCode) {
        log.info("🛠️ [@Tool getPowerTopologyInfo] 触发探查, rackCode: {}", rackCode);
        Map<String, Object> topologyInfo = topologyInspectionService.getPowerTopologyInfo(rackCode);
        return JSON.toJSONString(topologyInfo);
    }
}
