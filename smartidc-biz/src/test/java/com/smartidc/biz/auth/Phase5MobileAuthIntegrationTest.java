package com.smartidc.biz.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartidc.biz.domain.dto.mobile.MobilePasswordLoginDTO;
import com.smartidc.biz.domain.dto.mobile.WxBindRequestDTO;
import com.smartidc.biz.domain.dto.mobile.WxLoginRequestDTO;
import com.smartidc.biz.domain.entity.SysUser;
import com.smartidc.biz.domain.vo.mobile.MobileLoginVO;
import com.smartidc.biz.mapper.SysUserMapper;
import com.smartidc.biz.service.impl.MobileAuthServiceImpl;
import com.smartidc.common.exception.ServiceException;
import com.smartidc.framework.security.JwtUtils;
import com.smartidc.framework.security.UserContext;
import com.smartidc.framework.security.UserContextFilter;
import com.smartidc.framework.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 智维云 (SmartIDC) 阶段 5.1：移动随行端统一身份认证、双 Token 静默续期与多租户隔离单元测试
 * 对标 implementation_plan5.1.md 第四节验收清单 (V2, V3, V4, 双Token闭环)
 */
public class Phase5MobileAuthIntegrationTest {

    private JwtUtils jwtUtils;
    private SysUserMapper sysUserMapper;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private MobileAuthServiceImpl mobileAuthService;
    private UserContextFilter userContextFilter;

    // 内存模拟 Redis 键值字典
    private final Map<String, String> mockRedisStorage = new ConcurrentHashMap<>();

    // 预设测试工程师用户 (对应 seed 数据)
    private SysUser mockEngineer;
    // 预设被停用用户
    private SysUser mockDisabledUser;
    // 预设非运维人员
    private SysUser mockAuditorUser;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockRedisStorage.clear();

        // 1. 初始化 JWT 工具类
        jwtUtils = new JwtUtils("SmartIDC-Enterprise-AiOps-Mobile-Security-Token-SecretKey-2026-HighEntropy512Bit!", 7200);

        // 2. 初始化用户 Mapper Mock
        sysUserMapper = mock(SysUserMapper.class);

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String pwdHash = encoder.encode("123456");

        mockEngineer = new SysUser(
                3L,
                "000000",
                "engineer_li",
                pwdHash,
                "李工 (驻场运维工程师)",
                "engineer",
                "13800000002",
                0,
                LocalDateTime.now()
        );

        mockDisabledUser = new SysUser(
                5L,
                "000000",
                "disabled_user",
                pwdHash,
                "停用员工",
                "engineer",
                "13800000005",
                1, // 停用状态
                LocalDateTime.now()
        );

        mockAuditorUser = new SysUser(
                6L,
                "000000",
                "auditor_wang",
                pwdHash,
                "王审计",
                "auditor", // 非运维角色
                "13800000006",
                0,
                LocalDateTime.now()
        );

        when(sysUserMapper.selectByUsername("engineer_li")).thenReturn(mockEngineer);
        when(sysUserMapper.selectByUsername("ENG-001")).thenReturn(mockEngineer);
        when(sysUserMapper.selectByUsername("disabled_user")).thenReturn(mockDisabledUser);
        when(sysUserMapper.selectByUsername("auditor_wang")).thenReturn(mockAuditorUser);
        when(sysUserMapper.selectById(3L)).thenReturn(mockEngineer);
        when(sysUserMapper.selectById(5L)).thenReturn(mockDisabledUser);
        when(sysUserMapper.selectById(6L)).thenReturn(mockAuditorUser);

