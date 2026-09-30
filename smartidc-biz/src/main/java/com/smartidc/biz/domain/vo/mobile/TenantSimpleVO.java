package com.smartidc.biz.domain.vo.mobile;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 移动随行端租户简要信息出参 VO
 */
@Schema(description = "租户简要信息")
public class TenantSimpleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "租户名称")
    private String tenantName;

    @Schema(description = "是否为当前选中生效租户")
    private Boolean isCurrent;

    public TenantSimpleVO() {
    }

    public TenantSimpleVO(String tenantId, String tenantName, Boolean isCurrent) {
        this.tenantId = tenantId;
        this.tenantName = tenantName;
        this.isCurrent = isCurrent;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public Boolean getIsCurrent() {
        return isCurrent;
    }

    public void setIsCurrent(Boolean isCurrent) {
        this.isCurrent = isCurrent;
    }
}
