package com.smartidc.biz.controller.mobile;

import com.smartidc.biz.domain.dto.mobile.MobilePasswordLoginDTO;
import com.smartidc.biz.domain.dto.mobile.RefreshTokenRequestDTO;
import com.smartidc.biz.domain.dto.mobile.WxBindRequestDTO;
import com.smartidc.biz.domain.dto.mobile.WxLoginRequestDTO;
import com.smartidc.biz.domain.vo.mobile.EngineerUserVO;
import com.smartidc.biz.domain.vo.mobile.MobileLoginVO;
import com.smartidc.biz.service.MobileAuthService;
import com.smartidc.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 移动随行端统一身份认证、双 Token 静默续期与多租户切换 REST 控制器
 */
@Tag(name = "移动随行端统一身份认证与租户中枢")
@RestController
@RequestMapping("/api/v1/mobile/auth")
public class MobileAuthController {

    private final MobileAuthService mobileAuthService;

    public MobileAuthController(MobileAuthService mobileAuthService) {
        this.mobileAuthService = mobileAuthService;
    }

    @Operation(summary = "微信快捷登录 (三态流转与离线Mock)")
    @PostMapping("/wx-login")
    public R<MobileLoginVO> wxLogin(@Valid @RequestBody WxLoginRequestDTO request) {
        MobileLoginVO vo = mobileAuthService.wxLogin(request);
        return R.ok(vo, "微信认证处理完成");
    }

    @Operation(summary = "微信OpenID与工程师工号首绑激活")
    @PostMapping("/bind-wechat")
    public R<MobileLoginVO> bindWechat(@Valid @RequestBody WxBindRequestDTO request) {
        MobileLoginVO vo = mobileAuthService.bindWechat(request);
        return R.ok(vo, "工号绑定成功，已签发运维凭证");
    }

    @Operation(summary = "工程师账号密码登录")
    @PostMapping("/login")
    public R<MobileLoginVO> login(@Valid @RequestBody MobilePasswordLoginDTO request) {
        MobileLoginVO vo = mobileAuthService.passwordLogin(request);
        return R.ok(vo, "登录成功");
    }

    @Operation(summary = "双Token静默续期置换新AccessToken")
    @PostMapping("/refresh-token")
    public R<Map<String, String>> refreshToken(@Valid @RequestBody RefreshTokenRequestDTO request) {
        Map<String, String> tokenMap = mobileAuthService.refreshToken(request.getRefreshToken());
        return R.ok(tokenMap, "令牌续期成功");
    }

    @Operation(summary = "多租户安全切换")
    @PostMapping("/switch-tenant")
    public R<Map<String, String>> switchTenant(@RequestParam("targetTenantId") String targetTenantId) {
        Map<String, String> tokenMap = mobileAuthService.switchTenant(targetTenantId);
        return R.ok(tokenMap, "租户切换成功");
    }

    @Operation(summary = "主动登出并销毁RefreshToken")
    @PostMapping("/logout")
    public R<Void> logout(@RequestParam(value = "refreshToken", required = false) String refreshToken) {
        mobileAuthService.logout(refreshToken);
        return R.ok(null, "已成功安全登出");
    }

    @Operation(summary = "获取当前工程师个人信息与租户上下文")
    @GetMapping("/user-info")
    public R<EngineerUserVO> getUserInfo() {
        EngineerUserVO vo = mobileAuthService.getCurrentUserInfo();
        return R.ok(vo, "获取用户信息成功");
    }
}
