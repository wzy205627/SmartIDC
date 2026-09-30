package com.smartidc.biz.domain.dto.mobile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 移动随行端刷新令牌请求 DTO
 */
@Schema(description = "刷新令牌请求入参")
public class RefreshTokenRequestDTO {

    @NotBlank(message = "refreshToken 不能为空")
    @Schema(description = "长期刷新凭证 Refresh Token", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;

    public RefreshTokenRequestDTO() {
    }

    public RefreshTokenRequestDTO(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
