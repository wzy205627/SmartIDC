package com.smartidc.iot.mock;

/**
 * 动环物联网混沌演练与故障注入模式枚举
 */
public enum ChaosMode {
    /**
     * 正常安全运行工况 (全机柜指标处于标准安全区间)
     */
    NORMAL("正常工况", "各机架动环指标平稳游走于绿色安全区间 (23.5℃~25.5℃)"),

    /**
     * 单机架持续高危超温越限 (突破消抖窗口触发 CRITICAL 告警)
     */
    OVERHEAT("单柜严重超温", "目标机架 (如 A-03) 持续注入 38.5℃ 极度高温，触发 ACTIVE 高危告警"),

    /**
     * 供电母线故障/掉电 (输入电压归零、电流归零)
     */
    BLACKOUT("市电中断掉电", "目标机架 (如 A-01) 电压降至 0V，电流 0A，触发断电停机警告"),

    /**
     * 机房集群性告警风暴 (同机房 3 台以上机架同时过温，测试空间关联聚合)
     */
    STORM("告警风暴聚合", "同机房 A-01、A-02、A-03 机柜并发超温，测试 30 秒空间归并为主告警"),

    /**
     * 传感器瞬态信号毛刺 (单次瞬时 42℃ 随后恢复，测试滑动窗口消抖过滤)
     */
    GLITCH("瞬态信号毛刺", "单次突发 42.0℃ 异常后立即恢复，测试滑动消抖引擎零误报拦截");

    private final String title;
    private final String description;

    ChaosMode(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
