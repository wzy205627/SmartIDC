package com.smartidc.biz.controller.mobile;

import com.smartidc.biz.domain.dto.mobile.MobileResolveTicketDTO;
import com.smartidc.biz.domain.vo.mobile.MobileTicketDetailVO;
import com.smartidc.biz.domain.vo.mobile.MobileWorkTicketPageVO.MobileWorkTicketItemVO;
import com.smartidc.biz.service.MobileTicketService;
import com.smartidc.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 移动随行端现场排障工单全生命周期 REST 控制器
 */
@Tag(name = "移动随行端现场排障与工单中枢")
@RestController
@RequestMapping("/api/v1/mobile/ticket")
public class MobileTicketController {

    private final MobileTicketService mobileTicketService;

    public MobileTicketController(MobileTicketService mobileTicketService) {
        this.mobileTicketService = mobileTicketService;
    }

    @Operation(summary = "工程师现场一键接单 (状态推进: 0/1 ➔ 2 排障中)")
    @PostMapping("/{ticketId}/accept")
    public R<Void> acceptTicket(
            @Parameter(description = "工单ID")
            @PathVariable("ticketId") Long ticketId) {
        mobileTicketService.acceptTicket(ticketId);
        return R.ok(null, "接单成功，已进入现场排障状态");
    }

    @Operation(summary = "现场申请挂起待备件 (状态推进: 2 ➔ 3 挂起待审批/备件)")
    @PostMapping("/{ticketId}/suspend")
    public R<Void> suspendTicket(
            @Parameter(description = "工单ID")
            @PathVariable("ticketId") Long ticketId,
            @Parameter(description = "挂起说明与备件申请")
            @RequestParam(value = "reason", required = false) String reason) {
        mobileTicketService.suspendTicket(ticketId, reason);
        return R.ok(null, "工单已申请挂起");
    }

    @Operation(summary = "恢复现场排障 (状态推进: 3 ➔ 2 排障中)")
    @PostMapping("/{ticketId}/resume")
    public R<Void> resumeTicket(
            @Parameter(description = "工单ID")
            @PathVariable("ticketId") Long ticketId) {
        mobileTicketService.resumeTicket(ticketId);
        return R.ok(null, "工单已恢复现场排障");
    }

    @Operation(summary = "提交现场消警与双步防爆存证 (状态推进: 2 ➔ 6 已解决待复核)")
    @PostMapping("/resolve")
    public R<Void> resolveTicket(@Valid @RequestBody MobileResolveTicketDTO dto) {
        mobileTicketService.resolveTicket(dto);
        return R.ok(null, "消警申请已提交，已进入动环5分钟防抖观察期");
    }

    @Operation(summary = "获取工单全景详情与 SOP 处置建议 (带 BOLA 权限核验)")
    @GetMapping("/{ticketId}/detail")
    public R<MobileTicketDetailVO> getTicketDetail(
            @Parameter(description = "工单ID")
            @PathVariable("ticketId") Long ticketId) {
        MobileTicketDetailVO detail = mobileTicketService.getTicketDetail(ticketId);
        return R.ok(detail, "获取工单详情成功");
    }

    @Operation(summary = "查询当前工程师负责的工单列表 (支持状态过滤)")
    @GetMapping("/list")
    public R<List<MobileWorkTicketItemVO>> getTicketList(
            @Parameter(description = "状态过滤: 1-已指派, 2-排障中, 3-挂起, 6-待复核, 7-已办结")
            @RequestParam(value = "status", required = false) Integer status) {
        List<MobileWorkTicketItemVO> list = mobileTicketService.getEngineerTickets(status);
        return R.ok(list, "获取工单列表成功");
    }
}
