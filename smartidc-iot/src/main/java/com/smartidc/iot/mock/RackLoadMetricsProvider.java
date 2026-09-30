package com.smartidc.iot.mock;

import java.util.List;

/**
 * 机架在架负荷指标与机房机柜全域发现接口 (SPI)
 * 由 smartidc-biz 模块提供基于实际在架设备台账与额定功耗的真实物理量计算，
 * 支持跨多租户动态发现全机房在管物理机柜。
 */
public interface RackLoadMetricsProvider {

    /**
     * 获取全机房所有在管机柜元数据清单 (跨租户动态发现)
     *
     * @return 全量在管机柜元数据列表
     */
    List<ManagedRackMeta> listAllManagedRacks();

    /**
     * 获取指定机柜当前的在架物理负荷画像
     *
     * @param tenantId 租户编号
     * @param rackCode 机柜编号 (如 "A-03", "B-01")
     * @return 机架当前在架负荷
     */
    RackLoadInfo getRackLoad(String tenantId, String rackCode);
}
