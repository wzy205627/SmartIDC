package com.smartidc.biz.domain.dto.mobile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 微信账号与工程师工号首绑入参 DTO
 */
@Schema(description = "微信账号与工程师工号首绑入参")
public class WxBindRequestDTO {

    @NotBlank(message = "绑定临时凭证不可为空")
    @Schema(description = "绑定临时凭证 bindTicket", requiredMode = Schema.RequiredMode.REQUIRED)
    private String bindTicket;

    @NotBlank(message = "工程师工号/账号不可为空")
    @Schema(description = "工程师工号/账号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "账号初始密码不可为空")
    @Schema(description = "账号初始密码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    public WxBindRequestDTO() {
    }

    public WxBindRequestDTO(String bindTicket, String username, String password) {
        this.bindTicket = bindTicket;
        this.username = username;
        this.password = password;
    }

    public String getBindTicket() {
        return bindTicket;
    }

    public void setBindTicket(String bindTicket) {
        this.bindTicket = bindTicket;
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
}
