package com.smartidc.biz.domain.vo.mobile;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 移动随行端认证出参 VO
 */
@Schema(description = "移动端认证出参")
public class MobileLoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "业务响应状态: LOGIN_SUCCESS / NEED_BIND / UNAUTHORIZED_ACCESS")
    private String authState;

    @Schema(description = "首次绑定临时票据 (authState=NEED_BIND 时返回)")
    private String bindTicket;

    @Schema(description = "业务短期 Access Token (2小时)")
    private String accessToken;

    @Schema(description = "长期 Refresh Token (7天)")
    private String refreshToken;

    @Schema(description = "当前生效租户ID")
    private String currentTenantId;

    @Schema(description = "工程师用户信息")
    private EngineerUserVO user;

    @Schema(description = "该工程师被授权的租户列表")
    private List<TenantSimpleVO> tenantList = new ArrayList<>();

    public MobileLoginVO() {
    }

    public MobileLoginVO(String authState, String bindTicket, String accessToken, String refreshToken, String currentTenantId, EngineerUserVO user, List<TenantSimpleVO> tenantList) {
        this.authState = authState;
        this.bindTicket = bindTicket;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.currentTenantId = currentTenantId;
        this.user = user;
        this.tenantList = tenantList != null ? tenantList : new ArrayList<>();
    }

    public String getAuthState() {
        return authState;
    }

    public void setAuthState(String authState) {
        this.authState = authState;
    }

    public String getBindTicket() {
        return bindTicket;
    }

    public void setBindTicket(String bindTicket) {
        this.bindTicket = bindTicket;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getCurrentTenantId() {
        return currentTenantId;
    }

    public void setCurrentTenantId(String currentTenantId) {
        this.currentTenantId = currentTenantId;
    }

    public EngineerUserVO getUser() {
        return user;
    }

    public void setUser(EngineerUserVO user) {
        this.user = user;
    }

    public List<TenantSimpleVO> getTenantList() {
        return tenantList;
    }

    public void setTenantList(List<TenantSimpleVO> tenantList) {
        this.tenantList = tenantList;
    }
}
