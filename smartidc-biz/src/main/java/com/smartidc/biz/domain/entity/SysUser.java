package com.smartidc.biz.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础 RBAC 用户表实体 (对应 sys_user 表)
 */
@TableName("sys_user")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long userId;

    private String tenantId;

    private String username;

    private String password;

    private String nickName;

    private String roleKey; // admin, supervisor, engineer, auditor

    private String phone;

    private Integer status; // 0-正常, 1-停用

    private LocalDateTime createTime;

    public SysUser() {
    }

    public SysUser(Long userId, String tenantId, String username, String password, String nickName, String roleKey, String phone, Integer status, LocalDateTime createTime) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.password = password;
        this.nickName = nickName;
        this.roleKey = roleKey;
        this.phone = phone;
        this.status = status;
        this.createTime = createTime;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
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

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getRoleKey() {
        return roleKey;
    }

    public void setRoleKey(String roleKey) {
        this.roleKey = roleKey;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
