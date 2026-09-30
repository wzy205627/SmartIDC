package com.smartidc.iot.mock;

/**
 * 动环在管机柜元数据 (支持跨多租户动态发现)
 */
public class ManagedRackMeta {

    private final String tenantId;
    private final String rackCode;

    public ManagedRackMeta(String tenantId, String rackCode) {
        this.tenantId = tenantId;
        this.rackCode = rackCode;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getRackCode() {
        return rackCode;
    }
}
