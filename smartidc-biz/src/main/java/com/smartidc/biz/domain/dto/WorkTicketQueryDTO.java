package com.smartidc.biz.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

/**
 * 运维工单多维分页查询请求 DTO
 */
@Schema(description = "运维工单多维分页查询请求 DTO")
public class WorkTicketQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "页码", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "状态过滤: 0-待分配, 1-已指派, 2-排障中, 6-待复核, 7-已办结", example = "1")
    private Integer status;

    @Schema(description = "工单类型: ALARM_REPAIR, ROUTINE_CHECK, ASSET_MOVE", example = "ALARM_REPAIR")
    private String ticketType;

    @Schema(description = "机架编码", example = "A-03")
    private String rackCode;

    @Schema(description = "搜索关键字 (匹配工单流水号或标题)", example = "高温")
    private String keyword;

    @Schema(description = "租户ID", example = "000000")
    private String tenantId;

    public WorkTicketQueryDTO() {
    }

    public Integer getPageNum() {
        return pageNum != null && pageNum > 0 ? pageNum : 1;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize != null && pageSize > 0 ? pageSize : 10;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public String getRackCode() {
        return rackCode;
    }

    public void setRackCode(String rackCode) {
        this.rackCode = rackCode;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
