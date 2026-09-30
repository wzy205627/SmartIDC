package com.smartidc.biz.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 租户机房能耗与租赁计费结算表实体 (对应表 idc_billing_statement)
 * 纯 Java POJO 规范 (严禁 Lombok)
 */
@TableName("idc_billing_statement")
@Schema(description = "租户机房能耗与租赁计费结算账单实体")
public class IdcBillingStatement implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "账单主键ID", example = "1")
    private Long billId;

    @Schema(description = "租户ID", example = "T10001")
    private String tenantId;

    @Schema(description = "结算月份: 如 2026-08", example = "2026-08")
    private String billingMonth;

    @Schema(description = "机位租赁固定费用(元)", example = "3500.00")
    private BigDecimal rackRentalFee;

    @Schema(description = "当月实际耗电度数(kWh)", example = "3500.00")
    private BigDecimal powerKwh;

    @Schema(description = "设备电费总额(元)", example = "3100.00")
    private BigDecimal powerFee;

    @Schema(description = "当月机房核算 PUE 能效因子", example = "1.25")
    private BigDecimal pueFactor;

    @Schema(description = "制冷与动环公摊能耗费(元)", example = "775.00")
    private BigDecimal pueShareFee;

    @Schema(description = "应付总金额", example = "7375.00")
    private BigDecimal totalAmount;

    @Schema(description = "支付状态: 0-未支付, 1-已扣款结清, 2-逾期欠费", example = "0")
    private Integer paymentStatus;

    @Schema(description = "账单生成时间")
    private LocalDateTime createTime;

    public IdcBillingStatement() {
    }

    public IdcBillingStatement(Long billId, String tenantId, String billingMonth, BigDecimal rackRentalFee,
                               BigDecimal powerKwh, BigDecimal powerFee, BigDecimal pueFactor,
                               BigDecimal pueShareFee, BigDecimal totalAmount, Integer paymentStatus,
                               LocalDateTime createTime) {
        this.billId = billId;
        this.tenantId = tenantId;
        this.billingMonth = billingMonth;
        this.rackRentalFee = rackRentalFee;
        this.powerKwh = powerKwh;
        this.powerFee = powerFee;
        this.pueFactor = pueFactor;
        this.pueShareFee = pueShareFee;
        this.totalAmount = totalAmount;
        this.paymentStatus = paymentStatus;
        this.createTime = createTime;
    }

    public Long getBillId() {
        return billId;
    }

    public void setBillId(Long billId) {
        this.billId = billId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getBillingMonth() {
        return billingMonth;
    }

    public void setBillingMonth(String billingMonth) {
        this.billingMonth = billingMonth;
    }

    public BigDecimal getRackRentalFee() {
        return rackRentalFee;
    }

    public void setRackRentalFee(BigDecimal rackRentalFee) {
        this.rackRentalFee = rackRentalFee;
    }

    public BigDecimal getPowerKwh() {
        return powerKwh;
    }

    public void setPowerKwh(BigDecimal powerKwh) {
        this.powerKwh = powerKwh;
    }

    public BigDecimal getPowerFee() {
        return powerFee;
    }

    public void setPowerFee(BigDecimal powerFee) {
        this.powerFee = powerFee;
    }

    public BigDecimal getPueFactor() {
        return pueFactor;
    }

    public void setPueFactor(BigDecimal pueFactor) {
        this.pueFactor = pueFactor;
    }

    public BigDecimal getPueShareFee() {
        return pueShareFee;
    }

    public void setPueShareFee(BigDecimal pueShareFee) {
        this.pueShareFee = pueShareFee;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Integer getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(Integer paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "IdcBillingStatement{" +
                "billId=" + billId +
                ", tenantId='" + tenantId + '\'' +
                ", billingMonth='" + billingMonth + '\'' +
                ", rackRentalFee=" + rackRentalFee +
                ", powerKwh=" + powerKwh +
                ", powerFee=" + powerFee +
                ", pueFactor=" + pueFactor +
                ", pueShareFee=" + pueShareFee +
                ", totalAmount=" + totalAmount +
                ", paymentStatus=" + paymentStatus +
                ", createTime=" + createTime +
                '}';
    }
}
