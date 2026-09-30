package com.smartidc.biz.service;

import com.smartidc.biz.domain.entity.IdcBillingStatement;

import java.math.BigDecimal;
import java.util.List;

/**
 * IDC 能源能效 (PUE) 与月度租赁电费结算服务接口
 */
public interface IdcBillingService {

    /**
     * 电表物理量程上限 (默认 10 万度量程，用于翻转补偿)
     */
    BigDecimal METER_MAX_CAPACITY = BigDecimal.valueOf(100000.0);

    /**
     * 单机架月度基准空间租金 (3500.00 元/机架/月)
     */
    BigDecimal BASE_RACK_MONTHLY_RENT = new BigDecimal("3500.00");

    /**
     * 阶梯电费首档上限额度 (3000.00 kWh/机架)
     */
    BigDecimal POWER_TIER1_CAP_KWH = new BigDecimal("3000.00");

    /**
     * 阶梯电费首档单价 (0.85 元/kWh)
     */
    BigDecimal POWER_TIER1_RATE = new BigDecimal("0.85");

    /**
     * 阶梯电费超额二档单价 (1.10 元/kWh)
     */
    BigDecimal POWER_TIER2_RATE = new BigDecimal("1.10");

    /**
     * PUE 基准默认值 (除零保护安全基准)
     */
    BigDecimal PUE_DEFAULT_BASE = new BigDecimal("1.25");

    /**
     * PUE 物理极值下限 (热力学第二定律防线: PUE >= 1.00)
     */
    BigDecimal PUE_MIN_LIMIT = new BigDecimal("1.00");

    /**
     * PUE 合同约定封顶上限 (超过部分由机房自负: PUE <= 1.45)
     */
    BigDecimal PUE_MAX_CONTRACT_CAP = new BigDecimal("1.45");

    /**
     * 账单未支付状态
     */
    int PAYMENT_STATUS_UNPAID = 0;

    /**
     * 账单已支付结清状态
     */
    int PAYMENT_STATUS_PAID = 1;

    /**
     * 账单逾期欠费状态
     */
    int PAYMENT_STATUS_OVERDUE = 2;

    /**
     * 执行指定月份的全机房月度计费自动结算 (带 Redisson 分布式排他锁保护)
     *
     * @param billingMonth    结算月份 (如 2026-08)
     * @param forceRecompute 是否强制重算未支付账单
     * @return 生成或更新的账单清单
     */
    List<IdcBillingStatement> executeMonthlySettlement(String billingMonth, boolean forceRecompute);

    /**
     * 查询指定租户的月度账单历史
     *
     * @param tenantId 租户编号
     * @return 账单列表
     */
    List<IdcBillingStatement> listStatementsByTenant(String tenantId);

    /**
     * 管理员多维分页/按月份查询账单明细
     *
     * @param billingMonth 结算月份 (如 2026-08)
     * @return 账单列表
     */
    List<IdcBillingStatement> listStatementsByMonth(String billingMonth);

    /**
     * 计算单回路电表增量读数 (带电表 10 万度物理翻转与清零补偿)
     *
     * @param curKwh  当前采样时点读数 (kWh)
     * @param lastKwh 上期基准时点读数 (kWh)
     * @return 有效电量增量 (kWh, 保留 4 位小数)
     */
    BigDecimal calculateMeterDelta(BigDecimal curKwh, BigDecimal lastKwh);

    /**
     * 物理拓扑电表核算: A+B 双路 (2N PDU) 冗余回路累加与翻转补偿
     *
     * @param curA  A 路当前读数
     * @param lastA A 路基准读数
     * @param curB  B 路当前读数 (单路机柜传 null 或 0)
     * @param lastB B 路基准读数 (单路机柜传 null 或 0)
     * @return 机柜实际耗电总量 (kWh)
     */
    BigDecimal calculateRackPower(BigDecimal curA, BigDecimal lastA, BigDecimal curB, BigDecimal lastB);

    /**
     * PUE 能效安全核算: 纳入公共自用 IT 负载校正与极值截断
     *
     * @param totalFacilityKwh 机房进线总能耗 (kWh)
     * @param tenantItKwh      租户 IT 负载能耗 (kWh)
     * @param selfItKwh        机房自用及待机 IT 负载能耗 (kWh)
     * @return 核算 PUE 因子 (保留 2 位小数)
     */
    BigDecimal calculatePueFactor(BigDecimal totalFacilityKwh, BigDecimal tenantItKwh, BigDecimal selfItKwh);

    /**
     * PUE 能效计算 (总耗电 / 总 IT 耗电，带除零保护与物理截断)
     *
     * @param totalFacilityKwh 机房进线总能耗 (kWh)
     * @param totalItKwh       机房全部 IT 设备总能耗 (kWh)
     * @return 核算 PUE 因子 (保留 2 位小数)
     */
    BigDecimal calculatePueFactor(BigDecimal totalFacilityKwh, BigDecimal totalItKwh);

    /**
     * 热力学边界截断 (Clamping: 1.00 ~ 1.45)
     *
     * @param rawPue 未截断原始 PUE
     * @return 截断后结算 PUE (保留 2 位小数)
     */
    BigDecimal clampPue(BigDecimal rawPue);

    /**
     * 租期未满整月的非自然月日折算租金 (默认 3500.00 元/月)
     *
     * @param activeDays  当月实际在租天数
     * @param daysInMonth 当月总天数 (28~31)
     * @return 折算固定租金 (元, 保留 2 位小数)
     */
    BigDecimal calculateProratedRent(int activeDays, int daysInMonth);

    /**
     * 租期未满整月的非自然月日折算租金 (支持指定月租基准)
     *
     * @param activeDays      当月实际在租天数
     * @param daysInMonth     当月总天数 (28~31)
     * @param monthlyBaseRent 月度基准租金
     * @return 折算固定租金 (元, 保留 2 位小数)
     */
    BigDecimal calculateProratedRent(int activeDays, int daysInMonth, BigDecimal monthlyBaseRent);

    /**
     * 单机柜独立定额 (PER_RACK) 阶梯电费核算
     * <= 3000 kWh: 0.85 元/kWh
     * > 3000 kWh: 超额部分 1.10 元/kWh
     *
     * @param rackKwh 单机柜当月用电量 (kWh)
     * @return 阶梯电费金额 (元, 保留 2 位小数)
     */
    BigDecimal calculateTieredPowerFee(BigDecimal rackKwh);

    /**
     * PUE 制冷与动环公摊能耗费核算: Fee_power * (PUE - 1.00)
     *
     * @param powerFee  设备阶梯电费总额 (元)
     * @param pueFactor 核算结算 PUE 因子
     * @return PUE 公摊能耗费 (元, 保留 2 位小数)
     */
    BigDecimal calculatePueShareFee(BigDecimal powerFee, BigDecimal pueFactor);
}
