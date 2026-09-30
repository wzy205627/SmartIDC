package com.smartidc.biz.engine.alarm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 动环单机柜消抖状态机单元测试
 */
class RackAlarmStateMachineTest {

    private RackAlarmStateMachine fsm;

    @BeforeEach
    void setUp() {
        fsm = new RackAlarmStateMachine();
        // 设置测试窗口参数: 50ms 持续时间, 3次超限
        fsm.setWindowTimeThresholdMs(50L);
        fsm.setViolationCountThreshold(3);
        fsm.setRecoveryStableTimeMs(50L);
    }

    @Test
    @DisplayName("瞬时毛刺拦截测试：单次超限后迅速回落，状态回退 NORMAL 且不触发告警")
    void testSensorGlitchSuppression() {
        String rackCode = "A-03";
        AlarmEvaluationResult v1 = AlarmEvaluationResult.violation("TEMP_HIGH", "WARNING", "31.5℃", "温度偏高");

        // 第一次超限 -> 跃迁至 PENDING
        RackAlarmStateMachine.TransitionResult res1 = fsm.update(rackCode, v1);
        assertNotNull(res1);
        assertEquals(RackAlarmStateMachine.AlarmState.PENDING, res1.getNewState());
        assertFalse(res1.isShouldTriggerAlarm(), "初次超限应在消抖观察期，严禁触发报警");

        // 随后指标迅速回落安全线 (正常或安全回落)
        AlarmEvaluationResult safe = AlarmEvaluationResult.safeRecovered("TEMP_HIGH", "26.0℃", "正常温度");
        RackAlarmStateMachine.TransitionResult res2 = fsm.update(rackCode, safe);
        assertNotNull(res2);
        assertEquals(RackAlarmStateMachine.AlarmState.NORMAL, res2.getNewState());
        assertFalse(res2.isShouldTriggerAlarm(), "毛刺消除后回退 NORMAL，不触发报警");
    }

    @Test
    @DisplayName("持续故障确认测试：滑动窗口内持续超限且满3次，跃迁至 ACTIVE 触发真实报警")
    void testSustainedFaultTriggersActive() throws InterruptedException {
        String rackCode = "A-03";
        AlarmEvaluationResult v = AlarmEvaluationResult.violation("TEMP_HIGH", "WARNING", "32.0℃", "温度偏高");

        // 第一次超限 -> PENDING
        RackAlarmStateMachine.TransitionResult res1 = fsm.update(rackCode, v);
        assertEquals(RackAlarmStateMachine.AlarmState.PENDING, res1.getNewState());
        assertFalse(res1.isShouldTriggerAlarm());

        // 第二次超限 -> 仍在 PENDING
        RackAlarmStateMachine.TransitionResult res2 = fsm.update(rackCode, v);
        assertEquals(RackAlarmStateMachine.AlarmState.PENDING, res2.getNewState());
        assertFalse(res2.isShouldTriggerAlarm());

        // 等待超过 50ms 窗口阈值
        Thread.sleep(60);

        // 第三次超限 -> 达到数量与时间阈值，确认激活 ACTIVE
        RackAlarmStateMachine.TransitionResult res3 = fsm.update(rackCode, v);
        assertNotNull(res3);
        assertEquals(RackAlarmStateMachine.AlarmState.ACTIVE, res3.getNewState());
        assertTrue(res3.isShouldTriggerAlarm(), "故障持续确认，必须触发真实告警");
        assertEquals("WARNING", res3.getAlarmLevel());
    }

    @Test
    @DisplayName("迟滞回差稳定消警测试：指标降温回落至回差带并稳定后，状态跃迁至 CLEARED")
    void testHysteresisAutoClear() throws InterruptedException {
        String rackCode = "A-01";
        AlarmEvaluationResult v = AlarmEvaluationResult.violation("TEMP_HIGH", "CRITICAL", "36.0℃", "严重超温");

        // 触发 ACTIVE 告警
        fsm.update(rackCode, v);
        Thread.sleep(60);
        RackAlarmStateMachine.TransitionResult activeRes = fsm.update(rackCode, v);
        assertEquals(RackAlarmStateMachine.AlarmState.ACTIVE, activeRes.getNewState());

        // 模拟给状态机绑定了已落库的 AlarmId = 88L
        fsm.bindActiveAlarmId(rackCode, "TEMP_HIGH", 88L);

        // 温度安全回落 (<= 28.0℃ 回差线)
        AlarmEvaluationResult safe = AlarmEvaluationResult.safeRecovered("TEMP_HIGH", "25.0℃", "温度回落正常");
        RackAlarmStateMachine.TransitionResult recover1 = fsm.update(rackCode, safe);
        assertNull(recover1, "初次恢复进入稳定观察期，暂不立即消警");

        // 等待稳定时长超过 50ms
        Thread.sleep(60);

        RackAlarmStateMachine.TransitionResult recover2 = fsm.update(rackCode, safe);
        assertNotNull(recover2);
        assertEquals(RackAlarmStateMachine.AlarmState.CLEARED, recover2.getNewState());
        assertTrue(recover2.isShouldClearAlarm(), "持续稳定后触发自动消警");
        assertEquals(88L, recover2.getActiveAlarmId());
    }
}
