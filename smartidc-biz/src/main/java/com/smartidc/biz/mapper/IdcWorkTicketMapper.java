package com.smartidc.biz.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartidc.biz.domain.entity.IdcWorkTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 运维排障工单持久层 Mapper
 */
@Mapper
public interface IdcWorkTicketMapper extends BaseMapper<IdcWorkTicket> {

    /**
     * 忽略租户隔离，按机柜分页查询历史维保工单
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM idc_work_ticket WHERE rack_id = #{rackId} OR rack_code = #{rackCode} ORDER BY create_time DESC LIMIT #{offset}, #{limit}")
    List<IdcWorkTicket> selectTicketsByRackWithPage(@Param("rackId") Long rackId, @Param("rackCode") String rackCode, @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 忽略租户隔离，统计机柜工单总数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(*) FROM idc_work_ticket WHERE rack_id = #{rackId} OR rack_code = #{rackCode}")
    long countTicketsByRack(@Param("rackId") Long rackId, @Param("rackCode") String rackCode);

    /**
     * 忽略租户隔离，按工单状态查询待复核工单 (供防抖自动归档守护线程扫描)
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM idc_work_ticket WHERE status = #{status} ORDER BY create_time ASC")
    List<IdcWorkTicket> selectTicketsByStatusIgnoreTenant(@Param("status") Integer status);

    /**
     * 忽略租户隔离，根据 ID 查询工单 (供 BOLA 权限核验)
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM idc_work_ticket WHERE ticket_id = #{ticketId}")
    IdcWorkTicket selectByIdIgnoreTenant(@Param("ticketId") Long ticketId);
}
