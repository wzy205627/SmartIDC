package com.smartidc.biz.domain.vo.mobile;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * 移动随行端工程师个人信息出参 VO
 */
@Schema(description = "工程师用户信息")
public class EngineerUserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "工号/账号")
    private String username;

    @Schema(description = "用户姓名/昵称")
    private String nickName;

    @Schema(description = "角色标识 (engineer, supervisor 等)")
    private String roleKey;

    @Schema(description = "手机号码")
    private String phone;

    @Schema(description = "管辖机房列表")
    private Set<String> assignedRooms = new HashSet<>();

    public EngineerUserVO() {
    }

    public EngineerUserVO(Long userId, String username, String nickName, String roleKey, String phone, Set<String> assignedRooms) {
        this.userId = userId;
        this.username = username;
        this.nickName = nickName;
        this.roleKey = roleKey;
        this.phone = phone;
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

    public Set<String> getAssignedRooms() {
        return assignedRooms;
    }

    public void setAssignedRooms(Set<String> assignedRooms) {
        this.assignedRooms = assignedRooms;
    }
}
