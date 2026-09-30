package com.smartidc.biz.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartidc.biz.domain.entity.IdcAlarmEvent;
import com.smartidc.biz.domain.entity.IdcRack;
import com.smartidc.biz.mapper.IdcRackMapper;
import com.smartidc.biz.service.IdcAlarmEventService;
import com.smartidc.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 动环告警风暴抑制与告警态势控制层
 */
@Tag(name = "动环告警管理", description = "实时告警列表、态势卡片统计与人工闭环处置接口")
@RestController
@RequestMapping("/api/v1/alarm")
public class IdcAlarmEventController {

    private final IdcAlarmEventService alarmService;
    private final IdcRackMapper rackMapper;

    public IdcAlarmEventController(IdcAlarmEventService alarmService, IdcRackMapper rackMapper) {
        this.alarmService = alarmService;
        this.rackMapper = rackMapper;
    }

    @Operation(summary = "查询当前实时活动告警列表", description = "返回所有 status=1 (触发中) 的告警，支持按机房、机柜ID或机架编码过滤")
    @GetMapping("/active")
    public R<List<IdcAlarmEvent>> listActive(
            @Parameter(description = "机房区域名称 (可选)")
            @RequestParam(required = false) String roomName,
            @Parameter(description = "机柜ID (可选)")
            @RequestParam(required = false) Long rackId,
            @Parameter(description = "机架编码 (可选)")
            @RequestParam(required = false) String rackCode) {
        return R.ok(alarmService.listActiveAlarms(roomName, rackId, rackCode));
    }

    @Operation(summary = "获取大屏告警态势卡片统计", description = "返回当前活动总数、严重数、预警数与已消除数")
    @GetMapping("/stats")
    public R<Map<String, Object>> getStats() {
        return R.ok(alarmService.getAlarmStats());
    }

    @Operation(summary = "人工关闭/确认告警", description = "将告警状态变更为已消除 (status=3)")
    @PostMapping("/{alarmId}/close")
    public R<Void> closeAlarm(
            @Parameter(description = "告警ID")
            @PathVariable Long alarmId) {
        alarmService.closeAlarm(alarmId);
        return R.ok();
    }

    @Operation(summary = "标记告警为误报", description = "将告警状态变更为误报 (status=4)")
    @PostMapping("/{alarmId}/false-positive")
    public R<Void> markFalsePositive(
            @Parameter(description = "告警ID")
            @PathVariable Long alarmId) {
        alarmService.markFalsePositive(alarmId);
        return R.ok();
    }

    @Operation(summary = "动环告警演练 - 模拟注入越限告警", description = "用于应急响应演练，模拟生成指定机柜的动环越限事件并向前端广播")
    @PostMapping("/drill/inject")
    public R<IdcAlarmEvent> injectDrillAlarm(
            @Parameter(description = "机架编码") @RequestParam(defaultValue = "A-01") String rackCode,
            @Parameter(description = "告警等级 (CRITICAL / WARNING)") @RequestParam(defaultValue = "CRITICAL") String alarmLevel,
            @Parameter(description = "告警类型 (TEMP_HIGH / VOLTAGE_LOW / POWER_FAIL)") @RequestParam(defaultValue = "TEMP_HIGH") String alarmType,
            @Parameter(description = "采样指标值") @RequestParam(defaultValue = "35.80℃") String metricValue,
            @Parameter(description = "根因诊断建议") @RequestParam(defaultValue = "[应急演练] A-01机柜进风面发生严重过温，触发二级热岛警报") String summary) {

        Long rackId = 1L;
        String roomName = "华东01-A区";
        String tenantId = "000000";
        if (rackMapper != null) {
            IdcRack rack = rackMapper.selectOne(
                    new LambdaQueryWrapper<IdcRack>().eq(IdcRack::getRackCode, rackCode).last("LIMIT 1")
            );
            if (rack != null) {
                rackId = rack.getRackId();
                roomName = rack.getRoomName();
                tenantId = rack.getTenantId();
            }
        }
        IdcAlarmEvent event = alarmService.createOrEscalateAlarm(
                tenantId, rackId, rackCode, roomName, alarmLevel, alarmType, metricValue, summary
        );
        return R.ok(event, "演练告警注入成功");
    }

    @Operation(summary = "动环告警演练 - 一键消除所有活动告警", description = "快速恢复机房健康绿色状态")
    @PostMapping("/drill/reset")
    public R<Integer> resetDrillAlarms() {
        List<IdcAlarmEvent> activeList = alarmService.listActiveAlarms(null);
        int cleared = 0;
        for (IdcAlarmEvent e : activeList) {
            alarmService.closeAlarm(e.getAlarmId());
            cleared++;
        }
        return R.ok(cleared, "成功恢复健康态，消除活动告警共 " + cleared + " 起");
    }
}
