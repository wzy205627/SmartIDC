package com.smartidc.aiops.service.impl;

import com.smartidc.aiops.service.TopologyInspectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 真实硬件动环拓扑探查服务实现（预留接入 Phase 2 Redis 遥测缓存与 Prometheus/Modbus）
 */
@Service
@ConditionalOnProperty(prefix = "smartidc.aiops.topology", name = "mock-enabled", havingValue = "false")
public class HardwareTopologyInspectionServiceImpl implements TopologyInspectionService {

    private static final Logger log = LoggerFactory.getLogger(HardwareTopologyInspectionServiceImpl.class);

    @Override
    public Map<String, Object> queryAdjacentRackMetrics(String rackCode) {
        log.info("🔌 [Hardware动环拓扑] 查询真实硬件遥测指标, rackCode: {}", rackCode);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rack_code", rackCode);
        result.put("source", "HARDWARE_TELEMETRY");
        result.put("status", "NOT_CONFIGURED");
        result.put("message", "真实硬件数据源尚未激活，请在配置中开启 smartidc.aiops.topology.mock-enabled=true 进行仿真自测");
        return result;
    }

    @Override
    public Map<String, Object> getPowerTopologyInfo(String rackCode) {
        log.info("🔌 [Hardware动环拓扑] 查询真实供电拓扑, rackCode: {}", rackCode);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rack_code", rackCode);
        result.put("source", "HARDWARE_TOPOLOGY");
        result.put("status", "NOT_CONFIGURED");
        result.put("message", "真实电力监控数据源尚未激活，请在配置中开启 smartidc.aiops.topology.mock-enabled=true 进行仿真自测");
        return result;
    }
}
