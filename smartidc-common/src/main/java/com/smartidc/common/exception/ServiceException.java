package com.smartidc.common.exception;

import com.smartidc.common.enums.ResultCode;

/**
 * 业务全局受检异常
 */
public class ServiceException extends RuntimeException {

    private final int code;

    public ServiceException(String message) {
        super(message);
        this.code = ResultCode.INTERNAL_SERVER_ERROR.getCode();
    }

    public ServiceException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public ServiceException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
