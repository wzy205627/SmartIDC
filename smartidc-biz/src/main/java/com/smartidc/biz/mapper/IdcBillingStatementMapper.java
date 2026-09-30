package com.smartidc.biz.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartidc.biz.domain.entity.IdcBillingStatement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 租户机房能耗与租赁计费结算表持久层 Mapper
 */
@Mapper
public interface IdcBillingStatementMapper extends BaseMapper<IdcBillingStatement> {

    /**
     * 忽略租户隔离，查询指定月份的所有租户结算账单 (供全机房批处理结算与中台对账)
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM idc_billing_statement WHERE billing_month = #{billingMonth} ORDER BY tenant_id ASC")
    List<IdcBillingStatement> selectByBillingMonth(@Param("billingMonth") String billingMonth);

    /**
     * 忽略租户隔离，精确查询指定租户在指定结算月份的账单
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM idc_billing_statement WHERE tenant_id = #{tenantId} AND billing_month = #{billingMonth} LIMIT 1")
    IdcBillingStatement selectByTenantAndMonth(@Param("tenantId") String tenantId, @Param("billingMonth") String billingMonth);

    /**
     * 查询指定租户的全部历史账单 (按月份倒序排列)
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM idc_billing_statement WHERE tenant_id = #{tenantId} ORDER BY billing_month DESC")
    List<IdcBillingStatement> selectByTenantId(@Param("tenantId") String tenantId);

    /**
     * 忽略租户隔离，查询全部账单
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM idc_billing_statement ORDER BY billing_month DESC, tenant_id ASC")
    List<IdcBillingStatement> selectAllStatementsIgnoreTenant();
}
