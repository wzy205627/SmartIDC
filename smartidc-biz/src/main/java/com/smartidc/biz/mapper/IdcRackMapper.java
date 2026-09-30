package com.smartidc.biz.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartidc.biz.domain.entity.IdcRack;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * IDC机柜持久层接口
 */
@Mapper
public interface IdcRackMapper extends BaseMapper<IdcRack> {

    /**
     * 忽略租户隔离，查询全机房所有在管机柜元数据 (供 Mock 物联网模拟器全域广播)
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM idc_rack ORDER BY tenant_id, rack_code")
    List<IdcRack> selectAllRacksIgnoreTenant();

    /**
     * 忽略租户隔离，精确查询指定租户与机柜
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM idc_rack WHERE rack_code = #{rackCode} AND tenant_id = #{tenantId} LIMIT 1")
    IdcRack selectOneByCodeAndTenant(@Param("rackCode") String rackCode, @Param("tenantId") String tenantId);

    /**
     * 忽略租户隔离，按机柜编号查找机架实体 (供告警事件推导所属机房与ID)
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM idc_rack WHERE rack_code = #{rackCode} LIMIT 1")
    IdcRack selectOneByCodeIgnoreTenant(@Param("rackCode") String rackCode);
}
