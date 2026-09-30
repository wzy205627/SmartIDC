package com.smartidc.biz.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartidc.biz.domain.entity.IdcAlarmEvent;

import java.util.List;
import java.util.Map;

/**
 * 动环告警事件服务接口
 */
public interface IdcAlarmEventService extends IService<IdcAlarmEvent> {

    /**
     * 创建或升级告警事件
     *
     * @param tenantId    租户ID
     * @param rackId      机柜ID
     * @param rackCode    机架编码
     * @param roomName    机房区域
     * @param alarmLevel  告警等级 (WARNING, CRITICAL)
     * @param alarmType   告警类型 (TEMP_HIGH, POWER_FAIL)
     * @param metricValue 采样值
     * @param summary     根因或空间聚合描述
     * @return 告警实体
     */
    IdcAlarmEvent createOrEscalateAlarm(String tenantId, Long rackId, String rackCode, String roomName,
                                        String alarmLevel, String alarmType, String metricValue, String summary);

    /**
     * 自动消警 (指标稳定恢复)
     *
     * @param alarmId 告警ID
     */
    void clearAlarm(Long alarmId);

    /**
     * 人工关闭告警
     *
     * @param alarmId 告警ID
     */
    void closeAlarm(Long alarmId);

    /**
     * 标记为误报
     *
     * @param alarmId 告警ID
     */
    void markFalsePositive(Long alarmId);

    /**
     * 查询当前触发中的活动告警 (status = 1)
     *
     * @param roomName 可选按机房筛选
     * @return 活动告警列表
     */
    List<IdcAlarmEvent> listActiveAlarms(String roomName);

    /**
     * 查询当前触发中的活动告警 (status = 1)，支持按机房、机柜ID或机架编码过滤
     *
     * @param roomName 可选按机房筛选
     * @param rackId   可选按机柜ID筛选
     * @param rackCode 可选按机架编码筛选
     * @return 活动告警列表
     */
    List<IdcAlarmEvent> listActiveAlarms(String roomName, Long rackId, String rackCode);

    /**
     * 获取动环大屏告警态势统计
     *
     * @return 统计字典 (总活动数、严重数、预警数、已消警数)
     */
    Map<String, Object> getAlarmStats();
}
