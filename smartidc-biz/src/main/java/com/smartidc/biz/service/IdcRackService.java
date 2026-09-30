package com.smartidc.biz.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartidc.biz.domain.entity.IdcRack;

import java.util.List;

/**
 * 机柜资产服务接口
 */
public interface IdcRackService extends IService<IdcRack> {

    /**
     * 多维条件查询机架资产列表 (支持机柜编号、区域模糊匹配与智能容错)
     *
     * @param rackCode 机柜编号 (支持模糊匹配)
     * @param roomName 机房名称 (支持模糊匹配，并做智能容错兼容机柜编号)
     * @param status 资产状态 (0-空闲, 1-托管使用中, 2-维保锁定)
     * @param keyword 综合搜索关键字 (机柜编号 OR 所属区域)
     * @return 机柜列表
     */
    List<IdcRack> listRacks(String rackCode, String roomName, Integer status, String keyword);

    /**
     * 查询指定机房的所有机架列表
     */
    List<IdcRack> listByRoom(String roomName);
}
