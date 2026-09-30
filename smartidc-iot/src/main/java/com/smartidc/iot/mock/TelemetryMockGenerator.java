package com.smartidc.iot.mock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 动环遥测逼真物联数据生成器
 * 基于热力学方程、设备在架负荷、功率焦耳热耦合及高斯随机游走算法，
 * 真实反映机房精密空调冷风温度、设备负载发热、逆向相对湿度与供电电压电流。
 */
public class TelemetryMockGenerator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Random RANDOM = new Random();

    // 机房标准冷通道冷风基准温度 (21.5℃) 与标准环境湿度 (52%RH)
    private static final double AMBIENT_TEMP_C = 21.5;
    private static final double AMBIENT_HUM_PCT = 52.0;
    private static final double POWER_FACTOR = 0.95;

    public String generatePayload(String tenantId, String rackCode, ChaosMode chaosMode, String targetRack) {
        return generatePayload(tenantId, rackCode, chaosMode, targetRack, null);
    }

    /**
     * 为指定机架生成符合热力学与电力学耦合的遥测 JSON 报文
     *
     * @param tenantId   租户编号 (如: "000000")
     * @param rackCode   机架编号 (如: "A-03")
     * @param chaosMode  当前的混沌演练模式
     * @param targetRack 混沌演练目标机架 (如 "A-03")
     * @param loadInfo   机架当前实际在架资产负荷 (设备总额定功率、占用U位等)
     * @return 符合 ACL 防腐层解析规范的标准 JSON 报文
     */
    public String generatePayload(String tenantId, String rackCode, ChaosMode chaosMode, String targetRack, RackLoadInfo loadInfo) {
        // 1. 基准市电输入电压 (220V 稳态，叠加微幅高斯扰动)
        double volt = clamp(220.0 + (RANDOM.nextGaussian() * 0.4), 218.0, 222.0);
        double curr;
        double power;
        double temp;
        double hum;

        // 2. 物理与热力学耦合推演
        if (loadInfo == null || loadInfo.isEmptyRack()) {
            // 【空机柜无负载工况】:
            // 无发热设备运行，机柜内部与机房冷通道环境恒温平衡 (~21.5℃)，电流为 0A，功耗为 0.00kW
            power = 0.00;
            curr = 0.00;
            temp = clamp(AMBIENT_TEMP_C + (RANDOM.nextGaussian() * 0.15), 21.0, 22.0);
            hum = clamp(AMBIENT_HUM_PCT + (RANDOM.nextGaussian() * 0.4), 50.0, 54.0);
        } else {
            // 【在架设备带载运行工况】:
            // 依据在架设备额定总功率，引入服务器动态负载率 (约 70% ~ 80% 动态利用率)
            double ratedPower = loadInfo.getTotalRatedPowerKw();
            double loadFactor = clamp(0.75 + (RANDOM.nextGaussian() * 0.03), 0.65, 0.85);
            power = ratedPower * loadFactor;

            // 交流电流: I = P(W) / (U * 功率因数)
            curr = (power * 1000.0) / (volt * POWER_FACTOR);

            // 热力学温升方程 (焦耳热与风道散热阻力模型):
            // 每 1kW 发热功耗平均带来约 2.4℃ 温升，U位密集度额外带来热阻温升
            double uResistance = (loadInfo.getUsedUCount() / 42.0) * 1.2;
            double deltaTemp = (power * 2.4) + uResistance + (RANDOM.nextGaussian() * 0.2);
            temp = clamp(AMBIENT_TEMP_C + deltaTemp, 21.5, 45.0);

            // 逆向相对湿度耦合 (温湿度焓湿图效应：空气升温时饱和水汽压增大，相对湿度反向微降)
            double deltaHum = (temp - AMBIENT_TEMP_C) * 1.1;
            hum = clamp(AMBIENT_HUM_PCT - deltaHum + (RANDOM.nextGaussian() * 0.3), 35.0, 65.0);
        }

        // 3. 混沌演练模式重载 (用于高危故障注入与告警测试)
        if (chaosMode == ChaosMode.OVERHEAT && rackCode.equalsIgnoreCase(targetRack)) {
            // 单柜高危超温 (模拟精密空调局部风阀关闭或高发热拥塞)
            temp = 38.0 + (RANDOM.nextDouble() * 1.5); // 38.0℃ ~ 39.5℃
        } else if (chaosMode == ChaosMode.BLACKOUT && rackCode.equalsIgnoreCase(targetRack)) {
            // 母线断电
            volt = 0.0;
            curr = 0.0;
            power = 0.00;
        } else if (chaosMode == ChaosMode.STORM) {
            // 机房集群风暴 (A-01, A-02, A-03 并发超温)
            if (rackCode.equalsIgnoreCase("A-01") || rackCode.equalsIgnoreCase("A-02") || rackCode.equalsIgnoreCase("A-03")) {
                temp = 36.5 + (RANDOM.nextDouble() * 1.5);
            }
        } else if (chaosMode == ChaosMode.GLITCH && rackCode.equalsIgnoreCase(targetRack)) {
            // 瞬态信号毛刺 (瞬时 42℃，仅维持 1 轮)
            temp = 42.0;
        }

        // 4. 组装标准物联报文
        Map<String, Object> map = new HashMap<>();
        map.put("tenantId", tenantId != null ? tenantId : "000000");
        map.put("rackCode", rackCode);
        map.put("deviceKey", "TH-" + rackCode + "-001");
        map.put("temperature", round(temp, 1));
        map.put("humidity", round(hum, 1));
        map.put("voltage", round(volt, 1));
        map.put("currentAmp", round(curr, 1));
        map.put("powerKw", round(power, 2));
        map.put("timestamp", System.currentTimeMillis());

        try {
            return OBJECT_MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    private static BigDecimal round(double val, int scale) {
        return BigDecimal.valueOf(val).setScale(scale, RoundingMode.HALF_UP);
    }
}
