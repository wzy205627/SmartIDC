package com.smartidc.biz.engine.alarm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 机架动环告警滑动时间窗口消抖状态机 (De-bounce State Machine)
 * 状态流转模型：
 * NORMAL ➔ PENDING (初次超限) ➔ ACTIVE (滑动窗口内持续10s且累计>=3次) ➔ CLEARED (回落且持续20s)
 */
@Component
public class RackAlarmStateMachine {

    private static final Logger log = LoggerFactory.getLogger(RackAlarmStateMachine.class);

    public enum AlarmState {
        NORMAL,
        PENDING,
        ACTIVE,
        CLEARED
    }

    /**
     * 单机架单指标类型的消抖上下文
     */
    public static class Context {
        private AlarmState state = AlarmState.NORMAL;
        private long firstViolationTimeMs = 0L;
        private int violationCount = 0;
        private long firstRecoveredTimeMs = 0L;
        private Long activeAlarmId = null;
        private String currentLevel = null;

        public AlarmState getState() {
            return state;
        }

        public void setState(AlarmState state) {
            this.state = state;
        }

        public long getFirstViolationTimeMs() {
            return firstViolationTimeMs;
        }

        public void setFirstViolationTimeMs(long firstViolationTimeMs) {
            this.firstViolationTimeMs = firstViolationTimeMs;
        }

        public int getViolationCount() {
            return violationCount;
        }

        public void setViolationCount(int violationCount) {
            this.violationCount = violationCount;
        }

        public long getFirstRecoveredTimeMs() {
            return firstRecoveredTimeMs;
        }

        public void setFirstRecoveredTimeMs(long firstRecoveredTimeMs) {
            this.firstRecoveredTimeMs = firstRecoveredTimeMs;
        }

        public Long getActiveAlarmId() {
            return activeAlarmId;
        }

        public void setActiveAlarmId(Long activeAlarmId) {
            this.activeAlarmId = activeAlarmId;
        }

        public String getCurrentLevel() {
            return currentLevel;
        }

        public void setCurrentLevel(String currentLevel) {
            this.currentLevel = currentLevel;
        }
    }

    /**
     * 状态机流转结果
     */
    public static class TransitionResult {
        private final String rackCode;
        private final String alarmType;
        private final AlarmState oldState;
        private final AlarmState newState;
        private final boolean shouldTriggerAlarm;
        private final boolean shouldClearAlarm;
        private final Long activeAlarmId;
        private final String alarmLevel;
        private final String metricValue;
        private final String description;

        public TransitionResult(String rackCode, String alarmType, AlarmState oldState, AlarmState newState,
                                boolean shouldTriggerAlarm, boolean shouldClearAlarm, Long activeAlarmId,
                                String alarmLevel, String metricValue, String description) {
            this.rackCode = rackCode;
            this.alarmType = alarmType;
            this.oldState = oldState;
            this.newState = newState;
            this.shouldTriggerAlarm = shouldTriggerAlarm;
            this.shouldClearAlarm = shouldClearAlarm;
            this.activeAlarmId = activeAlarmId;
            this.alarmLevel = alarmLevel;
            this.metricValue = metricValue;
            this.description = description;
        }

        public String getRackCode() {
            return rackCode;
        }

        public String getAlarmType() {
            return alarmType;
        }

        public AlarmState getOldState() {
            return oldState;
        }

        public AlarmState getNewState() {
            return newState;
        }

        public boolean isShouldTriggerAlarm() {
            return shouldTriggerAlarm;
        }

        public boolean isShouldClearAlarm() {
            return shouldClearAlarm;
        }

        public Long getActiveAlarmId() {
            return activeAlarmId;
        }

        public String getAlarmLevel() {
            return alarmLevel;
        }

        public String getMetricValue() {
            return metricValue;
        }

        public String getDescription() {
            return description;
        }
    }

    // 默认消抖窗口参数: 满 3 次 且 持续 10 秒
    private long windowTimeThresholdMs = 10000L;
    private int violationCountThreshold = 3;
    private long recoveryStableTimeMs = 3000L;

