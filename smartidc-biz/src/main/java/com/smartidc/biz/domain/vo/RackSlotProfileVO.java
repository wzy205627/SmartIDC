package com.smartidc.biz.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 机柜 42U 插槽画像视图对象 (VO)
 */
@Schema(description = "机架单U位插槽画像视图")
public class RackSlotProfileVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "U位编号 (1-42)", example = "40")
    private Integer slotU;

    @Schema(description = "是否被占用", example = "true")
    private Boolean isOccupied;

    @Schema(description = "是否为该设备起始U位 (用于多U合并渲染)", example = "true")
    private Boolean isDeviceHead;

    @Schema(description = "设备ID (空闲时为null)", example = "3")
    private Long deviceId;

    @Schema(description = "设备名称", example = "核心交换机 H3C-S6800")
    private String deviceName;

    @Schema(description = "设备类型: IT_SERVER, IT_SWITCH, SENSOR_TEMP, SENSOR_UPS", example = "IT_SWITCH")
    private String deviceType;

    @Schema(description = "物联网设备Key", example = "SW-A03-001")
    private String iotDeviceKey;

    @Schema(description = "设备起始U位", example = "40")
    private Integer startU;

    @Schema(description = "设备占用U位总高", example = "2")
    @JsonProperty("uHeight")
    private Integer uHeight;

    @Schema(description = "额定功耗 (kW)", example = "0.80")
    private BigDecimal ratedPower;

    @Schema(description = "设备运行状态: 0-离线, 1-正常, 2-告警, 3-故障", example = "1")
    private Integer status;

    public RackSlotProfileVO() {
    }

    public static RackSlotProfileVO emptySlot(int slotU) {
        RackSlotProfileVO vo = new RackSlotProfileVO();
        vo.setSlotU(slotU);
        vo.setIsOccupied(false);
        vo.setIsDeviceHead(false);
        return vo;
    }

    public Integer getSlotU() {
        return slotU;
    }

    public void setSlotU(Integer slotU) {
        this.slotU = slotU;
    }

    public Boolean getIsOccupied() {
        return isOccupied;
    }

    public void setIsOccupied(Boolean occupied) {
        isOccupied = occupied;
    }

    public Boolean getIsDeviceHead() {
        return isDeviceHead;
    }

    public void setIsDeviceHead(Boolean deviceHead) {
        isDeviceHead = deviceHead;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getIotDeviceKey() {
        return iotDeviceKey;
    }

    public void setIotDeviceKey(String iotDeviceKey) {
        this.iotDeviceKey = iotDeviceKey;
    }

    public Integer getStartU() {
        return startU;
    }

    public void setStartU(Integer startU) {
        this.startU = startU;
    }

    public Integer getUHeight() {
        return uHeight;
    }

    public void setUHeight(Integer uHeight) {
        this.uHeight = uHeight;
    }

    public BigDecimal getRatedPower() {
        return ratedPower;
    }

    public void setRatedPower(BigDecimal ratedPower) {
        this.ratedPower = ratedPower;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
