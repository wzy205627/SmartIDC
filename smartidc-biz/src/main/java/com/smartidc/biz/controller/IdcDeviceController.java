package com.smartidc.biz.controller;

import com.smartidc.biz.domain.dto.DeviceMountDTO;
import com.smartidc.biz.domain.entity.IdcDevice;
import com.smartidc.biz.domain.vo.RackSlotProfileVO;
import com.smartidc.biz.service.IdcDeviceService;
import com.smartidc.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 机房设备与 U 位资产管理控制层
 */
@Tag(name = "设备资产与U位管理", description = "IT设备及动环传感器台账、42U插槽画像与上架防碰撞接口")
@RestController
@RequestMapping("/api/v1/device")
public class IdcDeviceController {

    private final IdcDeviceService idcDeviceService;

    public IdcDeviceController(IdcDeviceService idcDeviceService) {
        this.idcDeviceService = idcDeviceService;
    }

    @Operation(summary = "办理设备上架", description = "校验42U物理边界、额定容量与区间碰撞冲突，通过后执行物理上架")
    @PostMapping("/mount")
    public R<IdcDevice> mount(@Valid @RequestBody DeviceMountDTO dto) {
        IdcDevice device = idcDeviceService.mountDevice(dto);
        return R.ok(device, "设备上架成功");
    }

    @Operation(summary = "办理设备下架", description = "移出指定设备并释放其所占用的 U 位空间容量")
    @PostMapping("/unmount/{deviceId}")
    public R<Void> unmount(@Parameter(description = "设备ID") @PathVariable Long deviceId) {
        idcDeviceService.unmountDevice(deviceId);
        return R.ok(null, "设备下架成功");
    }

    @Operation(summary = "一键清空机柜在架设备", description = "一键批量移出指定机柜的所有在架设备，重置U位占用为0并释放负荷")
    @PostMapping("/rack/{rackId}/clear-all")
    public R<Integer> clearRackDevices(@Parameter(description = "机柜ID") @PathVariable Long rackId) {
        int clearedCount = idcDeviceService.clearRackDevices(rackId);
        return R.ok(clearedCount, "成功清空机柜在架设备共 " + clearedCount + " 台");
    }

    @Operation(summary = "办理设备替换", description = "原子级下架旧设备并上架新设备，级联重新计算U位空间与供电负载")
    @PostMapping("/replace/{oldDeviceId}")
    public R<IdcDevice> replace(
            @Parameter(description = "被替换的原设备ID") @PathVariable Long oldDeviceId,
            @Valid @RequestBody DeviceMountDTO dto) {
        IdcDevice device = idcDeviceService.replaceDevice(oldDeviceId, dto);
        return R.ok(device, "设备替换成功");
    }

    @Operation(summary = "获取机柜 42U 插槽全景画像", description = "返回 1U 到 42U 连续物理插槽切片，供前端 42U 机柜图渲染")
    @GetMapping("/rack/{rackId}/slots")
    public R<List<RackSlotProfileVO>> getRackSlots(@Parameter(description = "机柜ID") @PathVariable Long rackId) {
        return R.ok(idcDeviceService.getRackSlotProfiles(rackId));
    }

    @Operation(summary = "查询设备台账列表", description = "支持按所在机柜、设备类型与关键字综合检索")
    @GetMapping("/list")
    public R<List<IdcDevice>> list(
            @Parameter(description = "机柜ID") @RequestParam(required = false) Long rackId,
            @Parameter(description = "设备类型") @RequestParam(required = false) String deviceType,
            @Parameter(description = "关键字 (设备名称/DeviceKey)") @RequestParam(required = false) String keyword) {
        return R.ok(idcDeviceService.listDevices(rackId, deviceType, keyword));
    }
}
