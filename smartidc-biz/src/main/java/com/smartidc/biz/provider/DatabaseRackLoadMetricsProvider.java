package com.smartidc.biz.provider;

import com.smartidc.biz.domain.entity.IdcDevice;
import com.smartidc.biz.domain.entity.IdcRack;
import com.smartidc.biz.mapper.IdcDeviceMapper;
import com.smartidc.biz.mapper.IdcRackMapper;
import com.smartidc.iot.mock.ManagedRackMeta;
import com.smartidc.iot.mock.RackLoadInfo;
import com.smartidc.iot.mock.RackLoadMetricsProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据库在架资产负荷指标提供器
 * 实时从 MySQL 设备台账检索机架总占用 U 位、在架设备台数及额定发热总功耗，
 * 支持跨多租户动态发现机房内所有在管机架，
 * 为物联网动环 Mock 模拟器提供热力学与电力学耦合的真实物理基线。
 */
@Component
public class DatabaseRackLoadMetricsProvider implements RackLoadMetricsProvider {

    private final IdcRackMapper idcRackMapper;
    private final IdcDeviceMapper idcDeviceMapper;

    public DatabaseRackLoadMetricsProvider(IdcRackMapper idcRackMapper, IdcDeviceMapper idcDeviceMapper) {
        this.idcRackMapper = idcRackMapper;
        this.idcDeviceMapper = idcDeviceMapper;
    }

    @Override
    public List<ManagedRackMeta> listAllManagedRacks() {
        List<IdcRack> racks = idcRackMapper.selectAllRacksIgnoreTenant();
        List<ManagedRackMeta> list = new ArrayList<>();
        if (racks != null) {
            for (IdcRack r : racks) {
                if (r.getRackCode() != null && !r.getRackCode().isBlank()) {
                    String tenant = (r.getTenantId() != null && !r.getTenantId().isBlank()) ? r.getTenantId() : "000000";
                    list.add(new ManagedRackMeta(tenant, r.getRackCode()));
                }
            }
        }
        return list;
    }

    @Override
    public RackLoadInfo getRackLoad(String tenantId, String rackCode) {
        if (rackCode == null || rackCode.isBlank()) {
            return new RackLoadInfo(0.0, 0, 0);
        }

        String tenant = (tenantId != null && !tenantId.isBlank()) ? tenantId.trim() : "000000";
        // 忽略租户过滤器阻断，精确匹配机柜
        IdcRack rack = idcRackMapper.selectOneByCodeAndTenant(rackCode.trim(), tenant);
        if (rack == null) {
            rack = idcRackMapper.selectOneByCodeIgnoreTenant(rackCode.trim());
        }
        if (rack == null || rack.getRackId() == null) {
            return new RackLoadInfo(0.0, 0, 0);
        }

        // 忽略租户过滤，按 rackId 查询机柜在架的所有设备资产
        List<IdcDevice> devices = idcDeviceMapper.selectListByRackIdIgnoreTenant(rack.getRackId());
        if (devices == null || devices.isEmpty()) {
            return new RackLoadInfo(0.0, 0, 0);
        }

        // 统计在架设备总额定功率 (kW) 与已占 U 位
        double totalRatedPowerKw = 0.0;
        int usedU = 0;
        for (IdcDevice dev : devices) {
            if (dev.getRatedPower() != null) {
                totalRatedPowerKw += dev.getRatedPower().doubleValue();
            }
            if (dev.getUHeight() != null && dev.getUHeight() > 0) {
                usedU += dev.getUHeight();
            }
        }

        return new RackLoadInfo(totalRatedPowerKw, usedU, devices.size());
    }
}
