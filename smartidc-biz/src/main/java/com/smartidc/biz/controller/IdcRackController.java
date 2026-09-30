package com.smartidc.biz.controller;

import com.smartidc.biz.domain.entity.IdcRack;
import com.smartidc.biz.service.IdcRackService;
import com.smartidc.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 机房机柜资产管理控制层
 */
@Tag(name = "机柜资产管理", description = "机房空间物理拓扑与机柜台账接口")
@RestController
@RequestMapping("/api/v1/rack")
public class IdcRackController {

    private final IdcRackService idcRackService;

    public IdcRackController(IdcRackService idcRackService) {
        this.idcRackService = idcRackService;
    }

    @Operation(summary = "查询机柜列表", description = "支持按机柜编号、所属机房区域、资产状态及综合关键字多维筛选")
    @GetMapping("/list")
    public R<List<IdcRack>> list(
            @Parameter(description = "机柜编号 (如: A-01 / B-02)")
            @RequestParam(required = false) String rackCode,
            @Parameter(description = "机房名称 (如: 华东01-A区 / B区)")
            @RequestParam(required = false) String roomName,
            @Parameter(description = "资产状态 (0-空闲, 1-托管使用中, 2-维保锁定)")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "综合关键字 (同时模糊匹配机柜编号与机房区域)")
            @RequestParam(required = false) String keyword) {
        return R.ok(idcRackService.listRacks(rackCode, roomName, status, keyword));
    }

    @Operation(summary = "获取机柜详情", description = "根据机架ID查询详细U位及功耗容量")
    @GetMapping("/{rackId}")
    public R<IdcRack> getById(@PathVariable Long rackId) {
        return R.ok(idcRackService.getById(rackId));
    }
}
