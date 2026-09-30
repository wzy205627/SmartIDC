package com.smartidc.biz.oss;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

public class MinioPresignedTest {

    private static final String ENDPOINT = "http://localhost:9000";
    private static final String ACCESS_KEY = "smartidc_admin";
    private static final String SECRET_KEY = "smartidc123";
    private static final String BUCKET = "smartidc-inspection";
    private static final String REGION = "us-east-1";

    @Test
    @DisplayName("测试 MinIO S3 V4 预签名 PUT 直传与 SHA-256 存证")
    public void testMinioPresignedUploadAndSha256() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        // 1. 创建 Bucket (如果不存在)
        try {
            HttpRequest checkReq = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT + "/" + BUCKET))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> checkResp = client.send(checkReq, HttpResponse.BodyHandlers.discarding());
            if (checkResp.statusCode() == 404) {
                HttpRequest createReq = HttpRequest.newBuilder()
                        .uri(URI.create(ENDPOINT + "/" + BUCKET))
                        .PUT(HttpRequest.BodyPublishers.noBody())
                        .build();
                client.send(createReq, HttpResponse.BodyHandlers.discarding());
            }
        } catch (Exception ignored) {}

        // 2. 生成 S3 V4 预签名 PUT URL
        String objectKey = "inspection/test/202609/TK-TEST-001_" + System.currentTimeMillis() + ".jpg";
        String presignedUrl = generateV4PresignedPutUrl(BUCKET, objectKey, 900);
        assertNotNull(presignedUrl);
        assertTrue(presignedUrl.contains("X-Amz-Signature="));

        // 3. 模拟前端通过 HTTP PUT 上传文件字节流
        byte[] testData = "SMARTIDC-WATERMARK-EVIDENCE-TEST-IMAGE-BYTES".getBytes(StandardCharsets.UTF_8);
        HttpRequest uploadReq = HttpRequest.newBuilder()
                .uri(URI.create(presignedUrl))
                .header("Content-Type", "image/jpeg")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(testData))
                .build();
        HttpResponse<String> uploadResp = client.send(uploadReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, uploadResp.statusCode(), "MinIO 预签名直传应返回 200 OK: " + uploadResp.body());

        // 4. 验证计算 SHA-256 哈希防篡改指纹
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = md.digest(testData);
        String sha256Hex = HexFormat.of().formatHex(hashBytes);
        assertEquals(64, sha256Hex.length(), "SHA-256 哈希指纹应为 64 位十六进制字符");

        // 5. 验证直读下载回显
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT + "/" + BUCKET + "/" + objectKey))
                .GET()
                .build();
        HttpResponse<byte[]> getResp = client.send(getReq, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, getResp.statusCode());
        assertArrayEquals(testData, getResp.body());
    }

    private String generateV4PresignedPutUrl(String bucket, String objectKey, int expiresSeconds) throws Exception {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        URI endpointUri = URI.create(ENDPOINT);
        String host = endpointUri.getHost();
        if (endpointUri.getPort() != -1 && endpointUri.getPort() != 80 && endpointUri.getPort() != 443) {
            host += ":" + endpointUri.getPort();
        }

        String credentialScope = dateStamp + "/" + REGION + "/s3/aws4_request";
        String encodedScope = URLEncoder.encode(credentialScope, StandardCharsets.UTF_8);

        String canonicalUri = "/" + bucket + "/" + objectKey;

        // 查询参数字典序排序
        String canonicalQueryString =
                "X-Amz-Algorithm=AWS4-HMAC-SHA256" +
                "&X-Amz-Credential=" + ACCESS_KEY + "%2F" + encodedScope +
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

        byte[] kSecret = ("AWS4" + SECRET_KEY).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, dateStamp);
        byte[] kRegion = hmacSha256(kDate, REGION);
        byte[] kService = hmacSha256(kRegion, "s3");
        byte[] kSigning = hmacSha256(kService, "aws4_request");

        byte[] signatureBytes = hmacSha256(kSigning, stringToSign);
        String signature = HexFormat.of().formatHex(signatureBytes);

        return ENDPOINT + canonicalUri + "?" + canonicalQueryString + "&X-Amz-Signature=" + signature;
    }

    private byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
}
