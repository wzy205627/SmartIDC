package com.smartidc.biz.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * IDC机柜资产实体
 */
@TableName("idc_rack")
@Schema(description = "IDC机柜资产实体")
public class IdcRack implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "机架主键ID", example = "1")
    private Long rackId;

    @Schema(description = "租户ID", example = "000000")
    private String tenantId;

    @Schema(description = "所属机房/区域", example = "华东01-A区")
    private String roomName;

    @Schema(description = "机架编号", example = "A-03")
    private String rackCode;

    @Schema(description = "总可用U位 (默认42U)", example = "42")
    private Integer totalU;

    @Schema(description = "已占用U位", example = "24")
    private Integer usedU;

    @Schema(description = "额定供电容量(kVA)", example = "6.00")
    private BigDecimal powerRating;

    @Schema(description = "状态: 0-空闲, 1-托管使用中, 2-维保锁定", example = "1")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    public IdcRack() {
    }

    public Long getRackId() {
        return rackId;
    }

    public void setRackId(Long rackId) {
        this.rackId = rackId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getRackCode() {
        return rackCode;
    }

    public void setRackCode(String rackCode) {
        this.rackCode = rackCode;
    }

    public Integer getTotalU() {
        return totalU;
    }

    public void setTotalU(Integer totalU) {
        this.totalU = totalU;
    }

    public Integer getUsedU() {
        return usedU;
    }

    public void setUsedU(Integer usedU) {
        this.usedU = usedU;
    }

    public BigDecimal getPowerRating() {
        return powerRating;
    }

    public void setPowerRating(BigDecimal powerRating) {
        this.powerRating = powerRating;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
