package com.smartidc.biz.service.impl;

import com.smartidc.biz.domain.vo.mobile.PresignedUploadVO;
import com.smartidc.biz.service.MinioStorageService;
import com.smartidc.common.exception.ServiceException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * 智维云 MinIO / S3 兼容对象存储服务实现 (纯 Java 规范)
 */
@Service
public class MinioStorageServiceImpl implements MinioStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageServiceImpl.class);

    @Value("${smartidc.oss.minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${smartidc.oss.minio.access-key:smartidc_admin}")
    private String accessKey;

    @Value("${smartidc.oss.minio.secret-key:smartidc123}")
    private String secretKey;

    @Value("${smartidc.oss.minio.bucket-name:smartidc-inspection}")
    private String bucketName;

    @Value("${smartidc.oss.minio.region:us-east-1}")
    private String region;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public MinioStorageServiceImpl() {
    }

    public MinioStorageServiceImpl(String endpoint, String accessKey, String secretKey, String bucketName, String region) {
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucketName = bucketName;
        this.region = region;
    }

    @PostConstruct
    public void initBucket() {
        try {
            ensureBucketExists();
        } catch (Exception e) {
            log.warn("[MinIO存储] 初始化检查 Bucket 异常 (可能网关尚未就绪): {}", e.getMessage());
        }
    }

    private void ensureBucketExists() {
        try {
            HttpRequest checkReq = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/" + bucketName))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> resp = httpClient.send(checkReq, HttpResponse.BodyHandlers.discarding());
            if (resp.statusCode() == 404) {
                log.info("[MinIO存储] Bucket [{}] 不存在，尝试创建", bucketName);
                HttpRequest createReq = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint + "/" + bucketName))
                        .PUT(HttpRequest.BodyPublishers.noBody())
                        .build();
                httpClient.send(createReq, HttpResponse.BodyHandlers.discarding());
            }
        } catch (Exception e) {
            log.debug("[MinIO存储] 检查 Bucket 结果: {}", e.getMessage());
        }
    }

    @Override
    public PresignedUploadVO generatePresignedUploadUrl(String tenantId, Long ticketId, String fileName, String contentType) {
        String safeTenant = (tenantId == null || tenantId.isBlank()) ? "000000" : tenantId.trim();
        String yyyyMM = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        long timestamp = System.currentTimeMillis();

        String ext = ".jpg";
        if (fileName != null && fileName.contains(".")) {
            ext = fileName.substring(fileName.lastIndexOf('.'));
        }

        String ticketPrefix = ticketId != null ? ("TK" + ticketId) : "TK-GEN";
        String objectKey = "inspection/" + safeTenant + "/" + yyyyMM + "/" + ticketPrefix + "_" + timestamp + ext;

        int expiresSeconds = 900; // 15 分钟
        long expiresAt = System.currentTimeMillis() + (expiresSeconds * 1000L);

        try {
            String uploadUrl = createS3V4PresignedPutUrl(bucketName, objectKey, expiresSeconds);
            String viewUrl = getViewUrl(objectKey);
            return new PresignedUploadVO(uploadUrl, objectKey, viewUrl, expiresAt);
        } catch (Exception e) {
            log.error("[MinIO存储] 生成 S3 V4 预签名 URL 失败", e);
            throw new ServiceException("生成对象存储预签名直传凭证失败: " + e.getMessage());
        }
    }

    @Override
    public PresignedUploadVO uploadDirect(String tenantId, Long ticketId, String fileName, byte[] data, String contentType) {
        PresignedUploadVO presigned = generatePresignedUploadUrl(tenantId, ticketId, fileName, contentType);
        try {
            String ct = (contentType == null || contentType.isBlank()) ? "image/jpeg" : contentType;
            HttpRequest putReq = HttpRequest.newBuilder()
                    .uri(URI.create(presigned.getUploadUrl()))
                    .header("Content-Type", ct)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                    .build();
            HttpResponse<String> resp = httpClient.send(putReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new ServiceException("直传存储失败，HTTP 状态码: " + resp.statusCode());
            }
            return presigned;
        } catch (ServiceException se) {
            throw se;
        } catch (Exception e) {
            log.error("[MinIO存储] 流式中转上传异常", e);
            throw new ServiceException("流式中转存证上传失败: " + e.getMessage());
        }
    }

    @Override
    public boolean objectExists(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return false;
        }
        try {
            HttpRequest headReq = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/" + bucketName + "/" + objectKey))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> resp = httpClient.send(headReq, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            log.warn("[MinIO存储] 校验对象 [{}] 异常: {}", objectKey, e.getMessage());
            return false;
        }
    }

    @Override
    public byte[] getObjectBytes(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new ServiceException("对象 Key 不可为空");
        }
        try {
            HttpRequest getReq = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/" + bucketName + "/" + objectKey))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = httpClient.send(getReq, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200) {
                return resp.body();
            }
            throw new ServiceException("获取存证对象失败, 状态码: " + resp.statusCode());
        } catch (ServiceException se) {
            throw se;
        } catch (Exception e) {
            log.error("[MinIO存储] 拉取对象失败: {}", objectKey, e);
            throw new ServiceException("读取存证对象失败: " + e.getMessage());
        }
    }

    @Override
    public String calculateSha256(byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new ServiceException("计算 SHA-256 哈希失败: " + e.getMessage());
        }
    }

    @Override
    public String calculateObjectSha256(String objectKey) {
        byte[] bytes = getObjectBytes(objectKey);
        return calculateSha256(bytes);
    }

    @Override
    public String getViewUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return "";
        return endpoint + "/" + bucketName + "/" + objectKey;
    }

    private String createS3V4PresignedPutUrl(String bucket, String objectKey, int expiresSeconds) throws Exception {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        URI endpointUri = URI.create(endpoint);
        String host = endpointUri.getHost();
        if (endpointUri.getPort() != -1 && endpointUri.getPort() != 80 && endpointUri.getPort() != 443) {
            host += ":" + endpointUri.getPort();
        }

        String credentialScope = dateStamp + "/" + region + "/s3/aws4_request";
        String encodedScope = URLEncoder.encode(credentialScope, StandardCharsets.UTF_8);
        String canonicalUri = "/" + bucket + "/" + objectKey;

        // 查询参数按字典序排序
        String canonicalQueryString =
                "X-Amz-Algorithm=AWS4-HMAC-SHA256" +
                "&X-Amz-Credential=" + accessKey + "%2F" + encodedScope +
                "&X-Amz-Date=" + amzDate +
                "&X-Amz-Expires=" + expiresSeconds +
                "&X-Amz-SignedHeaders=host";

        String canonicalHeaders = "host:" + host + "\n";
        String signedHeaders = "host";

        String canonicalRequest =
                "PUT\n" +
                canonicalUri + "\n" +
                canonicalQueryString + "\n" +
                canonicalHeaders + "\n" +
                signedHeaders + "\n" +
                "UNSIGNED-PAYLOAD";

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String canonicalRequestHash = HexFormat.of().formatHex(md.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));

        String stringToSign =
                "AWS4-HMAC-SHA256\n" +
                amzDate + "\n" +
                credentialScope + "\n" +
                canonicalRequestHash;

        byte[] kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, dateStamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, "s3");
        byte[] kSigning = hmacSha256(kService, "aws4_request");

        byte[] signatureBytes = hmacSha256(kSigning, stringToSign);
        String signature = HexFormat.of().formatHex(signatureBytes);

        return endpoint + canonicalUri + "?" + canonicalQueryString + "&X-Amz-Signature=" + signature;
    }

    private byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
}
