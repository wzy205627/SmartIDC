package com.smartidc.framework.annotation;

import java.lang.annotation.*;

/**
 * 机房行级数据权限范围控制注解
 * 声明在 Controller 或 Service 方法上，自动对当前用户角色做机房范围过滤
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /**
     * 机房字段名 (或带表别名，如 r.room_name)
     */
    String roomAlias() default "room_name";
}
