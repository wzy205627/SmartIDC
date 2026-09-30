package com.smartidc.biz.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartidc.biz.domain.dto.DeviceMountDTO;
import com.smartidc.biz.domain.entity.IdcDevice;
import com.smartidc.biz.domain.vo.RackSlotProfileVO;

import java.util.List;

/**
 * 机房设备与传感器领域服务接口
 */
public interface IdcDeviceService extends IService<IdcDevice> {

    /**
     * 设备上架 (执行物理边界校验、供电容量上限校验、区间碰撞检测与级联统计)
     *
     * @param dto 上架参数
     * @return 上架成功后的设备实体
     */
    IdcDevice mountDevice(DeviceMountDTO dto);

    /**
     * 设备下架 (释放空间并原子扣减机架 used_u)
     *
     * @param deviceId 设备ID
     * @return 是否成功
     */
    boolean unmountDevice(Long deviceId);

    /**
     * 设备热替换与容量规划执行 (原子下架旧设备并上架新设备)
     *
     * @param oldDeviceId 被替换的原设备ID
     * @param dto         拟上架的新设备参数
     * @return 替换成功后的新设备实体
     */
    IdcDevice replaceDevice(Long oldDeviceId, DeviceMountDTO dto);

    /**
     * 获取指定机柜 1U~42U 插槽全景切片画像
     *
     * @param rackId 机架ID
     * @return 连续 42 个 U 位的插槽切片
     */
    List<RackSlotProfileVO> getRackSlotProfiles(Long rackId);

    /**
     * 一键清空指定机架的所有在架设备
     *
     * @param rackId 机架ID
     * @return 移出的设备数量
     */
    int clearRackDevices(Long rackId);

    /**
     * 查询设备台账列表
     *
     * @param rackId     机架ID (可选)
     * @param deviceType 设备类型 (可选)
     * @param keyword    设备名称/DeviceKey关键词 (可选)
     * @return 设备列表
     */
    List<IdcDevice> listDevices(Long rackId, String deviceType, String keyword);
}
