package com.smartidc.biz.controller.mobile;

import com.smartidc.biz.domain.vo.mobile.PresignedUploadVO;
import com.smartidc.biz.service.MinioStorageService;
import com.smartidc.common.core.domain.R;
import com.smartidc.framework.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 移动随行端对象存储与 S3 预签名直传中枢控制器
 */
@Tag(name = "移动随行端对象存储与存证中枢")
@RestController
@RequestMapping("/api/v1/mobile/oss")
public class MobileOssController {

    private final MinioStorageService minioStorageService;

    public MobileOssController(MinioStorageService minioStorageService) {
        this.minioStorageService = minioStorageService;
    }

    @Operation(summary = "申请 MinIO S3 预签名 PUT 直传凭证 (15分钟有效期)")
    @PostMapping("/presigned-url")
    public R<PresignedUploadVO> getPresignedUploadUrl(
            @Parameter(description = "关联工单ID (可为空)")
            @RequestParam(value = "ticketId", required = false) Long ticketId,
            @Parameter(description = "文件名 (如 inspection.jpg)")
            @RequestParam(value = "fileName", defaultValue = "inspection.jpg") String fileName,
            @Parameter(description = "文件类型/扩展名")
            @RequestParam(value = "fileType", defaultValue = "jpg") String fileType,
            @Parameter(description = "媒体类型")
            @RequestParam(value = "contentType", defaultValue = "image/jpeg") String contentType) {

        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "000000";
        }

        String safeFileName = (fileName != null && !fileName.isBlank()) ? fileName : ("photo." + fileType);
        PresignedUploadVO vo = minioStorageService.generatePresignedUploadUrl(tenantId, ticketId, safeFileName, contentType);
        return R.ok(vo, "申请预签名直传凭证成功");
    }

    @Operation(summary = "移动端流式中转兜底上传 (当专网无法直连 S3 网关时)")
    @PostMapping("/upload")
    public R<PresignedUploadVO> uploadFile(
            @Parameter(description = "现场照片文件")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "关联工单ID (可为空)")
            @RequestParam(value = "ticketId", required = false) Long ticketId) throws IOException {

        if (file == null || file.isEmpty()) {
            return R.fail(400, "上传文件不可为空");
        }

        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "000000";
        }

        PresignedUploadVO vo = minioStorageService.uploadDirect(
                tenantId,
                ticketId,
                file.getOriginalFilename(),
                file.getBytes(),
                file.getContentType()
        );

        return R.ok(vo, "存证照片中转上传成功");
    }
}
