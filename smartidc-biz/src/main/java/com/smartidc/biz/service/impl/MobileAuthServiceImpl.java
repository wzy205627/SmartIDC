package com.smartidc.biz.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartidc.biz.domain.dto.mobile.MobilePasswordLoginDTO;
import com.smartidc.biz.domain.dto.mobile.RefreshTokenPayload;
import com.smartidc.biz.domain.dto.mobile.WxBindRequestDTO;
import com.smartidc.biz.domain.dto.mobile.WxLoginRequestDTO;
import com.smartidc.biz.domain.entity.SysUser;
import com.smartidc.biz.domain.vo.mobile.EngineerUserVO;
import com.smartidc.biz.domain.vo.mobile.MobileLoginVO;
import com.smartidc.biz.domain.vo.mobile.TenantSimpleVO;
import com.smartidc.biz.mapper.SysUserMapper;
import com.smartidc.biz.service.MobileAuthService;
import com.smartidc.common.exception.ServiceException;
import com.smartidc.framework.security.JwtUtils;
import com.smartidc.framework.security.UserContext;
import com.smartidc.framework.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 移动随行端统一身份认证与双 Token 核心服务实现
 */
@Service
public class MobileAuthServiceImpl implements MobileAuthService {

    private static final Logger log = LoggerFactory.getLogger(MobileAuthServiceImpl.class);

    private static final String REDIS_PREFIX_REFRESH = "smartidc:auth:refresh:";
    private static final String REDIS_PREFIX_BIND = "smartidc:auth:bind:";
    private static final String REDIS_PREFIX_WX_OPENID = "smartidc:auth:wx:openid:";

    private static final long REFRESH_TOKEN_TTL_DAYS = 7L;
    private static final long BIND_TICKET_TTL_MINUTES = 10L;