        // 3. 内存模拟 Redis ValueOperations
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return mockRedisStorage.get(key);
        }).when(valueOperations).get(anyString());

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String val = invocation.getArgument(1);
            mockRedisStorage.put(key, val);
            return null;
        }).when(valueOperations).set(anyString(), anyString());

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String val = invocation.getArgument(1);
            mockRedisStorage.put(key, val);
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            mockRedisStorage.remove(key);
            return true;
        }).when(stringRedisTemplate).delete(anyString());

        objectMapper = new ObjectMapper();

        mobileAuthService = new MobileAuthServiceImpl(sysUserMapper, jwtUtils, stringRedisTemplate, objectMapper);
        userContextFilter = new UserContextFilter(jwtUtils);
    }

    @Test
    @DisplayName("验收指标 V2：微信 Mock 离线登录测试")
    void testV2_WxLoginMockMode() {
        WxLoginRequestDTO request = new WxLoginRequestDTO();
        request.setMock(true);
        request.setMockUserCode("ENG-001");

        MobileLoginVO vo = mobileAuthService.wxLogin(request);

        assertNotNull(vo);
        assertEquals("LOGIN_SUCCESS", vo.getAuthState());
        assertNotNull(vo.getAccessToken(), "必须成功签发业务 Access Token");
        assertNotNull(vo.getRefreshToken(), "必须成功签发长期 Refresh Token");
        assertEquals("000000", vo.getCurrentTenantId());
        assertNotNull(vo.getUser());
        assertEquals("engineer_li", vo.getUser().getUsername());
        assertEquals("engineer", vo.getUser().getRoleKey());
        assertFalse(vo.getTenantList().isEmpty(), "必须返回工程师被授权的租户列表");

        // 校验 Access Token 内容
        Claims claims = jwtUtils.parseToken(vo.getAccessToken());
        assertNotNull(claims);
        assertEquals("engineer_li", jwtUtils.getUsername(claims));
        assertEquals("000000", jwtUtils.getTenantId(claims));
        assertEquals("engineer", jwtUtils.getRoleKey(claims));
    }

    @Test
    @DisplayName("验收指标 V3：微信首绑三态拦截验证 (新用户首绑 -> 正常登录 -> 停用拦截)")
    void testV3_WxThreeStateBindingFlow() {
        String testCode = "wx_test_code_12345";
        String testOpenid = "wx_test_code_12345";

        // 1. 状态 2: NEED_BIND (新微信用户首次扫码，未绑定工号)
        WxLoginRequestDTO firstLogin = new WxLoginRequestDTO();
        firstLogin.setCode(testCode);
        firstLogin.setMock(false);

        MobileLoginVO needBindVO = mobileAuthService.wxLogin(firstLogin);
        assertNotNull(needBindVO);
        assertEquals("NEED_BIND", needBindVO.getAuthState());
        assertNotNull(needBindVO.getBindTicket(), "必须下发绑定临时凭据 bindTicket");
        assertNull(needBindVO.getAccessToken(), "未绑定前严禁签发 AccessToken");

        String bindTicket = needBindVO.getBindTicket();

        // 2. 模拟密码错误首绑 -> 必须拦截抛出异常
        WxBindRequestDTO wrongPwdBind = new WxBindRequestDTO(bindTicket, "engineer_li", "wrong_password");
        assertThrows(ServiceException.class, () -> mobileAuthService.bindWechat(wrongPwdBind));

        // 3. 正确首绑 -> 成功绑定并签发 Token
        WxBindRequestDTO validBind = new WxBindRequestDTO(bindTicket, "engineer_li", "123456");
        MobileLoginVO bindSuccessVO = mobileAuthService.bindWechat(validBind);
        assertNotNull(bindSuccessVO);
        assertEquals("LOGIN_SUCCESS", bindSuccessVO.getAuthState());
        assertNotNull(bindSuccessVO.getAccessToken());

        // 验证 bindTicket 已经被安全销毁
        assertNull(mockRedisStorage.get("smartidc:auth:bind:" + bindTicket));
        // 验证 openid 已与 userId=3 绑定
        assertEquals("3", mockRedisStorage.get("smartidc:auth:wx:openid:" + testOpenid));

        // 4. 再次扫码 -> 状态 1: LOGIN_SUCCESS 直接登录放行
        MobileLoginVO secondLoginVO = mobileAuthService.wxLogin(firstLogin);
        assertEquals("LOGIN_SUCCESS", secondLoginVO.getAuthState());
        assertEquals("engineer_li", secondLoginVO.getUser().getUsername());

        // 5. 状态 3: UNAUTHORIZED_ACCESS (绑定用户被停用时的安全阻断)
        String disabledOpenid = "wx_disabled_999";
        mockRedisStorage.put("smartidc:auth:wx:openid:" + disabledOpenid, "5"); // 绑定至 disabled_user (status=1)

        WxLoginRequestDTO disabledLogin = new WxLoginRequestDTO();
        disabledLogin.setCode(disabledOpenid);
        disabledLogin.setMock(false);

        MobileLoginVO disabledVO = mobileAuthService.wxLogin(disabledLogin);
        assertEquals("UNAUTHORIZED_ACCESS", disabledVO.getAuthState(), "账号停用必须返回未授权状态");
        assertNull(disabledVO.getAccessToken(), "被封禁账号不得下发任何凭证");
    }

    @Test
    @DisplayName("验收指标 V4：租户切换与 JWT 唯一事实源防越权测试")
    void testV4_TenantSwitchAndSingleSourceOfTruth() throws ServletException, IOException {
        // 1. 模拟登录获取初始租户 000000 的 Access Token
        String initialToken = jwtUtils.generateAccessToken(3L, "engineer_li", "000000", "engineer", Set.of("华东01-A区"));

        // 2. 模拟发起业务请求，测试 UserContextFilter 鉴权:
        //    黑客尝试在 Header 中恶意伪造 "Tenant-Id: T20002"
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + initialToken);
        request.addHeader("Tenant-Id", "T20002"); // 伪造租户 Header
        MockHttpServletResponse response = new MockHttpServletResponse();

        final String[] resolvedTenantId = new String[1];
        final String[] resolvedRoleKey = new String[1];

        userContextFilter.doFilter(request, response, (req, res) -> {
            // 在 Filter 内部校验上下文
            resolvedTenantId[0] = TenantContext.getTenantId();
            resolvedRoleKey[0] = UserContext.getUser().getRoleKey();
        });

        // 断言：上下文强制取自 JWT 载荷的 000000，恶意伪造的 T20002 被彻底无视！
        assertEquals("000000", resolvedTenantId[0], "必须以 JWT 载荷中的 tenantId 为唯一权威源，杜绝伪造 Header 越权");
        assertEquals("engineer", resolvedRoleKey[0]);

        // 3. 合法合规切换租户至 T10001
        UserContext.setUser(new UserContext.LoginUser(3L, "engineer_li", "engineer", Set.of("华东01-A区")));
        Map<String, String> switchResult = mobileAuthService.switchTenant("T10001");
        assertNotNull(switchResult.get("accessToken"));
        assertEquals("T10001", switchResult.get("currentTenantId"));

        // 4. 使用切换后的新 Token 发起请求
        String switchedToken = switchResult.get("accessToken");
        MockHttpServletRequest switchedRequest = new MockHttpServletRequest();
        switchedRequest.addHeader("Authorization", "Bearer " + switchedToken);
        switchedRequest.addHeader("Tenant-Id", "999999"); // 再次伪造无效租户

        userContextFilter.doFilter(switchedRequest, response, (req, res) -> {
            resolvedTenantId[0] = TenantContext.getTenantId();
        });
        assertEquals("T10001", resolvedTenantId[0], "切换后的租户上下文必须与新 Token 严格保持一致");

        // 5. 非法越权切换测试 (切换到未授权租户 T88888 -> 抛出越权异常)
        assertThrows(ServiceException.class, () -> mobileAuthService.switchTenant("T88888"));

        UserContext.clear();
        TenantContext.clear();
    }

    @Test
    @DisplayName("功能测试：双 Token 静默刷新与主动登出即时吊销闭环")
    void testDoubleTokenSilentRefreshAndLogout() {
        // 1. 账密登录
        MobilePasswordLoginDTO loginDTO = new MobilePasswordLoginDTO("engineer_li", "123456", "000000");
        MobileLoginVO loginVO = mobileAuthService.passwordLogin(loginDTO);
        assertNotNull(loginVO.getAccessToken());
        assertNotNull(loginVO.getRefreshToken());

        String refreshToken = loginVO.getRefreshToken();

        // 2. 利用 RefreshToken 置换新 AccessToken
        Map<String, String> refreshResult = mobileAuthService.refreshToken(refreshToken);
        assertNotNull(refreshResult.get("accessToken"));
        String newAccessToken = refreshResult.get("accessToken");

        Claims claims = jwtUtils.parseToken(newAccessToken);
        assertEquals("engineer_li", jwtUtils.getUsername(claims));
        assertEquals("000000", jwtUtils.getTenantId(claims));

        // 3. 主动登出
        mobileAuthService.logout(refreshToken);
        assertNull(mockRedisStorage.get("smartidc:auth:refresh:" + refreshToken));

        // 4. 登出后再尝试刷新 -> 必须抛出凭证失效异常
        assertThrows(ServiceException.class, () -> mobileAuthService.refreshToken(refreshToken));
    }
}
