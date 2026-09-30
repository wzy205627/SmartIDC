package com.smartidc.common.core.domain;

import com.smartidc.common.enums.ResultCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一 RESTful 响应体模型
 *
 * @param <T> 数据类型
 */
@Schema(description = "统一 API 响应体包装")
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "业务状态码 (200 为成功)", example = "200")
    private int code;

    @Schema(description = "提示消息", example = "操作成功")
    private String msg;

    @Schema(description = "业务承载数据")
    private T data;

    @Schema(description = "响应时间戳", example = "1789476150263")
    private long timestamp = System.currentTimeMillis();

    public R() {
    }

    public R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> R<T> ok() {
        return restResult(null, ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage());
    }

    public static <T> R<T> ok(T data) {
        return restResult(data, ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage());
    }

    public static <T> R<T> ok(T data, String msg) {
        return restResult(data, ResultCode.SUCCESS.getCode(), msg);
    }

    public static <T> R<T> fail() {
        return restResult(null, ResultCode.INTERNAL_SERVER_ERROR.getCode(), ResultCode.INTERNAL_SERVER_ERROR.getMessage());
    }

    public static <T> R<T> fail(String msg) {
        return restResult(null, ResultCode.INTERNAL_SERVER_ERROR.getCode(), msg);
    }

    public static <T> R<T> fail(int code, String msg) {
        return restResult(null, code, msg);
    }

    public static <T> R<T> fail(ResultCode resultCode) {
        return restResult(null, resultCode.getCode(), resultCode.getMessage());
    }

    private static <T> R<T> restResult(T data, int code, String msg) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setData(data);
        r.setMsg(msg);
        return r;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
