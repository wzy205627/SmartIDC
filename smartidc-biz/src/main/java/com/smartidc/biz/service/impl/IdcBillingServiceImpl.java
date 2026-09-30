package com.smartidc.biz.service.impl;

import com.smartidc.biz.domain.entity.IdcBillingStatement;
import com.smartidc.biz.domain.entity.IdcRack;
import com.smartidc.biz.domain.entity.IdcTelemetrySnapshot;
import com.smartidc.biz.mapper.IdcBillingStatementMapper;
import com.smartidc.biz.mapper.IdcDeviceMapper;
import com.smartidc.biz.mapper.IdcRackMapper;
import com.smartidc.biz.mapper.IdcTelemetrySnapshotMapper;
import com.smartidc.biz.service.IdcBillingService;
import com.smartidc.common.exception.ServiceException;
import com.smartidc.framework.tenant.TenantContext;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * IDC 能源能效 (PUE) 与月度租赁电费结算引擎实现 (工业生产级)
 * 纯 Java POJO 规范 (严禁 Lombok，全链路纯 BigDecimal 高精度金融对账)
 */
@Service
public class IdcBillingServiceImpl implements IdcBillingService {

    private static final Logger log = LoggerFactory.getLogger(IdcBillingServiceImpl.class);

    private final IdcBillingStatementMapper idcBillingStatementMapper;
    private final IdcRackMapper idcRackMapper;
    private final IdcDeviceMapper idcDeviceMapper;
    private final IdcTelemetrySnapshotMapper idcTelemetrySnapshotMapper;
    private final RedissonClient redissonClient;

