package com.smartidc.framework.handler;

import com.smartidc.common.core.domain.R;
import com.smartidc.common.enums.ResultCode;
import com.smartidc.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局统一异常拦截与处理
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务自定义异常
     */
    @ExceptionHandler(ServiceException.class)
    public R<Void> handleServiceException(ServiceException e) {
        log.warn("业务异常触发 [code={}, msg={}]", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常
     */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        String msg = e.getAllErrors().isEmpty() ? "参数校验失败" : e.getAllErrors().get(0).getDefaultMessage();
        log.warn("参数校验异常: {}", msg);
        return R.fail(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    /**
     * 请求方式不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("不支持的请求方式: {}", e.getMethod());
        return R.fail(ResultCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 未知运行时异常兜底
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统未知内部异常: ", e);
        return R.fail(ResultCode.INTERNAL_SERVER_ERROR.getCode(), "系统处理异常: " + e.getMessage());
    }
}