    /**
     * Key: rackCode + ":" + alarmType
     */
    private final Map<String, Context> contextMap = new ConcurrentHashMap<>();

    public void setWindowTimeThresholdMs(long windowTimeThresholdMs) {
        this.windowTimeThresholdMs = windowTimeThresholdMs;
    }

    public void setViolationCountThreshold(int violationCountThreshold) {
        this.violationCountThreshold = violationCountThreshold;
    }

    public void setRecoveryStableTimeMs(long recoveryStableTimeMs) {
        this.recoveryStableTimeMs = recoveryStableTimeMs;
    }

    /**
     * 针对指定机柜与评估结果推进状态机
     *
     * @param rackCode 机柜编号 (如: A-03)
     * @param eval     指标评估结果
     * @return 状态流转执行动作
     */
    public synchronized TransitionResult update(String rackCode, AlarmEvaluationResult eval) {
        if (rackCode == null || eval == null || eval.getAlarmType() == null) {
            return null;
        }

        String key = rackCode.trim() + ":" + eval.getAlarmType();
        Context ctx = contextMap.computeIfAbsent(key, k -> new Context());
        AlarmState oldState = ctx.getState();
        long now = System.currentTimeMillis();

        if (oldState == AlarmState.NORMAL) {
            if (eval.isViolated()) {
                // 初次超限: 跃迁至 PENDING 观察状态
                ctx.setState(AlarmState.PENDING);
                ctx.setFirstViolationTimeMs(now);
                ctx.setViolationCount(1);
                ctx.setCurrentLevel(eval.getAlarmLevel());
                ctx.setFirstRecoveredTimeMs(0L);

                log.info("[AlarmFSM] 监测到初次越限，进入 PENDING 消抖观察窗: 机柜=[{}], 类型=[{}], 数值=[{}], 等级=[{}]",
                        rackCode, eval.getAlarmType(), eval.getMetricValue(), eval.getAlarmLevel());
                return new TransitionResult(rackCode, eval.getAlarmType(), oldState, AlarmState.PENDING,
                        false, false, null, eval.getAlarmLevel(), eval.getMetricValue(), eval.getDescription());
            }
            return null;
        }

        if (oldState == AlarmState.PENDING) {
            if (eval.isViolated()) {
                ctx.setViolationCount(ctx.getViolationCount() + 1);
                ctx.setCurrentLevel(eval.getAlarmLevel());
                long duration = now - ctx.getFirstViolationTimeMs();

                // 判定是否达到真实告警条件 (滑动窗口 10 秒且累计 >= 3次，或极度高危CRITICAL加速触发)
                boolean timeMet = duration >= windowTimeThresholdMs;
                boolean countMet = ctx.getViolationCount() >= violationCountThreshold;
                boolean isCritical = "CRITICAL".equalsIgnoreCase(eval.getAlarmLevel());

                if ((timeMet && countMet) || (isCritical && ctx.getViolationCount() >= 2)) {
                    // 确认非偶然毛刺，正式激活告警 ACTIVE!
                    ctx.setState(AlarmState.ACTIVE);
                    log.warn("[AlarmFSM] 消抖窗口确认故障持续，状态变更为 ACTIVE 真实告警! 机柜=[{}], 类型=[{}], 持续时长=[{}ms], 累计采样=[{}次]",
                            rackCode, eval.getAlarmType(), duration, ctx.getViolationCount());

                    return new TransitionResult(rackCode, eval.getAlarmType(), oldState, AlarmState.ACTIVE,
                            true, false, null, eval.getAlarmLevel(), eval.getMetricValue(), eval.getDescription());
                } else {
                    // 继续在 PENDING 观察窗中缓冲
                    return new TransitionResult(rackCode, eval.getAlarmType(), oldState, AlarmState.PENDING,
                            false, false, null, eval.getAlarmLevel(), eval.getMetricValue(), eval.getDescription());
                }
            } else {
                // 在 PENDING 窗口内指标已自行回落安全区 ➔ 判定为传感器瞬时毛刺，成功消除过滤!
                ctx.setState(AlarmState.NORMAL);
                ctx.setViolationCount(0);
                ctx.setFirstViolationTimeMs(0L);
                log.info("[AlarmFSM] 传感器瞬时毛刺拦截成功 (指标回落)，状态回退 NORMAL: 机柜=[{}], 类型=[{}]",
                        rackCode, eval.getAlarmType());

                return new TransitionResult(rackCode, eval.getAlarmType(), oldState, AlarmState.NORMAL,
                        false, false, null, null, eval.getMetricValue(), "传感器偶发毛刺已被消抖引擎过滤");
            }
        }

        if (oldState == AlarmState.ACTIVE) {
            if (eval.isViolated()) {
                // 仍处于超限中，重置消除恢复计时器
                ctx.setFirstRecoveredTimeMs(0L);

                // 检查告警等级是否升级 (如 WARNING 升格为 CRITICAL)
                if ("CRITICAL".equalsIgnoreCase(eval.getAlarmLevel()) && !"CRITICAL".equalsIgnoreCase(ctx.getCurrentLevel())) {
                    ctx.setCurrentLevel("CRITICAL");
                    log.warn("[AlarmFSM] 机柜告警等级升级为 CRITICAL: 机柜=[{}], 类型=[{}], 数值=[{}]",
                            rackCode, eval.getAlarmType(), eval.getMetricValue());
                    return new TransitionResult(rackCode, eval.getAlarmType(), oldState, AlarmState.ACTIVE,
                            true, false, ctx.getActiveAlarmId(), "CRITICAL", eval.getMetricValue(), eval.getDescription());
                }
                return null;
            } else if (eval.isRecovered()) {
                // 指标已回落到安全回差带以下，启动持续稳定计时
                if (ctx.getFirstRecoveredTimeMs() == 0L) {
                    ctx.setFirstRecoveredTimeMs(now);
                    log.info("[AlarmFSM] 指标回落至安全线以下，启动消警稳定观察期 (需持续{}ms): 机柜=[{}], 类型=[{}]",
                            recoveryStableTimeMs, rackCode, eval.getAlarmType());
                    return null;
                }

                long stableElapsed = now - ctx.getFirstRecoveredTimeMs();
                if (stableElapsed >= recoveryStableTimeMs) {
                    // 持续稳定满足时间要求，正式自动消警 CLEARED!
                    ctx.setState(AlarmState.NORMAL);
                    Long alarmId = ctx.getActiveAlarmId();
                    ctx.setActiveAlarmId(null);
                    ctx.setFirstRecoveredTimeMs(0L);
                    ctx.setViolationCount(0);

                    log.info("[AlarmFSM] 故障持续稳定恢复，执行自动消警 CLEARED: 机柜=[{}], 类型=[{}], 关联AlarmId=[{}]",
                            rackCode, eval.getAlarmType(), alarmId);
                    return new TransitionResult(rackCode, eval.getAlarmType(), oldState, AlarmState.CLEARED,
                            false, true, alarmId, null, eval.getMetricValue(), "指标持续稳定，系统自动消除告警");
                }
            }
        }

        return null;
    }

    /**
     * 绑定生成的持久化告警主键 ID 至状态机上下文
     */
    public void bindActiveAlarmId(String rackCode, String alarmType, Long alarmId) {
        String key = rackCode.trim() + ":" + alarmType;
        Context ctx = contextMap.get(key);
        if (ctx != null) {
            ctx.setActiveAlarmId(alarmId);
        }
    }

    /**
     * 清理指定机柜的状态机 (人工消警或测试重置)
     */
    public void reset(String rackCode, String alarmType) {
        String key = rackCode.trim() + ":" + alarmType;
        contextMap.remove(key);
    }

    public void clearAll() {
        contextMap.clear();
    }
}
