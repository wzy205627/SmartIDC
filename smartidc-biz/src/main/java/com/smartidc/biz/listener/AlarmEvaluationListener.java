package com.smartidc.biz.listener;

import com.smartidc.biz.domain.entity.IdcAlarmEvent;
import com.smartidc.biz.domain.entity.IdcRack;
import com.smartidc.biz.engine.alarm.AlarmEvaluationResult;
import com.smartidc.biz.engine.alarm.AlarmRuleEngine;
import com.smartidc.biz.engine.alarm.RackAlarmStateMachine;
import com.smartidc.biz.engine.alarm.SpatialDeduplicationEngine;
import com.smartidc.biz.mapper.IdcRackMapper;
import com.smartidc.biz.service.IdcAlarmEventService;
import com.smartidc.framework.tenant.TenantContext;
import com.smartidc.iot.domain.dto.TelemetryMetricDTO;
import com.smartidc.iot.event.TelemetryArrivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动环遥测指标告警评估与抑制监听器
 * 核心流水线：
 * 接收防腐报文 ➔ 规则研判 (AlarmRuleEngine) ➔ 滑动消抖 (RackAlarmStateMachine) ➔ 空间聚合 (SpatialDeduplicationEngine) ➔ 告警持久化
 */
@Component
public class AlarmEvaluationListener {

    private static final Logger log = LoggerFactory.getLogger(AlarmEvaluationListener.class);

    private final AlarmRuleEngine ruleEngine;
    private final RackAlarmStateMachine stateMachine;
    private final SpatialDeduplicationEngine dedupEngine;
    private final IdcAlarmEventService alarmService;
    private final IdcRackMapper rackMapper;

    // 缓存机柜所属机房元数据 (rackCode -> IdcRack)
    private final Map<String, IdcRack> rackCache = new ConcurrentHashMap<>();

    public AlarmEvaluationListener(AlarmRuleEngine ruleEngine,
                                   RackAlarmStateMachine stateMachine,
                                   SpatialDeduplicationEngine dedupEngine,
                                   IdcAlarmEventService alarmService,
                                   IdcRackMapper rackMapper) {
        this.ruleEngine = ruleEngine;
        this.stateMachine = stateMachine;
        this.dedupEngine = dedupEngine;
        this.alarmService = alarmService;
        this.rackMapper = rackMapper;
    }

    @EventListener
    public void onTelemetryArrived(TelemetryArrivedEvent event) {
        if (event == null || event.getMetric() == null) {
            return;
        }

        TelemetryMetricDTO metric = event.getMetric();
        String rackCode = metric.getRackCode();
        if (rackCode == null || rackCode.isBlank()) {
            return;
        }

        String originTenant = TenantContext.getTenantId();
        if (metric.getTenantId() != null && !metric.getTenantId().isBlank()) {
            TenantContext.setTenantId(metric.getTenantId());
        }

        try {
            // 1. 阈值与越限规则研判
            List<AlarmEvaluationResult> results = ruleEngine.evaluate(metric);
            if (results.isEmpty()) {
                return;
            }

            // 获取机柜元信息 (所属机房及ID，支持跨租户自动识别)
            IdcRack rack = getOrLoadRack(rackCode);
            String roomName = rack != null ? rack.getRoomName() : "默认机房区域";
            Long rackId = rack != null ? rack.getRackId() : 0L;

            // 2. 依次推进消抖状态机
            for (AlarmEvaluationResult eval : results) {
                RackAlarmStateMachine.TransitionResult transition = stateMachine.update(rackCode, eval);
                if (transition == null) {
                    continue;
                }

                // 2.1 状态机判定应激活真实告警 (非偶发毛刺)
                if (transition.isShouldTriggerAlarm()) {
                    // 3. 执行空间关联聚合研判 (30秒机房维度)
                    SpatialDeduplicationEngine.DeduplicationDecision dedup =
                            dedupEngine.evaluate(roomName, rackCode, transition.getAlarmType());

                    IdcAlarmEvent alarmEvent;
                    if (dedup.isAggregated()) {
                        // 触发机房级集群性主告警升格！
                        alarmEvent = alarmService.createOrEscalateAlarm(
                                metric.getTenantId(), rackId, rackCode, roomName,
                                "CRITICAL", transition.getAlarmType(), transition.getMetricValue(),
                                dedup.getMasterSummary()
                        );
                        dedupEngine.bindMasterAlarmId(roomName, transition.getAlarmType(), alarmEvent.getAlarmId());
                    } else {
                        // 普通机柜级告警
                        alarmEvent = alarmService.createOrEscalateAlarm(
                                metric.getTenantId(), rackId, rackCode, roomName,
                                transition.getAlarmLevel(), transition.getAlarmType(), transition.getMetricValue(),
                                transition.getDescription()
                        );
                    }

                    // 记录状态机关联的数据库告警ID
                    stateMachine.bindActiveAlarmId(rackCode, transition.getAlarmType(), alarmEvent.getAlarmId());
                }

                // 2.2 状态机判定已稳定恢复，执行自动消警
                if (transition.isShouldClearAlarm()) {
                    Long alarmId = transition.getActiveAlarmId();
                    if (alarmId != null) {
                        alarmService.clearAlarm(alarmId);
                        dedupEngine.clearMasterAlarm(roomName, transition.getAlarmType());
                    }
                }
            }
        } finally {
            TenantContext.setTenantId(originTenant);
        }
    }

    private IdcRack getOrLoadRack(String rackCode) {
        if (rackCode == null || rackCode.isBlank()) {
            return null;
        }
        return rackCache.computeIfAbsent(rackCode.trim(), code -> rackMapper.selectOneByCodeIgnoreTenant(code));
    }
}
