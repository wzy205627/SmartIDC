package com.smartidc.aiops.service;

import java.util.Map;

public interface TopologyInspectionService {
    /**
     * 查询指定机柜及其相邻同风道机柜的实时动环指标（温湿度、风道静压）
     *
     * @param rackCode 机柜编码，如 RACK-A01
     * @return 包含目标机柜及相邻机柜的温湿度、风道静压指标
     */
    Map<String, Object> queryAdjacentRackMetrics(String rackCode);

    /**
     * 查询机柜的供电回路、母联开关及精密空调对应管路状态
     *
     * @param rackCode 机柜编码，如 RACK-A01
     * @return 包含供电回路、母联开关、精密空调管路等拓扑状态
     */
    Map<String, Object> getPowerTopologyInfo(String rackCode);
}