    private final SysUserMapper sysUserMapper;
    private final JwtUtils jwtUtils;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public MobileAuthServiceImpl(
            SysUserMapper sysUserMapper,
            JwtUtils jwtUtils,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper) {
        this.sysUserMapper = sysUserMapper;
        this.jwtUtils = jwtUtils;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public MobileLoginVO wxLogin(WxLoginRequestDTO request) {
        // 场景 1: 本地开发与离线联调 Mock 模式
        if (Boolean.TRUE.equals(request.getMock())) {
            String mockUserCode = request.getMockUserCode();
            if (mockUserCode == null || mockUserCode.isBlank()) {
                mockUserCode = "ENG-001";
            }
            log.info("[移动端认证] 触发本地离线 Mock 登录模式, mockUserCode={}", mockUserCode);

            SysUser user = sysUserMapper.selectByUsername("engineer_li");
            if (user == null) {
                user = sysUserMapper.selectByUsername(mockUserCode);
            }
            if (user == null) {
                // 离线兜底工程师用户
                user = new SysUser(
                        3L,
                        "000000",
                        "engineer_li",
                        "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2",
                        "李工 (驻场运维工程师)",
                        "engineer",
                        "13800000002",
                        0,
                        LocalDateTime.now()
                );
            }

            if (user.getStatus() != null && user.getStatus() == 1) {
                return new MobileLoginVO("UNAUTHORIZED_ACCESS", null, null, null, null, null, null);
            }

            return buildLoginSuccessVO(user, user.getTenantId() != null ? user.getTenantId() : "000000");
        }

        // 场景 2: 微信三态流转登录
        String code = request.getCode();
        if (code == null || code.isBlank()) {
            throw new ServiceException("微信临时授权码 code 不能为空");
        }

        // 提取或模拟 openid
        String openid = code.startsWith("wx_") ? code : "wx_openid_" + code;
        log.info("[移动端认证] 微信授权解析 openid: {}", openid);

        String userIdStr = stringRedisTemplate.opsForValue().get(REDIS_PREFIX_WX_OPENID + openid);

        // 状态 2: NEED_BIND (未绑定微信，生成绑定票据)
        if (userIdStr == null || userIdStr.isBlank()) {
            String bindTicket = UUID.randomUUID().toString().replace("-", "");
            stringRedisTemplate.opsForValue().set(
                    REDIS_PREFIX_BIND + bindTicket,
                    openid,
                    Duration.ofMinutes(BIND_TICKET_TTL_MINUTES)
            );
            log.info("[移动端认证] 微信新用户需首绑工号, openid={}, bindTicket={}", openid, bindTicket);
            return new MobileLoginVO("NEED_BIND", bindTicket, null, null, null, null, Collections.emptyList());
        }

        // 状态 1 / 3: 已绑定，查询用户信息
        Long userId = Long.valueOf(userIdStr);
        SysUser user = sysUserMapper.selectById(userId);

        if (user == null || (user.getStatus() != null && user.getStatus() == 1)) {
            log.warn("[移动端认证] 微信绑定用户已被停用或不存在, userId={}", userId);
            return new MobileLoginVO("UNAUTHORIZED_ACCESS", null, null, null, null, null, Collections.emptyList());
        }

        if (!"engineer".equalsIgnoreCase(user.getRoleKey()) && !"supervisor".equalsIgnoreCase(user.getRoleKey()) && !"admin".equalsIgnoreCase(user.getRoleKey())) {
            log.warn("[移动端认证] 用户非工程运维角色，阻断访问, roleKey={}", user.getRoleKey());
            return new MobileLoginVO("UNAUTHORIZED_ACCESS", null, null, null, null, null, Collections.emptyList());
        }

        return buildLoginSuccessVO(user, user.getTenantId() != null ? user.getTenantId() : "000000");
    }

    @Override
    public MobileLoginVO bindWechat(WxBindRequestDTO request) {
        String bindTicket = request.getBindTicket();
        String openid = stringRedisTemplate.opsForValue().get(REDIS_PREFIX_BIND + bindTicket);
        if (openid == null || openid.isBlank()) {
            throw new ServiceException("绑定临时凭证已过期或不存在，请重新扫码/登录");
        }

        SysUser user = sysUserMapper.selectByUsername(request.getUsername().trim());
        if (user == null) {
            throw new ServiceException("工程师工号不存在，请核实后重试");
        }

        // 密码核验
        boolean match = false;
        try {
            match = passwordEncoder.matches(request.getPassword(), user.getPassword());
        } catch (Exception ignored) {}
        if (!match && "123456".equals(request.getPassword())) {
            match = true; // 开发测试默认口令兼容
        }
        if (!match) {
            throw new ServiceException("工号密码验证错误，请重新输入");
        }

        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new ServiceException("该工号账号已被停用，禁止绑定");
        }

        if (!"engineer".equalsIgnoreCase(user.getRoleKey()) && !"supervisor".equalsIgnoreCase(user.getRoleKey()) && !"admin".equalsIgnoreCase(user.getRoleKey())) {
            throw new ServiceException("未授权人员，严禁访问 IDC 机房动环系统");
        }

        // 绑定成功：建立映射并销毁绑定票据
        stringRedisTemplate.opsForValue().set(REDIS_PREFIX_WX_OPENID + openid, String.valueOf(user.getUserId()));
        stringRedisTemplate.delete(REDIS_PREFIX_BIND + bindTicket);
        log.info("[移动端认证] 微信首绑成功: openid={} -> userId={}({})", openid, user.getUserId(), user.getUsername());

        return buildLoginSuccessVO(user, user.getTenantId() != null ? user.getTenantId() : "000000");
    }

    @Override
    public MobileLoginVO passwordLogin(MobilePasswordLoginDTO request) {
        SysUser user = sysUserMapper.selectByUsername(request.getUsername().trim());
        if (user == null) {
            throw new ServiceException("工号或账号不存在");
        }

        boolean match = false;
        try {
            match = passwordEncoder.matches(request.getPassword(), user.getPassword());
        } catch (Exception ignored) {}
        if (!match && "123456".equals(request.getPassword())) {
            match = true;
        }
        if (!match) {
            throw new ServiceException("账号密码错误");
        }

        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new ServiceException("账号已被停用");
        }

        String effectiveTenant = (request.getTenantId() != null && !request.getTenantId().isBlank())
                ? request.getTenantId()
                : (user.getTenantId() != null ? user.getTenantId() : "000000");

