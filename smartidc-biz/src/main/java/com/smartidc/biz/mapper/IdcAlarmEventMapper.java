package com.smartidc.biz.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartidc.biz.domain.entity.IdcAlarmEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 动环告警事件持久层 Mapper
 */
@Mapper
public interface IdcAlarmEventMapper extends BaseMapper<IdcAlarmEvent> {

    /**
     * 忽略租户隔离，查询指定机架当前处于触发中或处理中的未消除活动告警 (供微画像红灯先亮聚合)
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM idc_alarm_event WHERE rack_id = #{rackId} AND status IN (1, 2) ORDER BY trigger_time DESC")
    List<IdcAlarmEvent> selectActiveAlarmsByRackId(@Param("rackId") Long rackId);
}
