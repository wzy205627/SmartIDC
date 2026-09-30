package com.smartidc.biz.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartidc.biz.domain.entity.IdcDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 机房设备与传感器持久层接口
 */
@Mapper
public interface IdcDeviceMapper extends BaseMapper<IdcDevice> {

    /**
     * 忽略租户隔离，查询指定机架下的所有在架设备 (供动环物理热力学耦合计算)
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM idc_device WHERE rack_id = #{rackId}")
    List<IdcDevice> selectListByRackIdIgnoreTenant(@Param("rackId") Long rackId);
}
