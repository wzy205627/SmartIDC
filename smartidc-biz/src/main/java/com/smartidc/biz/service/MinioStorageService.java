package com.smartidc.biz.service;

import com.smartidc.biz.domain.vo.mobile.PresignedUploadVO;

/**
 * 智维云 MinIO / S3 兼容对象存储服务接口
 */
public interface MinioStorageService {

    /**
     * 为巡检存证生成 15 分钟有效期的 S3 预签名 PUT 直传凭证
     *
     * @param tenantId 租户编号
     * @param ticketId 工单ID
     * @param fileName 原始文件名或扩展名
     * @param contentType 媒体类型 (默认 image/jpeg)
     * @return 预签名直传凭证 VO
     */
    PresignedUploadVO generatePresignedUploadUrl(String tenantId, Long ticketId, String fileName, String contentType);

    /**
     * 后端流式中转兜底直传
     *
     * @param tenantId 租户编号
     * @param ticketId 工单ID
     * @param fileName 文件名
     * @param data 文件字节数组
     * @param contentType 媒体类型
     * @return 存储凭证 VO
     */
    PresignedUploadVO uploadDirect(String tenantId, Long ticketId, String fileName, byte[] data, String contentType);

    /**
     * 判断对象是否存在
     *
     * @param objectKey 存储对象 Key
     * @return true 存在
     */
    boolean objectExists(String objectKey);

    /**
     * 获取对象二进制字节
     *
     * @param objectKey 存储对象 Key
     * @return 字节数组
     */
    byte[] getObjectBytes(String objectKey);

    /**
     * 计算字节数据的 SHA-256 哈希值 (64位小写十六进制)
     *
     * @param data 字节数据
     * @return SHA-256 字符串
     */
    String calculateSha256(byte[] data);

    /**
     * 计算已存对象的 SHA-256 指纹
     *
     * @param objectKey 对象 Key
     * @return SHA-256 指纹
     */
    String calculateObjectSha256(String objectKey);

    /**
     * 获取对象公网/局域网回显访问 URL
     *
     * @param objectKey 对象 Key
     * @return 访问 URL
     */
    String getViewUrl(String objectKey);
}
