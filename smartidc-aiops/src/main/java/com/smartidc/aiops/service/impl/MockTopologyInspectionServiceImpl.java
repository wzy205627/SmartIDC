package com.smartidc.aiops.service.impl;

import com.smartidc.aiops.service.TopologyInspectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 高仿动环拓扑探查服务本地 Mock 实现
 * 用于研发自测及离线排障演练，针对 A 区机柜模拟典型的高热联动与冷机跳闸特征
 */
@Service
@ConditionalOnProperty(prefix = "smartidc.aiops.topology", name = "mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockTopologyInspectionServiceImpl implements TopologyInspectionService {

    private static final Logger log = LoggerFactory.getLogger(MockTopologyInspectionServiceImpl.class);

    @Override
    public Map<String, Object> queryAdjacentRackMetrics(String rackCode) {
        log.info("🔍 [Mock动环拓扑] 探查相邻机柜动环指标, rackCode: {}", rackCode);
        Map<String, Object> result = new LinkedHashMap<>();
        String normalizedCode = (rackCode != null) ? rackCode.trim().toUpperCase() : "";

        // 模拟 A 区（如 RACK-A01）高热告警联动现场
        if (normalizedCode.contains("A") || normalizedCode.contains("RACK-A01")) {
            result.put("target_rack", rackCode);
            result.put("aisle", "COLD_AISLE_A");
            result.put("target_temperature", 41.5);
            result.put("target_humidity", 45.0);
            result.put("static_pressure_pa", 12.0);
            result.put("thermal_status", "HOTSPOT_ALARM");

            List<Map<String, Object>> adjacentRacks = new ArrayList<>();
            Map<String, Object> rackA02 = new LinkedHashMap<>();
            rackA02.put("rack_code", "RACK-A02");
            rackA02.put("temperature", 39.8);
            rackA02.put("humidity", 46.2);
            rackA02.put("status", "ALARM");
            adjacentRacks.add(rackA02);

            Map<String, Object> rackA03 = new LinkedHashMap<>();
            rackA03.put("rack_code", "RACK-A03");
            rackA03.put("temperature", 38.5);
            rackA03.put("humidity", 47.0);
            rackA03.put("status", "ALARM");
            adjacentRacks.add(rackA03);

            result.put("adjacent_racks", adjacentRacks);
            result.put("diagnosis_hint", "检测到同风道相邻机柜群发性温升，疑似精密空调制冷失效或冷风道静压不足引发热岛聚集");
        } else {
            // 普通正常机柜
            result.put("target_rack", rackCode);
            result.put("aisle", "COLD_AISLE_B");
            result.put("target_temperature", 22.5);
            result.put("target_humidity", 50.0);
            result.put("static_pressure_pa", 25.0);
            result.put("thermal_status", "NORMAL");

            List<Map<String, Object>> adjacentRacks = new ArrayList<>();
            Map<String, Object> rackB02 = new LinkedHashMap<>();
            rackB02.put("rack_code", "RACK-B02");
            rackB02.put("temperature", 22.8);
            rackB02.put("humidity", 51.0);
            rackB02.put("status", "NORMAL");
            adjacentRacks.add(rackB02);

            result.put("adjacent_racks", adjacentRacks);
            result.put("diagnosis_hint", "该区域温湿度及风道静压均处于健康指标范围内");
        }

        return result;
    }

    @Override
    public Map<String, Object> getPowerTopologyInfo(String rackCode) {
        log.info("🔍 [Mock动环拓扑] 查询机柜供电与空调拓扑, rackCode: {}", rackCode);
        Map<String, Object> result = new LinkedHashMap<>();
        String normalizedCode = (rackCode != null) ? rackCode.trim().toUpperCase() : "";

        if (normalizedCode.contains("A") || normalizedCode.contains("RACK-A01")) {
            result.put("rack_code", rackCode);
            result.put("power_loop_a", "UPS-FEEDER-A-01");
            result.put("power_loop_b", "UPS-FEEDER-B-01");
            result.put("ac_loop", "AC-LOOP-01");
            result.put("ac_voltage_v", 185.0); // 电压骤降
            result.put("bus_tie_switch", "OPEN"); // 母联开关分闸
            result.put("cooling_unit", "CRAC-A-01");
            result.put("cooling_status", "TRIPPED"); // 冷机跳闸
            result.put("status", "POWER_COOLING_ABNORMAL");
            result.put("topology_hint", "关联冷机 CRAC-A-01 发生跳闸，供电回路 AC-LOOP-01 电压骤降至 185V，母联开关未合闸");
        } else {
            result.put("rack_code", rackCode);
            result.put("power_loop_a", "UPS-FEEDER-A-02");
            result.put("power_loop_b", "UPS-FEEDER-B-02");
            result.put("ac_loop", "AC-LOOP-02");
            result.put("ac_voltage_v", 380.0);
            result.put("bus_tie_switch", "CLOSED");
            result.put("cooling_unit", "CRAC-B-01");
            result.put("cooling_status", "RUNNING");
            result.put("status", "NORMAL");
            result.put("topology_hint", "双路供电正常，精密空调回路与母联开关处于额定工作状态");
        }

        return result;
    }
}
