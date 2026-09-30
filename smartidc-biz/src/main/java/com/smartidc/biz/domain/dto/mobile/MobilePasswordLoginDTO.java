package com.smartidc.biz.domain.dto.mobile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 移动随行端工号密码登录入参 DTO
 */
@Schema(description = "移动随行端工号密码登录入参")
public class MobilePasswordLoginDTO {

    @NotBlank(message = "工号/账号不可为空")
    @Schema(description = "工程师工号/账号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不可为空")
    @Schema(description = "账号密码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Schema(description = "指定登录租户ID (可选，默认使用用户默认租户)")
    private String tenantId;

    public MobilePasswordLoginDTO() {
    }

    public MobilePasswordLoginDTO(String username, String password, String tenantId) {
        this.username = username;
        this.password = password;
        this.tenantId = tenantId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
