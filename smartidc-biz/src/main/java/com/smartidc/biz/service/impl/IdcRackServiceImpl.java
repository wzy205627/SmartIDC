package com.smartidc.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartidc.biz.domain.entity.IdcRack;
import com.smartidc.biz.mapper.IdcRackMapper;
import com.smartidc.biz.service.IdcRackService;
import org.springframework.stereotype.Service;

import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 机柜资产服务实现类
 */
@Service
public class IdcRackServiceImpl extends ServiceImpl<IdcRackMapper, IdcRack> implements IdcRackService {

    @Override
    public List<IdcRack> listRacks(String rackCode, String roomName, Integer status, String keyword) {
        LambdaQueryWrapper<IdcRack> wrapper = new LambdaQueryWrapper<>();

        // 1. 综合通用关键字检索 (同时模糊匹配机柜编号和所属机房)
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(IdcRack::getRackCode, kw).or().like(IdcRack::getRoomName, kw));
        }

        // 2. 机柜编号检索 (模糊匹配，大小写兼顾)
        if (StringUtils.hasText(rackCode)) {
            wrapper.like(IdcRack::getRackCode, rackCode.trim());
        }

        // 3. 所属机房区域检索 (模糊匹配，并做智能容错：若用户在区域输入框误输入了机柜编号如 B-02 / A-01，依然智能命中)
        if (StringUtils.hasText(roomName)) {
            String rn = roomName.trim();
            wrapper.and(w -> w.like(IdcRack::getRoomName, rn).or().like(IdcRack::getRackCode, rn));
        }

        // 4. 资产状态过滤 (0-空闲, 1-托管使用中, 2-维保锁定)
        if (status != null) {
            wrapper.eq(IdcRack::getStatus, status);
        }

        wrapper.orderByAsc(IdcRack::getRackCode);
        return list(wrapper);
    }

    @Override
    public List<IdcRack> listByRoom(String roomName) {
        return listRacks(null, roomName, null, null);
    }
}
