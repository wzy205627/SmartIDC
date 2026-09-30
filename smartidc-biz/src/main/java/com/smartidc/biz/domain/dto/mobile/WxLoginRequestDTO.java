package com.smartidc.biz.domain.dto.mobile;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 微信小程序登录入参 DTO
 */
@Schema(description = "微信小程序登录入参")
public class WxLoginRequestDTO {

    @Schema(description = "微信临时登录凭证 code")
    private String code;

    @Schema(description = "是否启用本地离线 Mock 模式 (dev/test 生效)")
    private Boolean mock = false;

    @Schema(description = "Mock 工程师工号 (mock=true 时生效)")
    private String mockUserCode;

    public WxLoginRequestDTO() {
    }

    public WxLoginRequestDTO(String code, Boolean mock, String mockUserCode) {
        this.code = code;
        this.mock = mock;
        this.mockUserCode = mockUserCode;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Boolean getMock() {
        return mock;
    }

    public void setMock(Boolean mock) {
        this.mock = mock;
    }

    public String getMockUserCode() {
        return mockUserCode;
    }

    public void setMockUserCode(String mockUserCode) {
        this.mockUserCode = mockUserCode;
    }
}
