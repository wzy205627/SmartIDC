package com.smartidc.biz.domain.dto.mobile;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * 存入 Redis 的 Refresh Token 载荷对象
 */
public class RefreshTokenPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String tenantId;
    private String roleKey;
    private Set<String> assignedRooms = new HashSet<>();

    public RefreshTokenPayload() {
    }

    public RefreshTokenPayload(Long userId, String username, String tenantId, String roleKey, Set<String> assignedRooms) {
        this.userId = userId;
        this.username = username;
        this.tenantId = tenantId;
        this.roleKey = roleKey;
        this.assignedRooms = assignedRooms != null ? assignedRooms : new HashSet<>();
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getRoleKey() {
        return roleKey;
    }

    public void setRoleKey(String roleKey) {
        this.roleKey = roleKey;
    }

    public Set<String> getAssignedRooms() {
        return assignedRooms;
    }

    public void setAssignedRooms(Set<String> assignedRooms) {
        this.assignedRooms = assignedRooms;
    }
}
