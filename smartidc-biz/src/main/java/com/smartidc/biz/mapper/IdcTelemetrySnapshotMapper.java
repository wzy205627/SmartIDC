package com.smartidc.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartidc.biz.domain.entity.IdcTelemetrySnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 动环遥测时序快照持久层 Mapper
 */
@Mapper
public interface IdcTelemetrySnapshotMapper extends BaseMapper<IdcTelemetrySnapshot> {

    /**
     * 高性能单 SQL 批量插入快照
     *
     * @param list 快照集合
     * @return 影响行数
     */
    int insertBatch(@Param("list") List<IdcTelemetrySnapshot> list);

    /**
     * 查询指定机架最近一条离线历史心跳快照
     */
    @Select("SELECT * FROM idc_telemetry_snapshot WHERE rack_id = #{rackId} ORDER BY sample_time DESC LIMIT 1")
    IdcTelemetrySnapshot selectLatestSnapshotByRackId(@Param("rackId") Long rackId);
}
