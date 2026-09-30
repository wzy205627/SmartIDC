package com.smartidc.biz.billing;

import com.smartidc.biz.domain.entity.IdcBillingStatement;
import com.smartidc.biz.domain.entity.IdcRack;
import com.smartidc.biz.domain.entity.IdcTelemetrySnapshot;
import com.smartidc.biz.mapper.IdcBillingStatementMapper;
import com.smartidc.biz.mapper.IdcDeviceMapper;
import com.smartidc.biz.mapper.IdcRackMapper;
import com.smartidc.biz.mapper.IdcTelemetrySnapshotMapper;
import com.smartidc.biz.service.IdcBillingService;
import com.smartidc.biz.service.impl.IdcBillingServiceImpl;
import com.smartidc.common.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 智维云 (SmartIDC) Phase 5.4 工业级 PUE 能效计算与月度租赁电费结算引擎测试
 * 对标 implementation_plan5.4.md 第四节验收清单 (V1, V2, V3, V4)
 */
public class Phase5BillingSettlementTest {

    private IdcBillingStatementMapper idcBillingStatementMapper;
    private IdcRackMapper idcRackMapper;
    private IdcDeviceMapper idcDeviceMapper;
    private IdcTelemetrySnapshotMapper idcTelemetrySnapshotMapper;
    private RedissonClient redissonClient;

    private IdcBillingServiceImpl idcBillingService;

    // 内存模拟数据库表
    private final Map<Long, IdcBillingStatement> mockBillingTable = new ConcurrentHashMap<>();
    private final AtomicLong billIdGen = new AtomicLong(1000);
    private final List<IdcRack> mockRackTable = new CopyOnWriteArrayList<>();
    private final Map<Long, IdcTelemetrySnapshot> mockTelemetryTable = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        mockBillingTable.clear();
        mockRackTable.clear();
        mockTelemetryTable.clear();

        idcBillingStatementMapper = mock(IdcBillingStatementMapper.class);
        idcRackMapper = mock(IdcRackMapper.class);
        idcDeviceMapper = mock(IdcDeviceMapper.class);
        idcTelemetrySnapshotMapper = mock(IdcTelemetrySnapshotMapper.class);
        redissonClient = mock(RedissonClient.class);

        // 模拟 IdcBillingStatementMapper 持久化行为
        when(idcBillingStatementMapper.insert(any(IdcBillingStatement.class))).thenAnswer(invocation -> {
            IdcBillingStatement statement = invocation.getArgument(0);
            if (statement.getBillId() == null) {
                statement.setBillId(billIdGen.incrementAndGet());
            }
            mockBillingTable.put(statement.getBillId(), statement);
            return 1;
        });

        when(idcBillingStatementMapper.updateById(any(IdcBillingStatement.class))).thenAnswer(invocation -> {
            IdcBillingStatement statement = invocation.getArgument(0);
            mockBillingTable.put(statement.getBillId(), statement);
            return 1;
        });

        when(idcBillingStatementMapper.selectByTenantAndMonth(anyString(), anyString())).thenAnswer(invocation -> {
            String tenantId = invocation.getArgument(0);
            String month = invocation.getArgument(1);
            return mockBillingTable.values().stream()
                    .filter(b -> Objects.equals(b.getTenantId(), tenantId) && Objects.equals(b.getBillingMonth(), month))
                    .findFirst()
                    .orElse(null);
        });

        when(idcBillingStatementMapper.selectByBillingMonth(anyString())).thenAnswer(invocation -> {
            String month = invocation.getArgument(0);
            List<IdcBillingStatement> list = new ArrayList<>();
            for (IdcBillingStatement s : mockBillingTable.values()) {
                if (Objects.equals(s.getBillingMonth(), month)) {
                    list.add(s);
                }
            }
            return list;
        });

        when(idcBillingStatementMapper.selectByTenantId(anyString())).thenAnswer(invocation -> {
            String tenantId = invocation.getArgument(0);
            List<IdcBillingStatement> list = new ArrayList<>();
            for (IdcBillingStatement s : mockBillingTable.values()) {
                if (Objects.equals(s.getTenantId(), tenantId)) {
                    list.add(s);
                }
            }
            return list;
        });

        // 模拟机架资产查询
        when(idcRackMapper.selectAllRacksIgnoreTenant()).thenReturn(mockRackTable);

