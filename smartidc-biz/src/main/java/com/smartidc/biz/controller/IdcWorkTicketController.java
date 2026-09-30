package com.smartidc.biz.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartidc.biz.domain.dto.CreateWorkTicketDTO;
import com.smartidc.biz.domain.dto.WorkTicketQueryDTO;
import com.smartidc.biz.domain.vo.WorkTicketDetailVO;
import com.smartidc.biz.domain.vo.WorkTicketStatsVO;
import com.smartidc.biz.service.IdcWorkTicketService;
import com.smartidc.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 运维排障工单协同与状态机流转控制层
 */
@Tag(name = "运维排障工单", description = "动环告警转工单、状态机流转推进与协同闭环接口")
@RestController
@RequestMapping("/api/v1/ops/ticket")
public class IdcWorkTicketController {

    private final IdcWorkTicketService ticketService;

    public IdcWorkTicketController(IdcWorkTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Operation(summary = "创建排障工单", description = "支持从告警一键生成，自动关联机柜拓扑并联动告警状态为已派单")
    @PostMapping("/create")
    public R<WorkTicketDetailVO> createTicket(@RequestBody CreateWorkTicketDTO dto) {
        return R.ok(ticketService.createTicket(dto));
    }

    @Operation(summary = "多维分页查询工单列表", description = "支持按状态、类型、机柜、关键字分页检索")
    @GetMapping("/page")
    public R<Page<WorkTicketDetailVO>> pageTickets(WorkTicketQueryDTO query) {
        return R.ok(ticketService.pageTickets(query));
    }

    @Operation(summary = "获取工单详情与处置画像", description = "查询工单基础信息、关联告警指标与生命周期时间线")
    @GetMapping("/{ticketId}")
    public R<WorkTicketDetailVO> getDetail(
            @Parameter(description = "工单ID") @PathVariable Long ticketId) {
        WorkTicketDetailVO detail = ticketService.getTicketDetail(ticketId);
        if (detail == null) {
            return R.fail("工单不存在");
        }
        return R.ok(detail);
    }

    @Operation(summary = "指派/转派运维工程师", description = "将工单状态流转至 1 (已指派)")
    @PostMapping("/{ticketId}/assign")
    public R<WorkTicketDetailVO> assignTicket(
            @Parameter(description = "工单ID") @PathVariable Long ticketId,
            @RequestBody Map<String, Object> params) {
        Long operatorId = params.get("operatorId") != null ? Long.valueOf(params.get("operatorId").toString()) : null;
        String operatorName = params.get("operatorName") != null ? params.get("operatorName").toString() : "现场值班工程师";
        return R.ok(ticketService.assignTicket(ticketId, operatorId, operatorName));
    }

    @Operation(summary = "工程师接单并开始现场排障", description = "将工单状态流转至 2 (排障中)")
    @PostMapping("/{ticketId}/start")
    public R<WorkTicketDetailVO> startProcessing(
            @Parameter(description = "工单ID") @PathVariable Long ticketId) {
        return R.ok(ticketService.startProcessing(ticketId));
    }

    @Operation(summary = "提交现场排障处置记录", description = "将工单状态流转至 6 (已解决待复核)")
    @PostMapping("/{ticketId}/resolve")
    public R<WorkTicketDetailVO> resolveTicket(
            @Parameter(description = "工单ID") @PathVariable Long ticketId,
            @RequestBody Map<String, String> params) {
        String processNotes = params.get("processNotes");
        return R.ok(ticketService.resolveTicket(ticketId, processNotes));
    }

    @Operation(summary = "主管复核结案并闭环消警", description = "将工单状态流转至 7 (已办结)，并自动消除关联活动告警")
    @PostMapping("/{ticketId}/complete")
    public R<WorkTicketDetailVO> completeTicket(
            @Parameter(description = "工单ID") @PathVariable Long ticketId,
            @RequestParam(defaultValue = "true") Boolean closeAlarm) {
        return R.ok(ticketService.completeTicket(ticketId, closeAlarm));
    }

    @Operation(summary = "获取工单态势全盘统计", description = "返回工单总数、待分配、排障中、待复核、已办结及今日新增数")
    @GetMapping("/stats")
    public R<WorkTicketStatsVO> getStats() {
        return R.ok(ticketService.getTicketStats());
    }
}
