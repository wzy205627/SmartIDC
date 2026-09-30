package com.smartidc.biz.controller;

import com.smartidc.biz.domain.entity.IdcBillingStatement;
import com.smartidc.biz.service.IdcBillingService;
import com.smartidc.common.core.domain.R;
import com.smartidc.framework.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IDC 能源能效 (PUE) 与机房租赁计费中枢 REST 控制器
 */
@Tag(name = "IDC 月度租赁电费与 PUE 结算中枢")
@RestController
@RequestMapping({"/api/v1/billing", "/api/v1/test/billing"})
public class IdcBillingController {

    private final IdcBillingService idcBillingService;

    public IdcBillingController(IdcBillingService idcBillingService) {
        this.idcBillingService = idcBillingService;
    }

    @Operation(summary = "管理员手动触发月度计费与能耗自动结算")
    @PostMapping({"/settle", "/trigger-settlement"})
    public R<List<IdcBillingStatement>> settle(
            @Parameter(description = "结算月份，格式如 2026-08", required = true)
            @RequestParam("month") String month,
            @Parameter(description = "是否强制冲正重算未支付账单", required = false)
            @RequestParam(value = "force", defaultValue = "false") boolean force) {
        List<IdcBillingStatement> statements = idcBillingService.executeMonthlySettlement(month, force);
        return R.ok(statements, "月度账单结算成功");
    }

    @Operation(summary = "多维查询机房月度所有租户账单明细")
    @GetMapping("/statements")
    public R<List<IdcBillingStatement>> listStatementsByMonth(
            @Parameter(description = "结算月份，如 2026-08 (留空查询所有月份)")
            @RequestParam(value = "month", required = false) String month) {
        List<IdcBillingStatement> statements = idcBillingService.listStatementsByMonth(month);
        return R.ok(statements, "查询机房账单明细成功");
    }

    @Operation(summary = "托管租户查询自身历史能耗与租金对账单 (带多租户上下文隔离)")
    @GetMapping("/my-statements")
    public R<List<IdcBillingStatement>> getMyStatements() {
        String currentTenantId = TenantContext.getTenantId();
        List<IdcBillingStatement> statements = idcBillingService.listStatementsByTenant(currentTenantId);
        return R.ok(statements, "查询我的月度账单成功");
    }
}
