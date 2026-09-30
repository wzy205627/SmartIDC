package com.smartidc.common.enums;

import lombok.Getter;

/**
 * 业务响应状态码枚举
 */
@Getter
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数异常"),
    UNAUTHORIZED(401, "未授权或登录已过期"),
    FORBIDDEN(403, "无权限执行当前操作"),
    NOT_FOUND(404, "请求资源未找到"),
    METHOD_NOT_ALLOWED(405, "不支持当前请求方法"),
    INTERNAL_SERVER_ERROR(500, "系统内部繁忙，请稍后再试"),

    // 空间资产与 U 位冲突
    RACK_NOT_FOUND(1001, "指定机架不存在"),
    RACK_SLOT_OCCUPIED(1002, "该机柜对应 U 位插槽已被占用，无法重复上架"),
    RACK_POWER_EXCEEDED(1003, "上架设备总功率超出该机架额定容量"),

    // 告警与 AIOps 排障
    ALARM_NOT_FOUND(2001, "告警事件不存在"),
    TICKET_HITL_SUSPENDED(2002, "当前操作判定为高危控制动作，工单已挂起等待主管审批"),
    TICKET_ALREADY_PROCESSED(2003, "工单已处理，请勿重复操作"),
    CHECKPOINT_NOT_FOUND(2004, "AIOps 断点快照丢失或已过期"),

    // 租户与鉴权
    TENANT_DISABLED(3001, "当前租户已停用或欠费锁定"),
    USER_NOT_FOUND(3002, "用户不存在或密码错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
