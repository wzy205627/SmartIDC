package com.smartidc.biz.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartidc.biz.domain.entity.IdcTelemetrySnapshot;
import com.smartidc.biz.domain.vo.TelemetryTimelineVO;
import com.smartidc.iot.buffer.TelemetryPersistenceHandler;
import com.smartidc.iot.domain.dto.TelemetryMetricDTO;

import java.util.List;
import java.util.Map;

/**
 * 动环遥测时序快照服务接口
 */
public interface IdcTelemetrySnapshotService extends IService<IdcTelemetrySnapshot>, TelemetryPersistenceHandler {

    /**
     * 批量持久化物联遥测快照 (批量削峰写入)
     *
     * @param metrics 物联防腐指标集合
     */
    void batchSave(List<TelemetryMetricDTO> metrics);

    /**
     * 查询指定机柜最新动环遥测状态 (优先查 Redis 热缓存，回退快照库)
     *
     * @param rackCode 机架编码 (如: A-03)
     * @return 最新动环指标字典
     */
    Map<Object, Object> getLatestByRackCode(String rackCode);

    /**
     * 查询指定机柜动环历史时序列表
     *
     * @param rackCode 机架编码
     * @param limit    返回条数上限
     * @return 历史快照列表
     */
    List<IdcTelemetrySnapshot> getHistoryByRackCode(String rackCode, int limit);

    /**
     * 查询指定机柜的多轴动环历史时序回溯与降采样数据
     *
     * @param rackCode  机架编码 (如: A-03)
     * @param timeRange 时间范围 (1h, 6h, 24h, 7d)
     * @param metrics   指标列表 (可选，逗号分隔)
     * @return 降采样后的时序视图对象
     */
    TelemetryTimelineVO getTimelineByRackCode(String rackCode, String timeRange, String metrics);
}
