package com.smartidc.framework.tenant;

/**
 * 租户上下文持有者 (基于 ThreadLocal)
 */
public class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    /**
     * 默认平台运营方租户编号
     */
    public static final String DEFAULT_TENANT_ID = "000000";

    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenantId() {
        String tenantId = CURRENT_TENANT.get();
        return tenantId != null && !tenantId.isBlank() ? tenantId : DEFAULT_TENANT_ID;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
