package com.smartidc.framework.security;

import com.smartidc.framework.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 用户与租户上下文全局前置过滤器
 * 严格遵循【JWT 唯一权威源】原则：
 * 1. 优先解析 Authorization: Bearer <token>，提取 tenantId, userId, roleKey, assignedRooms；
 * 2. 存在有效 Bearer Token 时，强制采用 Token 中的 tenantId，彻底屏蔽外部伪造的 Tenant-Id 请求头；
 * 3. 无 Token 或公网放行接口时，降级采用 Tenant-Id (默认 000000) 与透传角色，兼顾向后兼容与本地测试。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserContextFilter implements Filter {

    private final JwtUtils jwtUtils;

    public UserContextFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest) {
            String authHeader = httpRequest.getHeader("Authorization");
            boolean tokenAuthenticated = false;

            if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
                String token = authHeader.substring(7).trim();
                Claims claims = jwtUtils.parseToken(token);
                if (claims != null && jwtUtils.validateToken(token)) {
                    Long userId = jwtUtils.getUserId(claims);
                    String username = jwtUtils.getUsername(claims);
                    String tenantId = jwtUtils.getTenantId(claims);
                    String roleKey = jwtUtils.getRoleKey(claims);
                    Set<String> rooms = jwtUtils.getAssignedRooms(claims);

                    if (tenantId != null && !tenantId.isBlank()) {
                        TenantContext.setTenantId(tenantId);
                    } else {
                        TenantContext.setTenantId("000000");
                    }

                    UserContext.LoginUser loginUser = new UserContext.LoginUser(
                            userId != null ? userId : 1L,
                            username != null ? username : "unknown",
                            roleKey != null ? roleKey : "engineer",
                            rooms
                    );
                    UserContext.setUser(loginUser);
                    tokenAuthenticated = true;
                }
            }

            if (!tokenAuthenticated) {
                // 降级模式 (兼容本地开发、无 Token 接口以及历史单元测试透传头)
                String tenantId = httpRequest.getHeader("Tenant-Id");
                if (tenantId != null && !tenantId.isBlank()) {
                    TenantContext.setTenantId(tenantId);
                } else {
                    TenantContext.setTenantId("000000");
                }

                String roleKey = httpRequest.getHeader("X-User-Role");
                String roomsHeader = httpRequest.getHeader("X-User-Rooms");

                Set<String> rooms = new HashSet<>();
                if (roomsHeader != null && !roomsHeader.isBlank()) {
                    String decodedRooms = roomsHeader;
                    if (decodedRooms.contains("%")) {
                        try {
                            decodedRooms = java.net.URLDecoder.decode(decodedRooms, java.nio.charset.StandardCharsets.UTF_8);
                        } catch (Exception ignored) {
                        }
                    } else {
                        try {
                            byte[] bytes = decodedRooms.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
                            String utf8Str = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                            if (utf8Str.length() < decodedRooms.length()) {
                                decodedRooms = utf8Str;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    rooms.addAll(Arrays.asList(decodedRooms.split(",")));
                }

                if (roleKey == null || roleKey.isBlank()) {
                    roleKey = "supervisor";
                }

                UserContext.LoginUser loginUser = new UserContext.LoginUser(1L, "admin", roleKey, rooms);
                UserContext.setUser(loginUser);
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            // 关键：防止 ThreadLocal 内存泄漏与线程复用污染
            TenantContext.clear();
            UserContext.clear();
        }
    }
}
