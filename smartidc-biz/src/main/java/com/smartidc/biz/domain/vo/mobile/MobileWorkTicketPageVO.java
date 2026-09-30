package com.smartidc.biz.domain.vo.mobile;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 移动随行端机柜维保工单独立分页响应 VO
 */
@Schema(description = "机柜维保工单独立分页响应")
public class MobileWorkTicketPageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "总记录数")
    private Long total;

    @Schema(description = "工单记录列表")
    private List<MobileWorkTicketItemVO> records = new ArrayList<>();

    public MobileWorkTicketPageVO() {
    }

    public MobileWorkTicketPageVO(Long total, List<MobileWorkTicketItemVO> records) {
        this.total = total != null ? total : 0L;
        this.records = records != null ? records : new ArrayList<>();
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public List<MobileWorkTicketItemVO> getRecords() {
        return records;
    }

    public void setRecords(List<MobileWorkTicketItemVO> records) {
        this.records = records;
    }

    @Schema(description = "工单项")
    public static class MobileWorkTicketItemVO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long ticketId;
        private String title;
        private String ticketType; // ALARM_REPAIR, ROUTINE_CHECK
        private Integer status; // 0-待分配, 1-排障中, 2-挂起待审批, 3-已批准, 4-已拒绝, 5-待消警复核, 6-已办结
        private String statusText;
        private String operatorName;
        private LocalDateTime createTime;

        public MobileWorkTicketItemVO() {
        }

        public MobileWorkTicketItemVO(Long ticketId, String title, String ticketType, Integer status, String statusText, String operatorName, LocalDateTime createTime) {
            this.ticketId = ticketId;
            this.title = title;
            this.ticketType = ticketType;
            this.status = status;
            this.statusText = statusText;
            this.operatorName = operatorName;
            this.createTime = createTime;
        }

        public Long getTicketId() {
            return ticketId;
        }

        public void setTicketId(Long ticketId) {
            this.ticketId = ticketId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getTicketType() {
            return ticketType;
        }

        public void setTicketType(String ticketType) {
            this.ticketType = ticketType;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        public String getStatusText() {
            return statusText;
        }

        public void setStatusText(String statusText) {
            this.statusText = statusText;
        }

        public String getOperatorName() {
            return operatorName;
        }

        public void setOperatorName(String operatorName) {
            this.operatorName = operatorName;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }
    }
}
