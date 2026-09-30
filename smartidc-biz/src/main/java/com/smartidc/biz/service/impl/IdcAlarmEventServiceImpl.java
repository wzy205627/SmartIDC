package com.smartidc.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartidc.biz.domain.entity.IdcAlarmEvent;
import com.smartidc.biz.domain.entity.IdcRack;
import com.smartidc.biz.domain.event.AlarmClearedEvent;
import com.smartidc.biz.domain.event.AlarmTriggeredEvent;
import com.smartidc.biz.mapper.IdcAlarmEventMapper;
import com.smartidc.biz.mapper.IdcRackMapper;
import com.smartidc.biz.service.IdcAlarmEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动环告警事件服务实现类
 */
@Service
public class IdcAlarmEventServiceImpl extends ServiceImpl<IdcAlarmEventMapper, IdcAlarmEvent>
        implements IdcAlarmEventService {

    private static final Logger log = LoggerFactory.getLogger(IdcAlarmEventServiceImpl.class);

    private final IdcAlarmEventMapper alarmEventMapper;
    private final IdcRackMapper rackMapper;
    private final ApplicationEventPublisher eventPublisher;

    public IdcAlarmEventServiceImpl(IdcAlarmEventMapper alarmEventMapper,
                                   IdcRackMapper rackMapper,
                                   ApplicationEventPublisher eventPublisher) {
        this.alarmEventMapper = alarmEventMapper;
        this.rackMapper = rackMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public IdcAlarmEvent createOrEscalateAlarm(String tenantId, Long rackId, String rackCode, String roomName,
                                              String alarmLevel, String alarmType, String metricValue, String summary) {
        // 查询该机柜是否已有处于触发中 (status=1) 的同类告警
        List<IdcAlarmEvent> activeList = alarmEventMapper.selectList(
                new LambdaQueryWrapper<IdcAlarmEvent>()
                        .eq(IdcAlarmEvent::getRackId, rackId)
                        .eq(IdcAlarmEvent::getAlarmType, alarmType)
                        .eq(IdcAlarmEvent::getStatus, 1)
                        .last("LIMIT 1")
        );

        IdcAlarmEvent event;
        if (!activeList.isEmpty()) {
            // 已存在活动告警 -> 更新指标、等级升格与摘要说明
            event = activeList.get(0);
            event.setMetricValue(metricValue);
            if ("CRITICAL".equalsIgnoreCase(alarmLevel)) {
                event.setAlarmLevel("CRITICAL");
            }
            if (summary != null && !summary.isBlank()) {
                event.setRcaSummary(summary);
            }
            alarmEventMapper.updateById(event);
            log.info("[AlarmService] 更新/升格现有活动告警: alarmId={}, rackCode={}, level={}",
                    event.getAlarmId(), rackCode, event.getAlarmLevel());
        } else {
            // 不存在 -> 创建全新告警记录
            event = new IdcAlarmEvent();
            event.setTenantId(tenantId != null ? tenantId : "000000");
            event.setDeviceId(0L);
            event.setRackId(rackId);
            event.setAlarmLevel(alarmLevel != null ? alarmLevel : "WARNING");
            event.setAlarmType(alarmType);
            event.setMetricValue(metricValue);
            event.setStatus(1); // 1-触发中
            event.setRcaSummary(summary);
            event.setTriggerTime(LocalDateTime.now());

            alarmEventMapper.insert(event);
            log.warn("[AlarmService] 新建活动告警成功: alarmId=[{}], 机柜=[{}], 等级=[{}], 类型=[{}], 数值=[{}]",
                    event.getAlarmId(), rackCode, event.getAlarmLevel(), event.getAlarmType(), event.getMetricValue());
        }

        // 派发领域事件 (供后续 WebSocket 实时推屏与 AIOps 使用)
        eventPublisher.publishEvent(new AlarmTriggeredEvent(this, event, rackCode, roomName));

        return event;
    }

    @Override
    public void clearAlarm(Long alarmId) {
        if (alarmId == null) {
            return;
        }
        IdcAlarmEvent alarm = alarmEventMapper.selectById(alarmId);
        if (alarm != null && alarm.getStatus() == 1) {
            alarm.setStatus(3); // 3-已消除
            alarm.setClearTime(LocalDateTime.now());
            alarmEventMapper.updateById(alarm);

            log.info("[AlarmService] 告警自动消警成功: alarmId={}, rackId={}, clearTime={}",
                    alarmId, alarm.getRackId(), alarm.getClearTime());

            eventPublisher.publishEvent(new AlarmClearedEvent(this, alarm, String.valueOf(alarm.getRackId())));
        }
    }

    @Override
    public void closeAlarm(Long alarmId) {
        if (alarmId == null) {
            return;
        }
        IdcAlarmEvent alarm = alarmEventMapper.selectById(alarmId);
        if (alarm != null && alarm.getStatus() == 1) {
            alarm.setStatus(3);
            alarm.setClearTime(LocalDateTime.now());
            alarmEventMapper.updateById(alarm);
            log.info("[AlarmService] 人工关闭告警: alarmId={}", alarmId);
            eventPublisher.publishEvent(new AlarmClearedEvent(this, alarm, String.valueOf(alarm.getRackId())));
        }
    }

    @Override
    public void markFalsePositive(Long alarmId) {
        if (alarmId == null) {
            return;
        }
        IdcAlarmEvent alarm = alarmEventMapper.selectById(alarmId);
        if (alarm != null) {
            alarm.setStatus(4); // 4-已标记误报
            alarm.setClearTime(LocalDateTime.now());
            alarmEventMapper.updateById(alarm);
            log.info("[AlarmService] 告警标记为误报: alarmId={}", alarmId);
            eventPublisher.publishEvent(new AlarmClearedEvent(this, alarm, String.valueOf(alarm.getRackId())));
        }
    }

    @Override
    public List<IdcAlarmEvent> listActiveAlarms(String roomName) {
        return listActiveAlarms(roomName, null, null);
    }

    @Override
    public List<IdcAlarmEvent> listActiveAlarms(String roomName, Long rackId, String rackCode) {
        LambdaQueryWrapper<IdcAlarmEvent> wrapper = new LambdaQueryWrapper<IdcAlarmEvent>()
                .eq(IdcAlarmEvent::getStatus, 1);

        if (rackId != null) {
            wrapper.eq(IdcAlarmEvent::getRackId, rackId);
        } else if (rackCode != null && !rackCode.isBlank()) {
            IdcRack rack = rackMapper.selectOne(
                    new LambdaQueryWrapper<IdcRack>().eq(IdcRack::getRackCode, rackCode).last("LIMIT 1")
            );
            if (rack != null) {
                wrapper.eq(IdcAlarmEvent::getRackId, rack.getRackId());
            } else {
                return List.of();
            }
        }

        wrapper.orderByDesc(IdcAlarmEvent::getTriggerTime);
        return alarmEventMapper.selectList(wrapper);
    }

    @Override
    public Map<String, Object> getAlarmStats() {
        Long activeCount = alarmEventMapper.selectCount(
                new LambdaQueryWrapper<IdcAlarmEvent>().eq(IdcAlarmEvent::getStatus, 1)
        );
        Long criticalCount = alarmEventMapper.selectCount(
                new LambdaQueryWrapper<IdcAlarmEvent>()
                        .eq(IdcAlarmEvent::getStatus, 1)
                        .eq(IdcAlarmEvent::getAlarmLevel, "CRITICAL")
        );
        Long warningCount = alarmEventMapper.selectCount(
                new LambdaQueryWrapper<IdcAlarmEvent>()
                        .eq(IdcAlarmEvent::getStatus, 1)
                        .eq(IdcAlarmEvent::getAlarmLevel, "WARNING")
        );
        Long clearedCount = alarmEventMapper.selectCount(
                new LambdaQueryWrapper<IdcAlarmEvent>().eq(IdcAlarmEvent::getStatus, 3)
        );

        Map<String, Object> stats = new HashMap<>();
        stats.put("activeTotal", activeCount != null ? activeCount : 0L);
        stats.put("criticalCount", criticalCount != null ? criticalCount : 0L);
        stats.put("warningCount", warningCount != null ? warningCount : 0L);
        stats.put("clearedCount", clearedCount != null ? clearedCount : 0L);
        return stats;
    }
}
