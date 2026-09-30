package com.smartidc.framework.security;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 当前登录用户信息与权限上下文 (基于 ThreadLocal)
 */
public class UserContext {

    public static class LoginUser {
        private Long userId;
        private String username;
        private String roleKey; // admin, supervisor, engineer
        private Set<String> assignedRooms = new HashSet<>();

        public LoginUser() {
        }

        public LoginUser(Long userId, String username, String roleKey, Set<String> assignedRooms) {
            this.userId = userId;
            this.username = username;
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

    private static final ThreadLocal<LoginUser> CURRENT_USER = new ThreadLocal<>();

    public static void setUser(LoginUser user) {
        CURRENT_USER.set(user);
    }

    public static LoginUser getUser() {
        return CURRENT_USER.get();
    }

    /**
     * 判断当前是否为现场运维工程师 (受机房行级数据权限限制)
     */
    public static boolean isEngineer() {
        LoginUser user = CURRENT_USER.get();
        return user != null && "engineer".equalsIgnoreCase(user.getRoleKey());
    }

    /**
     * 获取当前工程师管辖的机房列表
     */
    public static Set<String> getAssignedRooms() {
        LoginUser user = CURRENT_USER.get();
        return user != null && user.getAssignedRooms() != null ? user.getAssignedRooms() : Collections.emptySet();
    }

    public static Long getUserId() {
        LoginUser user = CURRENT_USER.get();
        return user != null ? user.getUserId() : null;
    }

    public static String getUsername() {
        LoginUser user = CURRENT_USER.get();
        return user != null ? user.getUsername() : null;
    }

    public static String getRoleKey() {
        LoginUser user = CURRENT_USER.get();
        return user != null ? user.getRoleKey() : null;
    }

    public static void setUserId(Long userId) {
        LoginUser u = CURRENT_USER.get();
        if (u == null) {
            u = new LoginUser();
            CURRENT_USER.set(u);
        }
        u.setUserId(userId);
    }

    public static void setUsername(String username) {
        LoginUser u = CURRENT_USER.get();
        if (u == null) {
            u = new LoginUser();
            CURRENT_USER.set(u);
        }
        u.setUsername(username);
    }

    public static void setRoleKey(String roleKey) {
        LoginUser u = CURRENT_USER.get();
        if (u == null) {
            u = new LoginUser();
            CURRENT_USER.set(u);
        }
        u.setRoleKey(roleKey);
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