        // 模拟动环快照查询
        when(idcTelemetrySnapshotMapper.selectLatestSnapshotByRackId(any(Long.class))).thenAnswer(invocation -> {
            Long rackId = invocation.getArgument(0);
            return mockTelemetryTable.get(rackId);
        });

        // 默认默认 Redisson 锁模拟 (单线程直通)
        RLock defaultLock = createMockRLock(new ReentrantLock());
        when(redissonClient.getLock(anyString())).thenReturn(defaultLock);

        idcBillingService = new IdcBillingServiceImpl(
                idcBillingStatementMapper,
                idcRackMapper,
                idcDeviceMapper,
                idcTelemetrySnapshotMapper,
                redissonClient
        );
    }

    /**
     * V1 验收维度: A+B 双路电表与翻转补偿测试
     * 模拟 A 路电量 1200kWh、B 路电表物理翻转（99990 ➔ 100，补偿后增量 110kWh），
     * 断言机柜总耗电量为准确的 1310kWh，0 负数异常。
     */
    @Test
    @DisplayName("V1: A+B 双路 2N PDU 拓扑核算与电表物理翻转补偿 (1200 + 110 = 1310 kWh)")
    void testV1_DualFeedAndRolloverCompensation() {
        // 1. 模拟 A 路电表正向累计 1200 kWh (0 -> 1200)
        BigDecimal curA = new BigDecimal("1200.0000");
        BigDecimal lastA = new BigDecimal("0.0000");
        BigDecimal deltaA = idcBillingService.calculateMeterDelta(curA, lastA);
        assertEquals(new BigDecimal("1200.0000"), deltaA, "A路电量增量必须为 1200.0000 kWh");

        // 2. 模拟 B 路电表发生量程溢出翻转: 上期 99990.0000, 当期 100.0000
        BigDecimal curB = new BigDecimal("100.0000");
        BigDecimal lastB = new BigDecimal("99990.0000");
        BigDecimal deltaB = idcBillingService.calculateMeterDelta(curB, lastB);

        // 翻转补偿后增量 = 100 + 100000 - 99990 = 110 kWh
        assertEquals(new BigDecimal("110.0000"), deltaB, "B路发生翻转溢出后必须精确补偿为 110.0000 kWh");
        assertTrue(deltaB.compareTo(BigDecimal.ZERO) > 0, "翻转补偿绝不允许产生负数电量");

        // 3. A+B 双路 2N PDU 聚合核算
        BigDecimal totalRackKwh = idcBillingService.calculateRackPower(curA, lastA, curB, lastB);
        assertEquals(new BigDecimal("1310.0000"), totalRackKwh, "A+B双路聚合电量必须为 1310.0000 kWh");

        // 4. 单路机柜向下兼容性验证 (B 路为 null 或 0)
        BigDecimal singleFeedKwh = idcBillingService.calculateRackPower(curA, lastA, null, null);
        assertEquals(new BigDecimal("1200.0000"), singleFeedKwh, "单路供电机柜向下兼容时电量必须等于A路 1200.0000 kWh");
    }

    /**
     * V2 验收维度: PUE 热力学边界与自用 IT 分母校正
     * 模拟机房总能耗 24000kWh，租户 IT 16000kWh，公共自用 IT 3200kWh；断言核算 PUE 为 1.25；
     * 当总能耗异常导致 PUE < 1.0 时自动 clamp 截断至 1.00，当超过上限时截断至 1.45。
     */
    @Test
    @DisplayName("V2: PUE 热力学边界与自用 IT 分母校正 (24000/(16000+3200)=1.25, clamp 1.00~1.45)")
    void testV2_PueThermodynamicBoundsAndSelfItCorrection() {
        // 1. 标准场景: 机房总能耗 24000 kWh, 租户 IT 16000 kWh, 自用/空置 IT 3200 kWh
        BigDecimal totalFacilityKwh = new BigDecimal("24000.0000");
        BigDecimal tenantItKwh = new BigDecimal("16000.0000");
        BigDecimal selfItKwh = new BigDecimal("3200.0000");

        // 分母校正: 16000 + 3200 = 19200 kWh, 24000 / 19200 = 1.25
        BigDecimal standardPue = idcBillingService.calculatePueFactor(totalFacilityKwh, tenantItKwh, selfItKwh);
        assertEquals(new BigDecimal("1.25"), standardPue, "纳入自用IT分母后PUE必须为 1.25");

        // 2. 违反热力学第二定律下限测试: 异常输入导致输入总能耗小于 IT 功耗 (例如 15000 / 19200 = 0.7813)
        BigDecimal abnormalLowFacility = new BigDecimal("15000.0000");
        BigDecimal clampedLowPue = idcBillingService.calculatePueFactor(abnormalLowFacility, tenantItKwh, selfItKwh);
        assertEquals(new BigDecimal("1.00"), clampedLowPue, "PUE 低于 1.00 时必须被热力学下限硬截断至 1.00");

        // 3. 超过合同约定的封顶上限测试: 异常高温能耗导致 PUE 飙升 (例如 35000 / 19200 = 1.8229)
        BigDecimal abnormalHighFacility = new BigDecimal("35000.0000");
        BigDecimal clampedHighPue = idcBillingService.calculatePueFactor(abnormalHighFacility, tenantItKwh, selfItKwh);
        assertEquals(new BigDecimal("1.45"), clampedHighPue, "PUE 超过合同上限时必须被硬截断至 1.45 (超出部分机房自负)");

        // 4. 除零保护测试: 新机房开局无任何 IT 负载 (分母为 0)
        BigDecimal zeroItPue = idcBillingService.calculatePueFactor(new BigDecimal("5000.0000"), BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(new BigDecimal("1.25"), zeroItPue, "IT负载为0时必须安全触发除零保护，锁定基准默认值 1.25");
    }

    /**
     * V3 验收维度: 租金日折算与阶梯电费高精度核算
     * 模拟月中 16 号起租（9 月 30 天，在租 15 天），断言固定租金为准确的 1750.00 元；
     * 用电量 3500kWh，首档 3000kWh×0.85 + 超额 500kWh×1.10 = 3100.00 元，全链路精确到分。
     */
    @Test
    @DisplayName("V3: 租金非自然月按天日折算 (1750.00元) 与 PER_RACK 阶梯电费 (3100.00元)")
    void testV3_ProratedRentAndTieredPowerTariff() {
        // 1. 租期未满整月日折算: 9 月 (共 30 天), 16 号起租 (在租 15 天: 16~30 号)
        int daysInMonth = 30;
        int activeDays = 15;
        BigDecimal proratedRent = idcBillingService.calculateProratedRent(activeDays, daysInMonth);

        // 3500.00 * 15 / 30 = 1750.00 元
        assertEquals(new BigDecimal("1750.00"), proratedRent, "非自然月 15/30 天折算租金必须为准确的 1750.00 元");

        // 整月在租验证 (30/30 天)
        BigDecimal fullMonthRent = idcBillingService.calculateProratedRent(30, 30);
        assertEquals(new BigDecimal("3500.00"), fullMonthRent, "满月租金必须为全额 3500.00 元");

        // 2. 单机柜独立定额 (PER_RACK) 阶梯电费核算: 3500 kWh
        // 首档基准: 3000 kWh * 0.85 = 2550.00 元
        // 超额二档: (3500 - 3000) = 500 kWh * 1.10 = 550.00 元
        // 总电费: 2550.00 + 550.00 = 3100.00 元
        BigDecimal rackKwh = new BigDecimal("3500.0000");
        BigDecimal powerFee = idcBillingService.calculateTieredPowerFee(rackKwh);
        assertEquals(new BigDecimal("3100.00"), powerFee, "3500度阶梯电费必须为准确的 3100.00 元");

        // 首档以内阶梯测试 (2000 kWh * 0.85 = 1700.00 元)
        BigDecimal tier1OnlyFee = idcBillingService.calculateTieredPowerFee(new BigDecimal("2000.0000"));
        assertEquals(new BigDecimal("1700.00"), tier1OnlyFee, "首档以内电费必须为 1700.00 元");

        // 3. PUE 制冷公摊能耗费核算: PUE = 1.25, 公摊增量 = (1.25 - 1.00) = 0.25
        // pueShareFee = powerFee * 0.25 = 3100.00 * 0.25 = 775.00 元
        BigDecimal pueFactor = new BigDecimal("1.25");
        BigDecimal pueShareFee = idcBillingService.calculatePueShareFee(powerFee, pueFactor);
        assertEquals(new BigDecimal("775.00"), pueShareFee, "PUE 制冷公摊费必须为 775.00 元");

        // 4. 汇总总账单验证
        BigDecimal totalAmount = proratedRent.add(powerFee).add(pueShareFee);
        // 1750.00 + 3100.00 + 775.00 = 5625.00 元
        assertEquals(new BigDecimal("5625.00"), totalAmount, "账单总金额必须精确匹配 5625.00 元");
    }

    /**
     * V4 验收维度: Redisson 分布式锁防多节点并发测试
     * 模拟并发双线程同时调用结算，断言只有一个线程成功抢占执行，另一个线程安全跳过；
     * 生成账单精准入库 idc_billing_statement，0 重复记录。
     */
    @Test
    @DisplayName("V4: Redisson 分布式锁防多 Pod 并发踩踏与账单精准幂等入库")
    void testV4_RedissonDistributedLockConcurrencyAndPersistence() throws Exception {
        String testMonth = "2026-08";

        // 准备机柜资产拓扑数据: 1个平台机柜 + 1个租户机柜 (T10001)
        IdcRack platformRack = new IdcRack();
        platformRack.setRackId(1L);
        platformRack.setTenantId("000000");
        platformRack.setRackCode("A-01");
        platformRack.setPowerRating(new BigDecimal("5.00"));
        platformRack.setStatus(1);
        platformRack.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        mockRackTable.add(platformRack);

        IdcRack tenantRack = new IdcRack();
        tenantRack.setRackId(2L);
        tenantRack.setTenantId("T10001");
        tenantRack.setRackCode("B-01");
        tenantRack.setPowerRating(new BigDecimal("6.00"));
        tenantRack.setStatus(1);
        tenantRack.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        mockRackTable.add(tenantRack);

        // 创建真并发共享的 ReentrantLock 模拟 Redisson 分布式排他锁
        ReentrantLock sharedLock = new ReentrantLock();
        when(redissonClient.getLock(anyString())).thenAnswer(invocation -> createMockRLock(sharedLock));

        // 设立并发线程屏障
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        List<List<IdcBillingStatement>> executionResults = new CopyOnWriteArrayList<>();
        AtomicBoolean thread1Acquired = new AtomicBoolean(false);
        AtomicBoolean thread2Acquired = new AtomicBoolean(false);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // 保持齐步走
                    // 模拟慢速结算执行，拉长临界区窗口
                    List<IdcBillingStatement> statements = idcBillingService.executeMonthlySettlement(testMonth, false);
                    if (!statements.isEmpty()) {
                        if (index == 0) thread1Acquired.set(true);
                        if (index == 1) thread2Acquired.set(true);
                    }
                    executionResults.add(statements);
                } catch (Exception e) {
                    // unexpected
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // 双线程并发开闸
        startLatch.countDown();
        finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 断言: 必须有且仅有 1 个线程成功抢占并执行，另 1 个线程安全跳过 (返回空集合)
        int successCount = 0;
        int skippedCount = 0;
        for (List<IdcBillingStatement> res : executionResults) {
            if (!res.isEmpty()) {
                successCount++;
            } else {
                skippedCount++;
            }
        }

        assertEquals(1, successCount, "并发双线程下必须仅有 1 个线程成功获取分布式锁执行结算");
        assertEquals(1, skippedCount, "另 1 个线程抢锁失败必须安全跳过 (返回空列表)");

        // 验证数据库最终账单入库: 0 重复记录，恰好 1 笔 T10001 的 2026-08 账单
        assertEquals(1, mockBillingTable.size(), "数据库 idc_billing_statement 中必须精准入库 1 笔账单，0 重复记录");
        IdcBillingStatement bill = mockBillingTable.values().iterator().next();
        assertEquals("T10001", bill.getTenantId());
        assertEquals("2026-08", bill.getBillingMonth());
        assertEquals(IdcBillingService.PAYMENT_STATUS_UNPAID, bill.getPaymentStatus());
        assertTrue(bill.getTotalAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    /**
     * 账单防篡改与支付保护测试:
     * 已支付结清 (payment_status = 1) 的账单严禁被冲正或覆盖，强制重算时抛出 ServiceException 阻断
     */
    @Test
    @DisplayName("扩展测试: 账单防篡改与已支付保护 (payment_status = 1 严禁修改冲正)")
    void testPaidBillProtection() {
        String billingMonth = "2026-07";
        String tenantId = "T10001";

        // 先预置一张已支付结清的账单
        IdcBillingStatement paidBill = new IdcBillingStatement(
                888L,
                tenantId,
                billingMonth,
                new BigDecimal("3500.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("1700.00"),
                new BigDecimal("1.25"),
                new BigDecimal("425.00"),
                new BigDecimal("5625.00"),
                IdcBillingService.PAYMENT_STATUS_PAID,
                LocalDateTime.now().minusDays(10)
        );
        mockBillingTable.put(paidBill.getBillId(), paidBill);

        // 加入机架以触发结算逻辑
        IdcRack rack = new IdcRack();
        rack.setRackId(10L);
        rack.setTenantId(tenantId);
        rack.setStatus(1);
        mockRackTable.add(rack);

        // 尝试对已支付账单强制冲正 (forceRecompute = true)，必须触发 ServiceException 阻断
        ServiceException ex = assertThrows(ServiceException.class, () -> {
            idcBillingService.executeMonthlySettlement(billingMonth, true);
        });

        assertTrue(ex.getMessage().contains("已支付结清，禁止修改或冲正"), "必须明确拦截已支付账单篡改");
        // 校验原有金额丝毫不变
        assertEquals(new BigDecimal("5625.00"), mockBillingTable.get(888L).getTotalAmount());
        assertEquals(IdcBillingService.PAYMENT_STATUS_PAID, mockBillingTable.get(888L).getPaymentStatus());
    }

    /**
     * 未支付账单幂等冲正测试:
     * 未支付 (payment_status = 0) 的账单在 forceRecompute=true 时应准确更新最新费用
     */
    @Test
    @DisplayName("扩展测试: 未支付账单支持 forceRecompute 幂等冲正更新")
    void testUnpaidBillForceRecompute() {
        String billingMonth = "2026-06";
        String tenantId = "T10001";

        // 预置旧的未支付账单
        IdcBillingStatement oldBill = new IdcBillingStatement(
                777L,
                tenantId,
                billingMonth,
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("850.00"),
                new BigDecimal("1.25"),
                new BigDecimal("212.50"),
                new BigDecimal("2062.50"),
                IdcBillingService.PAYMENT_STATUS_UNPAID,
                LocalDateTime.now().minusDays(20)
        );
        mockBillingTable.put(oldBill.getBillId(), oldBill);

        IdcRack rack = new IdcRack();
        rack.setRackId(15L);
        rack.setTenantId(tenantId);
        rack.setStatus(1);
        mockRackTable.add(rack);

        // 触发强制冲正
        List<IdcBillingStatement> statements = idcBillingService.executeMonthlySettlement(billingMonth, true);
        assertEquals(1, statements.size());

        IdcBillingStatement updated = mockBillingTable.get(777L);
        assertNotNull(updated);
        // 验证账单主键 ID 保持不变 (原地冲正)，租金按标准月租更新为 3500.00
        assertEquals(777L, updated.getBillId());
        assertEquals(new BigDecimal("3500.00"), updated.getRackRentalFee());
    }

    /**
     * 辅助方法: 基于 Java ReentrantLock 构建支持 tryLock(0, ...) 抢锁行为的 Mock RLock
     */
    private RLock createMockRLock(ReentrantLock realLock) {
        RLock rLock = mock(RLock.class);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenAnswer(new Answer<Boolean>() {
                @Override
                public Boolean answer(InvocationOnMock invocation) throws Throwable {
                    long waitTime = invocation.getArgument(0);
                    TimeUnit unit = invocation.getArgument(2);
                    boolean got = realLock.tryLock(waitTime, unit);
                    if (got) {
                        // 稍微睡眠模拟批处理真实耗时，给并发竞争留出窗口
                        Thread.sleep(60);
                    }
                    return got;
                }
            });

            when(rLock.isHeldByCurrentThread()).thenAnswer(inv -> realLock.isHeldByCurrentThread());

            doAnswer(inv -> {
                if (realLock.isHeldByCurrentThread()) {
                    realLock.unlock();
                }
                return null;
            }).when(rLock).unlock();

        } catch (InterruptedException e) {
            // ignore
        }
        return rLock;
    }
}
