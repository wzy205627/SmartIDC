package com.smartidc.admin.controller;

import com.smartidc.common.core.domain.R;
import com.smartidc.iot.mock.TelemetryMockSimulatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 动环物联网仿真演练与故障注入控制器
 * 为运维演练、大屏推屏测试与评审自检提供一键式模拟控制端点
 */
@Tag(name = "动环仿真与故障演练中枢", description = "提供物联网遥测 Mock 数据流发生与高危故障注入接口")
@RestController
@RequestMapping("/api/v1/mock")
public class MockChaosController {

    private final TelemetryMockSimulatorService simulatorService;

    public MockChaosController(TelemetryMockSimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @Operation(summary = "查询动环模拟器与演练当前状态")
    @GetMapping("/status")
    public R<Map<String, Object>> getStatus() {
        return R.ok(simulatorService.getStatus());
    }

    @Operation(summary = "启动/恢复动环心跳模拟数据流")
    @PostMapping("/start")
    public R<Void> start() {
        simulatorService.start();
        return R.ok();
    }

    @Operation(summary = "暂停动环心跳模拟数据流")
    @PostMapping("/stop")
    public R<Void> stop() {
        simulatorService.stop();
        return R.ok();
    }

    @Operation(summary = "一键故障注入：单机柜持续严重超温 (>38℃)")
    @PostMapping("/inject-overheat")
    public R<String> injectOverheat(
            @Parameter(description = "目标机柜编号，默认为 A-03")
            @RequestParam(defaultValue = "A-03") String rackCode) {
        simulatorService.injectOverheat(rackCode);
        return R.ok("已成功注入机柜 [" + rackCode + "] 严重超温故障 (模式: OVERHEAT)");
    }

    @Operation(summary = "一键故障注入：市电中断欠压掉电 (0V / 0A)")
    @PostMapping("/inject-blackout")
    public R<String> injectBlackout(
            @Parameter(description = "目标机柜编号，默认为 A-01")
            @RequestParam(defaultValue = "A-01") String rackCode) {
        simulatorService.injectBlackout(rackCode);
        return R.ok("已成功注入机柜 [" + rackCode + "] 供电断电故障 (模式: BLACKOUT)");
    }

    @Operation(summary = "一键故障注入：机房集群告警风暴 (A-01, A-02, A-03 同时过温)")
    @PostMapping("/inject-storm")
    public R<String> injectStorm() {
        simulatorService.injectStorm();
        return R.ok("已成功注入同机房多机柜并发过温告警风暴 (模式: STORM)");
    }

    @Operation(summary = "一键故障注入：单次瞬态偶发毛刺 (42℃ 单次突发)")
    @PostMapping("/inject-glitch")
    public R<String> injectGlitch(
            @Parameter(description = "目标机柜编号，默认为 A-03")
            @RequestParam(defaultValue = "A-03") String rackCode) {
        simulatorService.injectGlitch(rackCode);
        return R.ok("已成功向机柜 [" + rackCode + "] 注入单次瞬态 42℃ 毛刺信号 (模式: GLITCH)");
    }

    @Operation(summary = "一键重置恢复全机房正常安全工况 (恢复后将触发自动消警)")
    @PostMapping("/reset")
    public R<String> reset() {
        simulatorService.resetToNormal();
        return R.ok("已成功一键重置恢复全机房标准安全工况 (模式: NORMAL)");
    }
}
