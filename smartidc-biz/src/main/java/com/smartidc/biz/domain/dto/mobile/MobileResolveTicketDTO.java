package com.smartidc.biz.domain.dto.mobile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 移动随行端现场消警与存证提交 DTO (纯 Java 规范)
 */
@Schema(description = "移动端现场消警与存证提交参数")
public class MobileResolveTicketDTO {

    @NotNull(message = "工单ID不可为空")
    @Schema(description = "工单ID")
    private Long ticketId;

    @NotBlank(message = "现场排障记录不可为空")
    @Schema(description = "现场处置记录与故障根因消除说明")
    private String processNotes;

    @NotBlank(message = "现场照片存证 Key 不可为空")
    @Schema(description = "MinIO 对象存储 Key")
    private String evidenceObjectKey;

    @Schema(description = "照片 SHA-256 哈希指纹 (防篡改)")
    private String evidenceHash;

    @Schema(description = "机架权威物理位置描述")
    private String rackLocation;

    public MobileResolveTicketDTO() {
    }

    public MobileResolveTicketDTO(Long ticketId, String processNotes, String evidenceObjectKey, String evidenceHash, String rackLocation) {
        this.ticketId = ticketId;
        this.processNotes = processNotes;
        this.evidenceObjectKey = evidenceObjectKey;
        this.evidenceHash = evidenceHash;
        this.rackLocation = rackLocation;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getProcessNotes() {
        return processNotes;
    }

    public void setProcessNotes(String processNotes) {
        this.processNotes = processNotes;
    }

    public String getEvidenceObjectKey() {
        return evidenceObjectKey;
    }

    public void setEvidenceObjectKey(String evidenceObjectKey) {
        this.evidenceObjectKey = evidenceObjectKey;
    }

    public String getEvidenceHash() {
        return evidenceHash;
    }

    public void setEvidenceHash(String evidenceHash) {
        this.evidenceHash = evidenceHash;
    }

    public String getRackLocation() {
        return rackLocation;
    }

    public void setRackLocation(String rackLocation) {
        this.rackLocation = rackLocation;
    }
}