    public IdcBillingServiceImpl(IdcBillingStatementMapper idcBillingStatementMapper,
                                 IdcRackMapper idcRackMapper,
                                 IdcDeviceMapper idcDeviceMapper,
                                 IdcTelemetrySnapshotMapper idcTelemetrySnapshotMapper,
                                 RedissonClient redissonClient) {
        this.idcBillingStatementMapper = idcBillingStatementMapper;
        this.idcRackMapper = idcRackMapper;
        this.idcDeviceMapper = idcDeviceMapper;
        this.idcTelemetrySnapshotMapper = idcTelemetrySnapshotMapper;
        this.redissonClient = redissonClient;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<IdcBillingStatement> executeMonthlySettlement(String billingMonth, boolean forceRecompute) {
        if (billingMonth == null || !billingMonth.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
            throw new ServiceException("结算月份格式不合法，须为 yyyy-MM 格式，如 2026-08");
        }

        String lockKey = "smartidc:billing:lock:monthly:" + billingMonth;
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;

        try {
            // 抢占分布式排他锁，等待 0 秒，持有最长 30 分钟，抢锁失败立即退出防止多 Pod 并发踩踏
            acquired = lock.tryLock(0, 30, TimeUnit.MINUTES);
            if (!acquired) {
                log.info("[月度计费任务] 其他集群节点正在执行 [{}] 账单结算，本节点安全跳过", billingMonth);
                return Collections.emptyList();
            }

            log.info("[月度计费任务] 成功获取分布式调度锁，开始结算 [{}] 账单, forceRecompute={}", billingMonth, forceRecompute);

            YearMonth ym = YearMonth.parse(billingMonth);
            int daysInMonth = ym.lengthOfMonth();

            // 1. 忽略租户隔离，提取全机房机柜资产拓扑快照
            List<IdcRack> allRacks = idcRackMapper.selectAllRacksIgnoreTenant();
            if (allRacks == null || allRacks.isEmpty()) {
                log.warn("[月度计费任务] 未查询到任何在管机架资产，结算终止: month={}", billingMonth);
                return Collections.emptyList();
            }

            // 2. 区分平台自用机架 (000000) 与外部托管租户机架
            List<IdcRack> platformRacks = new ArrayList<>();
            Map<String, List<IdcRack>> tenantRacksMap = new LinkedHashMap<>();

            for (IdcRack rack : allRacks) {
                String tenantId = rack.getTenantId();
                if (tenantId == null || TenantContext.DEFAULT_TENANT_ID.equals(tenantId)) {
                    platformRacks.add(rack);
                } else {
                    tenantRacksMap.computeIfAbsent(tenantId, k -> new ArrayList<>()).add(rack);
                }
            }

            // 3. 计算平台公共自用 IT 负载 (E_IT_Self) 与空置待机能耗 (E_IT_Idle)
            BigDecimal platformItKwh = BigDecimal.ZERO;
            for (IdcRack rack : platformRacks) {
                BigDecimal rackKwh = resolveRackMonthlyKwh(rack, daysInMonth, daysInMonth);
                platformItKwh = platformItKwh.add(rackKwh);
            }

            // 4. 汇总各外部租户机柜 IT 耗电量
            BigDecimal allTenantItKwh = BigDecimal.ZERO;
            Map<String, List<RackBillItem>> tenantBillItemsMap = new LinkedHashMap<>();

            for (Map.Entry<String, List<IdcRack>> entry : tenantRacksMap.entrySet()) {
                String tenantId = entry.getKey();
                List<IdcRack> racks = entry.getValue();
                List<RackBillItem> items = new ArrayList<>();

                for (IdcRack rack : racks) {
                    int activeDays = resolveActiveDays(rack, ym, daysInMonth);
                    BigDecimal rentFee = calculateProratedRent(activeDays, daysInMonth, BASE_RACK_MONTHLY_RENT);
                    BigDecimal rackKwh = resolveRackMonthlyKwh(rack, activeDays, daysInMonth);
                    BigDecimal powerFee = calculateTieredPowerFee(rackKwh);

                    items.add(new RackBillItem(rack.getRackId(), rack.getRackCode(), rentFee, rackKwh, powerFee));
                    allTenantItKwh = allTenantItKwh.add(rackKwh);
                }
                tenantBillItemsMap.put(tenantId, items);
            }

            // 5. PUE 能效计算: 纳入自用分母与热力学极值截断 (1.00 ~ 1.45)
            // 全机房总进线能耗估算基准 (IT总能耗 * 1.25)
            BigDecimal totalItKwh = allTenantItKwh.add(platformItKwh);
            BigDecimal totalFacilityKwh = totalItKwh.multiply(new BigDecimal("1.25")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal billingPue = calculatePueFactor(totalFacilityKwh, allTenantItKwh, platformItKwh);

            log.info("[月度计费任务] [{}] 能耗全景: 租户IT={}kWh, 自用IT={}kWh, 分母总IT={}kWh, 总进线={}kWh, 结算PUE={}",
                    billingMonth, allTenantItKwh, platformItKwh, totalItKwh, totalFacilityKwh, billingPue);

            // 6. 逐租户生成/更新账单快照
            List<IdcBillingStatement> statementList = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (Map.Entry<String, List<RackBillItem>> entry : tenantBillItemsMap.entrySet()) {
                String tenantId = entry.getKey();
                List<RackBillItem> items = entry.getValue();

                BigDecimal totalRentFee = BigDecimal.ZERO;
                BigDecimal totalKwh = BigDecimal.ZERO;
                BigDecimal totalPowerFee = BigDecimal.ZERO;

                for (RackBillItem item : items) {
                    totalRentFee = totalRentFee.add(item.rentFee);
                    totalKwh = totalKwh.add(item.kwh);
                    totalPowerFee = totalPowerFee.add(item.powerFee);
                }

                totalRentFee = totalRentFee.setScale(2, RoundingMode.HALF_UP);
                totalKwh = totalKwh.setScale(2, RoundingMode.HALF_UP);
                totalPowerFee = totalPowerFee.setScale(2, RoundingMode.HALF_UP);

                BigDecimal pueShareFee = calculatePueShareFee(totalPowerFee, billingPue);
                BigDecimal totalAmount = totalRentFee.add(totalPowerFee).add(pueShareFee).setScale(2, RoundingMode.HALF_UP);

                // 账单幂等冲正与支付保护检查
                IdcBillingStatement existing = idcBillingStatementMapper.selectByTenantAndMonth(tenantId, billingMonth);
                if (existing != null) {
                    if (existing.getPaymentStatus() != null && existing.getPaymentStatus() == PAYMENT_STATUS_PAID) {
                        log.warn("[月度计费任务] 租户 [{}] 该月账单已支付结清，拒绝覆盖冲正: billId={}", tenantId, existing.getBillId());
                        if (forceRecompute) {
                            throw new ServiceException("租户 [" + tenantId + "] " + billingMonth + " 账单已支付结清，禁止修改或冲正");
                        }
                        statementList.add(existing);
                        continue;
                    }

                    if (forceRecompute) {
                        log.info("[月度计费任务] 触发强制重算，冲正更新账单: billId={}, tenantId={}", existing.getBillId(), tenantId);
                        existing.setRackRentalFee(totalRentFee);
                        existing.setPowerKwh(totalKwh);
                        existing.setPowerFee(totalPowerFee);
                        existing.setPueFactor(billingPue);
                        existing.setPueShareFee(pueShareFee);
                        existing.setTotalAmount(totalAmount);
                        existing.setCreateTime(now);
                        idcBillingStatementMapper.updateById(existing);
                        statementList.add(existing);
                    } else {
                        log.info("[月度计费任务] 账单已存在且未指定 forceRecompute，跳过更新: billId={}", existing.getBillId());
                        statementList.add(existing);
                    }
                } else {
                    IdcBillingStatement newStatement = new IdcBillingStatement(
                            null,
                            tenantId,
                            billingMonth,
                            totalRentFee,
                            totalKwh,
                            totalPowerFee,
                            billingPue,
                            pueShareFee,
                            totalAmount,
                            PAYMENT_STATUS_UNPAID,
                            now
                    );
                    idcBillingStatementMapper.insert(newStatement);
                    log.info("[月度计费任务] 新增生成账单成功: tenantId={}, month={}, totalAmount={}",
                            tenantId, billingMonth, totalAmount);
                    statementList.add(newStatement);
                }
            }

            log.info("[月度计费任务] [{}] 账单结算圆满完成，共处理 {} 户账单", billingMonth, statementList.size());
            return statementList;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[月度计费任务] 调度加锁被中断", e);
            throw new ServiceException("月度计费调度任务被异常中断");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public List<IdcBillingStatement> listStatementsByTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = TenantContext.getTenantId();
        }
        return idcBillingStatementMapper.selectByTenantId(tenantId);
    }

    @Override
    public List<IdcBillingStatement> listStatementsByMonth(String billingMonth) {
        if (billingMonth == null || billingMonth.isBlank()) {
            return idcBillingStatementMapper.selectAllStatementsIgnoreTenant();
        }
        return idcBillingStatementMapper.selectByBillingMonth(billingMonth);
    }

    @Override
    public BigDecimal calculateMeterDelta(BigDecimal curKwh, BigDecimal lastKwh) {
        if (curKwh == null || lastKwh == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal delta = curKwh.subtract(lastKwh);
        if (delta.compareTo(BigDecimal.ZERO) < 0) {
            // 触发硬件智能电表物理翻转溢出补偿 (10 万度量程)
            delta = curKwh.add(METER_MAX_CAPACITY).subtract(lastKwh);
            log.warn("[电表翻转补偿] 检测到读数回退，已执行物理翻转补偿: cur={}, last={}, delta={}", curKwh, lastKwh, delta);
        }
        return delta.setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateRackPower(BigDecimal curA, BigDecimal lastA, BigDecimal curB, BigDecimal lastB) {
        BigDecimal deltaA = calculateMeterDelta(curA, lastA);
        BigDecimal deltaB = calculateMeterDelta(curB, lastB);
        return deltaA.add(deltaB).setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculatePueFactor(BigDecimal totalFacilityKwh, BigDecimal tenantItKwh, BigDecimal selfItKwh) {
        BigDecimal safeTenantIt = tenantItKwh != null ? tenantItKwh : BigDecimal.ZERO;
        BigDecimal safeSelfIt = selfItKwh != null ? selfItKwh : BigDecimal.ZERO;
        BigDecimal totalItKwh = safeTenantIt.add(safeSelfIt);
        return calculatePueFactor(totalFacilityKwh, totalItKwh);
    }

    @Override
    public BigDecimal calculatePueFactor(BigDecimal totalFacilityKwh, BigDecimal totalItKwh) {
        // 除零保护: 当机房全部 IT 负载为 0 时直接返回基准默认值 1.25
        if (totalItKwh == null || totalItKwh.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[PUE核算] IT 总用电分母为 0，启用除零保护默认基准: PUE={}", PUE_DEFAULT_BASE);
            return PUE_DEFAULT_BASE.setScale(2, RoundingMode.HALF_UP);
        }

        if (totalFacilityKwh == null || totalFacilityKwh.compareTo(BigDecimal.ZERO) <= 0) {
            return PUE_MIN_LIMIT.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal rawPue = totalFacilityKwh.divide(totalItKwh, 4, RoundingMode.HALF_UP);
        return clampPue(rawPue);
    }

    @Override
    public BigDecimal clampPue(BigDecimal rawPue) {
        if (rawPue == null) {
            return PUE_DEFAULT_BASE.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal clamped = rawPue;
        // 热力学第二定律防线: PUE 不得小于 1.00
        if (clamped.compareTo(PUE_MIN_LIMIT) < 0) {
            log.warn("[PUE截断] 原始PUE={}低于热力学物理下限，已执行下限截断至 {}", rawPue, PUE_MIN_LIMIT);
            clamped = PUE_MIN_LIMIT;
        } else if (clamped.compareTo(PUE_MAX_CONTRACT_CAP) > 0) {
            // 合同封顶防线: PUE 超过上限 1.45 部分由 IDC 自行兜底
            log.warn("[PUE截断] 原始PUE={}超过合同封顶上限，已执行上限截断至 {}", rawPue, PUE_MAX_CONTRACT_CAP);
            clamped = PUE_MAX_CONTRACT_CAP;
        }
        return clamped.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateProratedRent(int activeDays, int daysInMonth) {
        return calculateProratedRent(activeDays, daysInMonth, BASE_RACK_MONTHLY_RENT);
    }

    @Override
    public BigDecimal calculateProratedRent(int activeDays, int daysInMonth, BigDecimal monthlyBaseRent) {
        if (monthlyBaseRent == null) {
            monthlyBaseRent = BASE_RACK_MONTHLY_RENT;
        }
        if (daysInMonth <= 0) {
            daysInMonth = 30;
        }
        if (activeDays <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (activeDays >= daysInMonth) {
            return monthlyBaseRent.setScale(2, RoundingMode.HALF_UP);
        }
        return monthlyBaseRent.multiply(BigDecimal.valueOf(activeDays))
                .divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateTieredPowerFee(BigDecimal rackKwh) {
        if (rackKwh == null || rackKwh.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        // 单柜独立核算 (PER_RACK)
        if (rackKwh.compareTo(POWER_TIER1_CAP_KWH) <= 0) {
            // 首档基准: <= 3000 kWh * 0.85
            return rackKwh.multiply(POWER_TIER1_RATE).setScale(2, RoundingMode.HALF_UP);
        }

        // 超额二档: 3000 * 0.85 + (Kwh - 3000) * 1.10
        BigDecimal tier1Fee = POWER_TIER1_CAP_KWH.multiply(POWER_TIER1_RATE);
        BigDecimal tier2Kwh = rackKwh.subtract(POWER_TIER1_CAP_KWH);
        BigDecimal tier2Fee = tier2Kwh.multiply(POWER_TIER2_RATE);

        return tier1Fee.add(tier2Fee).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculatePueShareFee(BigDecimal powerFee, BigDecimal pueFactor) {
        if (powerFee == null || powerFee.compareTo(BigDecimal.ZERO) <= 0 || pueFactor == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal pueDelta = pueFactor.subtract(BigDecimal.ONE);
        if (pueDelta.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return powerFee.multiply(pueDelta).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 求解机柜在结算月内的实际有效在租天数
     */
    private int resolveActiveDays(IdcRack rack, YearMonth ym, int daysInMonth) {
        if (rack.getStatus() != null && rack.getStatus() == 0) {
            return 0; // 空闲机架不计租金
        }
        LocalDateTime createTime = rack.getCreateTime();
        if (createTime == null) {
            return daysInMonth;
        }
        YearMonth createYm = YearMonth.from(createTime);
        if (createYm.equals(ym)) {
            // 当月中旬起租
            int startDay = createTime.getDayOfMonth();
            return Math.max(1, daysInMonth - startDay + 1);
        } else if (createYm.isAfter(ym)) {
            return 0; // 结算月之后才创建
        } else {
            return daysInMonth; // 结算月之前已在租
        }
    }

    /**
     * 物理拓扑与时序遥测聚合: 求解单机柜月度耗电量 (kWh)
     * 包含缺失兜底插值 (保底 50% 额定功率估算)
     */
    private BigDecimal resolveRackMonthlyKwh(IdcRack rack, int activeDays, int daysInMonth) {
        if (activeDays <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }

        // 尝试从最新动环快照中提取机架功率
        IdcTelemetrySnapshot snapshot = idcTelemetrySnapshotMapper.selectLatestSnapshotByRackId(rack.getRackId());
        if (snapshot != null && snapshot.getPowerKw() != null && snapshot.getPowerKw().compareTo(BigDecimal.ZERO) > 0) {
            // 实时功耗 kW * 24h * 实际天数
            return snapshot.getPowerKw()
                    .multiply(BigDecimal.valueOf(24))
                    .multiply(BigDecimal.valueOf(activeDays))
                    .setScale(4, RoundingMode.HALF_UP);
        }

        // 兜底算法: 若无时序上报，根据额定容量 powerRating (默认 5kW) 与 50% 开机负荷率插补
        BigDecimal ratedPower = rack.getPowerRating() != null ? rack.getPowerRating() : new BigDecimal("5.00");
        BigDecimal estimatedLoadFactor = new BigDecimal("0.50"); // 50% 平均负荷率
        return ratedPower.multiply(estimatedLoadFactor)
                .multiply(BigDecimal.valueOf(24))
                .multiply(BigDecimal.valueOf(activeDays))
                .setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 机柜账单计算明细项中间结构
     */
    private static class RackBillItem {
        final Long rackId;
        final String rackCode;
        final BigDecimal rentFee;
        final BigDecimal kwh;
        final BigDecimal powerFee;

        RackBillItem(Long rackId, String rackCode, BigDecimal rentFee, BigDecimal kwh, BigDecimal powerFee) {
            this.rackId = rackId;
            this.rackCode = rackCode;
            this.rentFee = rentFee;
            this.kwh = kwh;
            this.powerFee = powerFee;
        }
    }
}
