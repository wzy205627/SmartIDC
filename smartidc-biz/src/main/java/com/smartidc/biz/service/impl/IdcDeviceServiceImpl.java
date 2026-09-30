package com.smartidc.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartidc.biz.domain.dto.DeviceMountDTO;
import com.smartidc.biz.domain.entity.IdcDevice;
import com.smartidc.biz.domain.entity.IdcRack;
import com.smartidc.biz.domain.vo.RackSlotProfileVO;
import com.smartidc.biz.mapper.IdcDeviceMapper;
import com.smartidc.biz.service.IdcDeviceService;
import com.smartidc.biz.service.IdcRackService;
import com.smartidc.common.enums.ResultCode;
import com.smartidc.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 机房设备与传感器领域服务实现类
 */
@Service
public class IdcDeviceServiceImpl extends ServiceImpl<IdcDeviceMapper, IdcDevice> implements IdcDeviceService {

    private static final Logger log = LoggerFactory.getLogger(IdcDeviceServiceImpl.class);

    private final IdcRackService idcRackService;

    public IdcDeviceServiceImpl(IdcRackService idcRackService) {
        this.idcRackService = idcRackService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IdcDevice mountDevice(DeviceMountDTO dto) {
        int newStart = dto.getStartU();
        int uHeight = dto.getUHeight();
        int newEnd = newStart + uHeight - 1;

        // 1. 物理边界校验 (1U ~ 42U)
        if (newStart < 1 || newStart > 42) {
            throw new ServiceException("起始U位超出机架 [1-42U] 物理边界");
        }
        if (newEnd > 42) {
            throw new ServiceException(String.format("上架失败: 设备占用区间 [%dU-%dU] 超出机架 42U 顶端物理边界", newStart, newEnd));
        }

        // 2. 目标机柜合法性与状态校验
        IdcRack rack = idcRackService.getById(dto.getRackId());
        if (rack == null) {
            throw new ServiceException(ResultCode.RACK_NOT_FOUND);
        }
        if (rack.getStatus() != null && rack.getStatus() == 2) {
            throw new ServiceException("当前机柜处于维保锁定状态，禁止执行上架操作");
        }

        // 3. 查询当前机架已有设备列表
        LambdaQueryWrapper<IdcDevice> query = new LambdaQueryWrapper<>();
        query.eq(IdcDevice::getRackId, dto.getRackId());
        List<IdcDevice> existingDevices = list(query);

        // 4. 额定供电容量上限校验 (Σ existing.power + new.power <= rating)
        BigDecimal totalExistingPower = BigDecimal.ZERO;
        for (IdcDevice d : existingDevices) {
            if (d.getRatedPower() != null) {
                totalExistingPower = totalExistingPower.add(d.getRatedPower());
            }
        }
        BigDecimal totalProjectedPower = totalExistingPower.add(dto.getRatedPower());
        if (rack.getPowerRating() != null && totalProjectedPower.compareTo(rack.getPowerRating()) > 0) {
            throw new ServiceException(ResultCode.RACK_POWER_EXCEEDED.getCode(),
                    String.format("上架失败: 上架后总功率 (%.2f kW) 超出该机架额定供电容量 (%.2f kVA)",
                            totalProjectedPower.doubleValue(), rack.getPowerRating().doubleValue()));
        }

        // 5. 空间区间几何碰撞检测算法 (Interval Collision Detection)
        // 判定准则: max(newStart, exStart) <= min(newEnd, exEnd)
        for (IdcDevice d : existingDevices) {
            if (d.getStartU() != null && d.getUHeight() != null && d.getUHeight() > 0) {
                int exStart = d.getStartU();
                int exEnd = exStart + d.getUHeight() - 1;

                if (Math.max(newStart, exStart) <= Math.min(newEnd, exEnd)) {
                    throw new ServiceException(ResultCode.RACK_SLOT_OCCUPIED.getCode(),
                            String.format("上架失败: 拟占 U 位 [%dU-%dU] 与已有设备 [%s (%dU-%dU)] 发生物理空间冲突",
                                    newStart, newEnd, d.getDeviceName(), exStart, exEnd));
                }
            }
        }

        // 6. 原子执行持久化插入
        IdcDevice device = new IdcDevice();
        device.setTenantId(rack.getTenantId());
        device.setRackId(dto.getRackId());
        device.setDeviceName(dto.getDeviceName());
        device.setDeviceType(dto.getDeviceType());
        device.setIotDeviceKey(dto.getIotDeviceKey());
        device.setStartU(newStart);
        device.setUHeight(uHeight);
        device.setRatedPower(dto.getRatedPower());
        device.setStatus(1); // 1-正常
        device.setCreateTime(LocalDateTime.now());
        save(device);

        // 7. 级联原子更新机柜真实占用总U位与资产状态
        refreshRackUsedU(rack);

        return device;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unmountDevice(Long deviceId) {
        IdcDevice device = getById(deviceId);
        if (device == null) {
            throw new ServiceException("指定设备不存在或已被下架");
        }

        IdcRack rack = idcRackService.getById(device.getRackId());
        // 删除设备
        boolean removed = removeById(deviceId);

        // 原子刷新机柜实际占用 U 位与状态
        if (removed && rack != null) {
            refreshRackUsedU(rack);
        }

        return removed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int clearRackDevices(Long rackId) {
        IdcRack rack = idcRackService.getById(rackId);
        if (rack == null) {
            throw new ServiceException(ResultCode.RACK_NOT_FOUND);
        }
        if (rack.getStatus() != null && rack.getStatus() == 2) {
            throw new ServiceException("当前机柜处于维保锁定状态，禁止清空设备");
        }

        LambdaQueryWrapper<IdcDevice> query = new LambdaQueryWrapper<>();
        query.eq(IdcDevice::getRackId, rackId);
        List<IdcDevice> devices = list(query);
        if (devices.isEmpty()) {
            return 0;
        }

        int count = devices.size();
        remove(query);
        refreshRackUsedU(rack);
        log.info("[DeviceService] 机柜 [{}] (ID={}) 在架 {} 台设备已一键清空并重置为0U", rack.getRackCode(), rackId, count);
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IdcDevice replaceDevice(Long oldDeviceId, DeviceMountDTO dto) {
        IdcDevice oldDevice = getById(oldDeviceId);
        if (oldDevice == null) {
            throw new ServiceException("被替换的原设备不存在或已被移除");
        }

        IdcRack rack = idcRackService.getById(oldDevice.getRackId());
        if (rack == null) {
            throw new ServiceException(ResultCode.RACK_NOT_FOUND);
        }
        if (rack.getStatus() != null && rack.getStatus() == 2) {
            throw new ServiceException("当前机柜处于维保锁定状态，禁止执行设备替换");
        }

        int newStart = dto.getStartU();
        int uHeight = dto.getUHeight();
        int newEnd = newStart + uHeight - 1;

        // 1. 物理边界校验 (1U ~ 42U)
        if (newStart < 1 || newStart > 42) {
            throw new ServiceException("起始U位超出机架 [1-42U] 物理边界");
        }
        if (newEnd > 42) {
            throw new ServiceException(String.format("替换失败: 拟上架区间 [%dU-%dU] 超出机架 42U 顶端物理边界", newStart, newEnd));
        }

        // 2. 查询当前机柜除旧设备以外的已有设备列表
        LambdaQueryWrapper<IdcDevice> query = new LambdaQueryWrapper<>();
        query.eq(IdcDevice::getRackId, oldDevice.getRackId());
        query.ne(IdcDevice::getDeviceId, oldDeviceId);
        List<IdcDevice> remainingDevices = list(query);

        // 3. 供电功耗容量上限校验 (Σ remaining.power + new.power <= rating)
        BigDecimal remainingPower = BigDecimal.ZERO;
        for (IdcDevice d : remainingDevices) {
            if (d.getRatedPower() != null) {
                remainingPower = remainingPower.add(d.getRatedPower());
            }
        }
        BigDecimal totalProjectedPower = remainingPower.add(dto.getRatedPower() != null ? dto.getRatedPower() : BigDecimal.ZERO);
        if (rack.getPowerRating() != null && totalProjectedPower.compareTo(rack.getPowerRating()) > 0) {
            throw new ServiceException(ResultCode.RACK_POWER_EXCEEDED.getCode(),
                    String.format("替换失败: 替换后总负荷 (%.2f kW) 超出该机架额定供电容量 (%.2f kVA)",
                            totalProjectedPower.doubleValue(), rack.getPowerRating().doubleValue()));
        }

        // 4. 空间区间几何碰撞检测 (排除旧设备，但不得与其它已有设备冲突)
        for (IdcDevice d : remainingDevices) {
            if (d.getStartU() != null && d.getUHeight() != null && d.getUHeight() > 0) {
                int exStart = d.getStartU();
                int exEnd = exStart + d.getUHeight() - 1;

                if (Math.max(newStart, exStart) <= Math.min(newEnd, exEnd)) {
                    throw new ServiceException(ResultCode.RACK_SLOT_OCCUPIED.getCode(),
                            String.format("替换失败: 拟占 U 位 [%dU-%dU] 与其它在架设备 [%s (%dU-%dU)] 发生空间冲突",
                                    newStart, newEnd, d.getDeviceName(), exStart, exEnd));
                }
            }
        }

        // 5. 原子删除旧设备
        removeById(oldDeviceId);

        // 6. 原子保存新设备
        IdcDevice newDevice = new IdcDevice();
        newDevice.setTenantId(rack.getTenantId());
        newDevice.setRackId(rack.getRackId());
        newDevice.setDeviceName(dto.getDeviceName());
        newDevice.setDeviceType(dto.getDeviceType());
        newDevice.setIotDeviceKey(dto.getIotDeviceKey());
        newDevice.setStartU(newStart);
        newDevice.setUHeight(uHeight);
        newDevice.setRatedPower(dto.getRatedPower());
        newDevice.setStatus(1); // 1-正常
        newDevice.setCreateTime(LocalDateTime.now());
        save(newDevice);

        // 7. 级联原子更新机柜真实占用总U位与资产状态
        refreshRackUsedU(rack);

        return newDevice;
    }

    /**
     * 重新校准并持久化机架实际占用的总 U 位数与资产状态
     */
    private void refreshRackUsedU(IdcRack rack) {
        if (rack == null || rack.getRackId() == null) return;
        LambdaQueryWrapper<IdcDevice> query = new LambdaQueryWrapper<>();
        query.eq(IdcDevice::getRackId, rack.getRackId());
        List<IdcDevice> devices = list(query);
        int realUsedU = 0;
        for (IdcDevice d : devices) {
            if (d.getUHeight() != null && d.getUHeight() > 0) {
                realUsedU += d.getUHeight();
            }
        }
        rack.setUsedU(realUsedU);
        // 若机柜处于维保锁定(2)则维持不变；否则动态切换：> 0 托管使用中(1)，= 0 空闲可用(0)
        if (rack.getStatus() != null && rack.getStatus() != 2) {
            rack.setStatus(realUsedU > 0 ? 1 : 0);
        }
        idcRackService.updateById(rack);
    }

    @Override
    public List<RackSlotProfileVO> getRackSlotProfiles(Long rackId) {
        // 查询该机柜所有已上架设备
        LambdaQueryWrapper<IdcDevice> query = new LambdaQueryWrapper<>();
        query.eq(IdcDevice::getRackId, rackId);
        List<IdcDevice> devices = list(query);

        // 将设备按占用的 U 位映射到 Map
        Map<Integer, IdcDevice> slotDeviceMap = new HashMap<>();
        for (IdcDevice d : devices) {
            if (d.getStartU() != null && d.getUHeight() != null) {
                int start = d.getStartU();
                int end = start + d.getUHeight() - 1;
                for (int u = start; u <= end; u++) {
                    slotDeviceMap.put(u, d);
                }
            }
        }

        // 构造 1U ~ 42U 的标准切片列表 (自顶向下从 42U 到 1U 排列)
        List<RackSlotProfileVO> result = new ArrayList<>(42);
        for (int u = 42; u >= 1; u--) {
            IdcDevice d = slotDeviceMap.get(u);
            if (d != null) {
                RackSlotProfileVO vo = new RackSlotProfileVO();
                vo.setSlotU(u);
                vo.setIsOccupied(true);
                vo.setIsDeviceHead(d.getStartU() != null && d.getStartU() == u);
                vo.setDeviceId(d.getDeviceId());
                vo.setDeviceName(d.getDeviceName());
                vo.setDeviceType(d.getDeviceType());
                vo.setIotDeviceKey(d.getIotDeviceKey());
                vo.setStartU(d.getStartU());
                vo.setUHeight(d.getUHeight());
                vo.setRatedPower(d.getRatedPower());
                vo.setStatus(d.getStatus());
                result.add(vo);
            } else {
                result.add(RackSlotProfileVO.emptySlot(u));
            }
        }

        return result;
    }

    @Override
    public List<IdcDevice> listDevices(Long rackId, String deviceType, String keyword) {
        LambdaQueryWrapper<IdcDevice> query = new LambdaQueryWrapper<>();
        if (rackId != null) {
            query.eq(IdcDevice::getRackId, rackId);
        }
        if (deviceType != null && !deviceType.isBlank()) {
            query.eq(IdcDevice::getDeviceType, deviceType);
        }
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w.like(IdcDevice::getDeviceName, keyword)
                    .or()
                    .like(IdcDevice::getIotDeviceKey, keyword));
        }
        query.orderByAsc(IdcDevice::getRackId).orderByDesc(IdcDevice::getStartU);
        return list(query);
    }
}
