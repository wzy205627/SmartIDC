package com.smartidc.biz.controller.mobile;

import com.smartidc.biz.domain.vo.mobile.MobileRackProfileVO;
import com.smartidc.biz.domain.vo.mobile.MobileRackTelemetryVO;
import com.smartidc.biz.domain.vo.mobile.MobileWorkTicketPageVO;
import com.smartidc.biz.service.MobileRackService;
import com.smartidc.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 移动随行端扫码巡检与机柜微画像 REST 控制器
 */
@Tag(name = "移动随行端机柜微画像与巡检中枢")
@RestController
@RequestMapping("/api/v1/mobile/rack")
public class MobileRackController {

    private final MobileRackService mobileRackService;

    public MobileRackController(MobileRackService mobileRackService) {
        this.mobileRackService = mobileRackService;
    }

    @Operation(summary = "获取机柜轻量微画像聚合视图 (带 BOLA 越权防护)")
    @GetMapping("/{rackCode}/profile")
    public R<MobileRackProfileVO> getRackProfile(
            @Parameter(description = "机柜编号, 如 RACK-A01, A-03")
            @PathVariable("rackCode") String rackCode) {
        MobileRackProfileVO profile = mobileRackService.getRackProfile(rackCode);
        return R.ok(profile, "获取机柜微画像成功");
    }

    @Operation(summary = "5秒静默轮询轻量动环端点 (仅传输最新温湿度功率)")
    @GetMapping("/{rackCode}/telemetry")
    public R<MobileRackTelemetryVO> getLatestTelemetry(
            @Parameter(description = "机柜编号")
            @PathVariable("rackCode") String rackCode) {
        MobileRackTelemetryVO telemetry = mobileRackService.getLatestTelemetry(rackCode);
        return R.ok(telemetry, "获取动环指标快照成功");
    }

    @Operation(summary = "按需独立分页获取机柜维保工单 (防弱网 Over-fetching)")
    @GetMapping("/{rackCode}/tickets")
    public R<MobileWorkTicketPageVO> getRackTickets(
            @Parameter(description = "机柜编号")
            @PathVariable("rackCode") String rackCode,
            @Parameter(description = "页码 (默认1)")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页大小 (默认5)")
            @RequestParam(value = "size", defaultValue = "5") int size) {
        MobileWorkTicketPageVO tickets = mobileRackService.getRackTickets(rackCode, page, size);
        return R.ok(tickets, "获取机柜工单列表成功");
    }
}
