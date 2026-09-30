package com.smartidc.biz.service;

import com.smartidc.biz.domain.dto.mobile.MobilePasswordLoginDTO;
import com.smartidc.biz.domain.dto.mobile.WxBindRequestDTO;
import com.smartidc.biz.domain.dto.mobile.WxLoginRequestDTO;
import com.smartidc.biz.domain.vo.mobile.EngineerUserVO;
import com.smartidc.biz.domain.vo.mobile.MobileLoginVO;

import java.util.Map;

/**
 * 移动随行端统一身份认证与双 Token 服务接口
 */
public interface MobileAuthService {

    /**
     * 微信小程序登录 (含三态流转与开发 Mock 支持)
     */
    MobileLoginVO wxLogin(WxLoginRequestDTO request);

    /**
     * 微信 OpenID 与工程师工号密码首次激活绑定
     */
    MobileLoginVO bindWechat(WxBindRequestDTO request);

    /**
     * 工程师工号密码兜底登录
     */
    MobileLoginVO passwordLogin(MobilePasswordLoginDTO request);

    /**
     * 利用 Refresh Token 无感静默置换新 Access Token
     */
    Map<String, String> refreshToken(String refreshToken);

    /**
     * 租户安全切换 (校验租户归属授权并重新签发 Access Token)
     */
    Map<String, String> switchTenant(String targetTenantId);

    /**
     * 主动登出 (从 Redis 废除 Refresh Token)
     */
    void logout(String refreshToken);

    /**
     * 获取当前登录工程师个人信息与当前租户上下文
     */
    EngineerUserVO getCurrentUserInfo();
}
