package com.smartidc.biz.controller;

import com.smartidc.biz.domain.entity.IdcTelemetrySnapshot;
import com.smartidc.biz.domain.vo.TelemetryTimelineVO;
import com.smartidc.biz.service.IdcTelemetrySnapshotService;
import com.smartidc.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 动环遥测监控与时序数据控制层
 */
@Tag(name = "动环遥测监控", description = "机柜温湿度与供电指标热数据读取与时序回溯接口")
@RestController
@RequestMapping("/api/v1/telemetry")
public class IdcTelemetryController {

    private final IdcTelemetrySnapshotService snapshotService;

    public IdcTelemetryController(IdcTelemetrySnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @Operation(summary = "查询指定机柜最新动环状态", description = "优先毫秒级读取 Redis 热缓存，缓存失效时回退快照库")
    @GetMapping("/latest/{rackCode}")
    public R<Map<Object, Object>> getLatest(
            @Parameter(description = "机柜编号 (如: A-03)")
            @PathVariable String rackCode) {
        return R.ok(snapshotService.getLatestByRackCode(rackCode));
    }

    @Operation(summary = "回溯指定机柜历史动环时序快照", description = "查询按时间倒序排列的历史遥测数据")
    @GetMapping("/history/{rackCode}")
    public R<List<IdcTelemetrySnapshot>> getHistory(
            @Parameter(description = "机柜编号 (如: A-03)")
            @PathVariable String rackCode,
            @Parameter(description = "返回条数上限 (默认50)")
            @RequestParam(defaultValue = "50") int limit) {
        return R.ok(snapshotService.getHistoryByRackCode(rackCode, limit));
    }

    @Operation(summary = "多轴动环历史时序回溯与降采样查询", description = "支持 1h/6h/24h/7d 自适应分桶聚合降采样与越限告警区间高亮")
    @GetMapping("/timeline/{rackCode}")
    public R<TelemetryTimelineVO> getTimeline(
            @Parameter(description = "机柜编号 (如: A-03)")
            @PathVariable String rackCode,
            @Parameter(description = "时间范围: 1h, 6h, 24h, 7d (默认 24h)")
            @RequestParam(defaultValue = "24h") String timeRange,
            @Parameter(description = "指标列表 (可选，逗号分隔)")
            @RequestParam(required = false) String metrics) {
        return R.ok(snapshotService.getTimelineByRackCode(rackCode, timeRange, metrics));
    }
}