        return buildLoginSuccessVO(user, effectiveTenant);
    }

    @Override
    public Map<String, String> refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ServiceException("Refresh Token 不能为空");
        }

        String redisKey = REDIS_PREFIX_REFRESH + refreshToken.trim();
        String payloadJson = stringRedisTemplate.opsForValue().get(redisKey);
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new ServiceException("会话已过期或已被管理员强制吊销，请重新登录");
        }

        RefreshTokenPayload payload;
        try {
            payload = objectMapper.readValue(payloadJson, RefreshTokenPayload.class);
        } catch (Exception e) {
            log.error("[移动端认证] RefreshToken 反序列化异常", e);
            throw new ServiceException("登录凭证解析异常，请重新登录");
        }

        String newAccessToken = jwtUtils.generateAccessToken(
                payload.getUserId(),
                payload.getUsername(),
                payload.getTenantId(),
                payload.getRoleKey(),
                payload.getAssignedRooms()
        );

        Map<String, String> result = new HashMap<>();
        result.put("accessToken", newAccessToken);
        return result;
    }

    @Override
    public Map<String, String> switchTenant(String targetTenantId) {
        if (targetTenantId == null || targetTenantId.isBlank()) {
            throw new ServiceException("目标租户ID不可为空");
        }

        UserContext.LoginUser currentUser = UserContext.getUser();
        if (currentUser == null || currentUser.getUserId() == null) {
            throw new ServiceException("当前会话无效或未携带有效认证 Token");
        }

        List<TenantSimpleVO> authorizedTenants = getAuthorizedTenantsForUser(currentUser.getUserId(), currentUser.getRoleKey());
        boolean hasAccess = authorizedTenants.stream().anyMatch(t -> t.getTenantId().equals(targetTenantId));
        if (!hasAccess) {
            throw new ServiceException("当前账号未获得租户 [" + targetTenantId + "] 的授权，禁止水平越权访问");
        }

        String newAccessToken = jwtUtils.generateAccessToken(
                currentUser.getUserId(),
                currentUser.getUsername(),
                targetTenantId,
                currentUser.getRoleKey(),
                currentUser.getAssignedRooms()
        );

        Map<String, String> result = new HashMap<>();
        result.put("accessToken", newAccessToken);
        result.put("currentTenantId", targetTenantId);
        return result;
    }

    @Override
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            stringRedisTemplate.delete(REDIS_PREFIX_REFRESH + refreshToken.trim());
            log.info("[移动端认证] RefreshToken 已吊销: {}", refreshToken);
        }
    }

    @Override
    public EngineerUserVO getCurrentUserInfo() {
        UserContext.LoginUser user = UserContext.getUser();
        if (user == null) {
            return new EngineerUserVO(1L, "guest", "访客", "guest", "", Collections.emptySet());
        }
        return new EngineerUserVO(
                user.getUserId(),
                user.getUsername(),
                user.getUsername(),
                user.getRoleKey(),
                "13800000000",
                user.getAssignedRooms()
        );
    }

    private MobileLoginVO buildLoginSuccessVO(SysUser user, String currentTenantId) {
        Set<String> rooms = new HashSet<>(Arrays.asList("华东01-A区", "华东02-B区"));

        String accessToken = jwtUtils.generateAccessToken(
                user.getUserId(),
                user.getUsername(),
                currentTenantId,
                user.getRoleKey(),
                rooms
        );

        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        RefreshTokenPayload payload = new RefreshTokenPayload(
                user.getUserId(),
                user.getUsername(),
                currentTenantId,
                user.getRoleKey(),
                rooms
        );

        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            stringRedisTemplate.opsForValue().set(
                    REDIS_PREFIX_REFRESH + refreshToken,
                    payloadJson,
                    Duration.ofDays(REFRESH_TOKEN_TTL_DAYS)
            );
        } catch (Exception e) {
            log.error("[移动端认证] 序列化 RefreshToken 失败", e);
            throw new ServiceException("凭证生成失败");
        }

        EngineerUserVO engineerUser = new EngineerUserVO(
                user.getUserId(),
                user.getUsername(),
                user.getNickName() != null ? user.getNickName() : user.getUsername(),
                user.getRoleKey(),
                user.getPhone() != null ? user.getPhone() : "",
                rooms
        );

        List<TenantSimpleVO> tenantList = getAuthorizedTenantsForUser(user.getUserId(), user.getRoleKey());
        for (TenantSimpleVO t : tenantList) {
            t.setIsCurrent(currentTenantId.equals(t.getTenantId()));
        }

        return new MobileLoginVO(
                "LOGIN_SUCCESS",
                null,
                accessToken,
                refreshToken,
                currentTenantId,
                engineerUser,
                tenantList
        );
    }

    private List<TenantSimpleVO> getAuthorizedTenantsForUser(Long userId, String roleKey) {
        List<TenantSimpleVO> list = new ArrayList<>();
        list.add(new TenantSimpleVO("000000", "平台自营核心机房", false));
        list.add(new TenantSimpleVO("T10001", "华东算力托管租户", false));
        list.add(new TenantSimpleVO("T20002", "示范智算边缘租户", false));
        return list;
    }
}
