package com.smartidc.iot.mock;

/**
 * 机架当前资产负荷值对象
 * 包含在架设备总额定功率、占用 U 位与在架设备总数，供动环热力学仿真计算
 */
public class RackLoadInfo {

    private final double totalRatedPowerKw;
    private final int usedUCount;
    private final int deviceCount;

    public RackLoadInfo(double totalRatedPowerKw, int usedUCount, int deviceCount) {
        this.totalRatedPowerKw = totalRatedPowerKw;
        this.usedUCount = usedUCount;
        this.deviceCount = deviceCount;
    }

    public double getTotalRatedPowerKw() {
        return totalRatedPowerKw;
    }

    public int getUsedUCount() {
        return usedUCount;
    }

    public int getDeviceCount() {
        return deviceCount;
    }

    public boolean isEmptyRack() {
        return deviceCount == 0 || totalRatedPowerKw <= 0.001;
    }
}
