package com.smartidc.biz.domain.vo.mobile;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * MinIO S3 预签名直传凭证 VO (纯 Java 规范)
 */
@Schema(description = "MinIO S3 预签名直传凭证")
public class PresignedUploadVO {

    @Schema(description = "预签名 PUT 直传完整 URL (有效期15分钟)")
    private String uploadUrl;

    @Schema(description = "对象存储唯一 Key")
    private String objectKey;

    @Schema(description = "公网/局域网访问回显 URL")
    private String viewUrl;

    @Schema(description = "过期时间戳 (毫秒)")
    private Long expiresAt;

    public PresignedUploadVO() {
    }

    public PresignedUploadVO(String uploadUrl, String objectKey, String viewUrl, Long expiresAt) {
        this.uploadUrl = uploadUrl;
        this.objectKey = objectKey;
        this.viewUrl = viewUrl;
        this.expiresAt = expiresAt;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }

    public void setUploadUrl(String uploadUrl) {
        this.uploadUrl = uploadUrl;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getViewUrl() {
        return viewUrl;
    }

    public void setViewUrl(String viewUrl) {
        this.viewUrl = viewUrl;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
