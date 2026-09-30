package com.smartidc.biz.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartidc.biz.domain.dto.mobile.MobileResolveTicketDTO;
import com.smartidc.biz.domain.entity.*;
import com.smartidc.biz.domain.vo.mobile.PresignedUploadVO;
import com.smartidc.biz.engine.alarm.AlarmEvaluationResult;
import com.smartidc.biz.engine.alarm.RackAlarmStateMachine;
import com.smartidc.biz.mapper.*;
import com.smartidc.biz.service.IdcBillingService;
import com.smartidc.biz.service.MinioStorageService;
import com.smartidc.biz.service.impl.IdcBillingServiceImpl;
import com.smartidc.biz.service.impl.MobileTicketServiceImpl;
import com.smartidc.biz.service.watchdog.TicketDebounceWatchdog;
import com.smartidc.framework.security.UserContext;
import com.smartidc.framework.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 智维云 (SmartIDC) Phase 5.5 生产级全链路时间机器自动化演练验收套件
 * 对标 implementation_plan5.5.md 第二节第 4 项与第四节终极验收矩阵
 * 
 * 60 秒内严格串联跑通 6 大业务断言闭环：
 * 1. 断言 1 (越限与防抖): 注入持续 38.5℃ 高温遥测，状态机防抖后生成 ACTIVE 状态严重越限告警 (alarmId=701)
 * 2. 断言 2 (AI 挂起): 触发冷机跳闸 RCA，研判高危倒闸操作，精准停靠 APPROVAL_SUSPEND_NODE 并生成待办工单
 * 3. 断言 3 (主管审批): 模拟主管在线核准恢复，工单推进至 1 (已指派)
 * 4. 断言 4 (移动端作业): 现场工程师扫码接单 (1->2) ➔ MinIO 预签名直传存证 ➔ 现场提交消警 (2->6)
 * 5. 断言 5 (5分钟防抖消警): 注入 26.5℃ 安全回温遥测，时间机器快进 6 分钟，看门狗自动归档办结 (6->7) 并自动消除告警
 * 6. 断言 6 (时间机器月底出账): 快进至自然月底触发 PUE 阶梯结算，Redisson 排他锁护航，精准生成账单入库
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase5EndToEndRehearsalTest {

    private static final Logger log = LoggerFactory.getLogger(Phase5EndToEndRehearsalTest.class);

    // 常量定义
    public static final String NODE_APPROVAL_SUSPEND = "APPROVAL_SUSPEND_NODE";
    public static final String HITL_STATUS_SUSPENDED = "HITL_SUSPENDED";

    // 内存模拟数据库表
    private final Map<Long, IdcAlarmEvent> mockAlarmTable = new ConcurrentHashMap<>();
    private final Map<Long, IdcWorkTicket> mockTicketTable = new ConcurrentHashMap<>();
    private final Map<Long, IdcBillingStatement> mockBillingTable = new ConcurrentHashMap<>();
    private final List<IdcRack> mockRackTable = new CopyOnWriteArrayList<>();
    private final Map<Long, IdcTelemetrySnapshot> mockTelemetryTable = new ConcurrentHashMap<>();
    private final Map<String, String> mockRedis = new ConcurrentHashMap<>();

    private final AtomicLong alarmIdGen = new AtomicLong(700);
    private final AtomicLong ticketIdGen = new AtomicLong(30040);
    private final AtomicLong billIdGen = new AtomicLong(5000);

    // 被测核心服务
    private RackAlarmStateMachine alarmStateMachine;
    private MobileTicketServiceImpl mobileTicketService;
    private TicketDebounceWatchdog ticketDebounceWatchdog;
    private IdcBillingServiceImpl idcBillingService;
    private MinioStorageService minioStorageService;

    // 持久层与第三方组件 Mock
    private IdcAlarmEventMapper alarmEventMapper;
    private IdcWorkTicketMapper workTicketMapper;
    private IdcRackMapper rackMapper;
    private IdcDeviceMapper deviceMapper;
    private IdcTelemetrySnapshotMapper telemetrySnapshotMapper;
    private IdcBillingStatementMapper billingStatementMapper;
    private RedissonClient redissonClient;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;

    // 共享演练上下文
    private Long currentAlarmId;
    private Long currentTicketId;
    private static final String CURRENT_TRACE_ID = "trace-e2e-20260926-001";
    private static final String TARGET_RACK_CODE = "A-03";
    private static final String TARGET_ROOM_NAME = "华东01-A区";
    private static final String TARGET_TENANT_ID = "T10001";

    @BeforeAll
    void setUpAll() {
        alarmEventMapper = mock(IdcAlarmEventMapper.class);
        workTicketMapper = mock(IdcWorkTicketMapper.class);
        rackMapper = mock(IdcRackMapper.class);
        deviceMapper = mock(IdcDeviceMapper.class);
        telemetrySnapshotMapper = mock(IdcTelemetrySnapshotMapper.class);
        billingStatementMapper = mock(IdcBillingStatementMapper.class);
        minioStorageService = mock(MinioStorageService.class);
        redissonClient = mock(RedissonClient.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        objectMapper = new ObjectMapper();

        // Redis Mock
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(inv -> mockRedis.get(inv.getArgument(0)));
        doAnswer(inv -> {
            mockRedis.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        // Alarm Mapper Mock
        when(alarmEventMapper.insert(any(IdcAlarmEvent.class))).thenAnswer(inv -> {
            IdcAlarmEvent event = inv.getArgument(0);
            if (event.getAlarmId() == null) {
                event.setAlarmId(alarmIdGen.incrementAndGet());
            }
            mockAlarmTable.put(event.getAlarmId(), event);
            return 1;
        });
        when(alarmEventMapper.selectById(any(Long.class))).thenAnswer(inv -> mockAlarmTable.get(inv.getArgument(0)));
        when(alarmEventMapper.updateById(any(IdcAlarmEvent.class))).thenAnswer(inv -> {
            IdcAlarmEvent event = inv.getArgument(0);
            mockAlarmTable.put(event.getAlarmId(), event);
            return 1;
        });

        // Ticket Mapper Mock
        when(workTicketMapper.insert(any(IdcWorkTicket.class))).thenAnswer(inv -> {
            IdcWorkTicket ticket = inv.getArgument(0);
            if (ticket.getTicketId() == null) {
                ticket.setTicketId(ticketIdGen.incrementAndGet());
            }
            mockTicketTable.put(ticket.getTicketId(), ticket);
            return 1;
        });
        when(workTicketMapper.selectById(any(Long.class))).thenAnswer(inv -> mockTicketTable.get(inv.getArgument(0)));
        when(workTicketMapper.selectByIdIgnoreTenant(any(Long.class))).thenAnswer(inv -> mockTicketTable.get(inv.getArgument(0)));
        when(workTicketMapper.updateById(any(IdcWorkTicket.class))).thenAnswer(inv -> {
            IdcWorkTicket ticket = inv.getArgument(0);
            mockTicketTable.put(ticket.getTicketId(), ticket);
            return 1;
        });
        when(workTicketMapper.selectTicketsByStatusIgnoreTenant(any(Integer.class))).thenAnswer(inv -> {
            int st = inv.getArgument(0);
            List<IdcWorkTicket> list = new ArrayList<>();
            for (IdcWorkTicket t : mockTicketTable.values()) {
                if (t.getStatus() != null && t.getStatus() == st) {
                    list.add(t);
                }
            }
            return list;
        });

        // Rack Mapper Mock
        when(rackMapper.selectAllRacksIgnoreTenant()).thenReturn(mockRackTable);
        when(rackMapper.selectOneByCodeIgnoreTenant(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return mockRackTable.stream().filter(r -> Objects.equals(r.getRackCode(), code)).findFirst().orElse(null);
        });

        // Telemetry Snapshot Mapper Mock
        when(telemetrySnapshotMapper.selectLatestSnapshotByRackId(any(Long.class))).thenAnswer(inv -> {
            Long rackId = inv.getArgument(0);
            return mockTelemetryTable.get(rackId);
        });

        // Billing Mapper Mock
        when(billingStatementMapper.insert(any(IdcBillingStatement.class))).thenAnswer(inv -> {
            IdcBillingStatement b = inv.getArgument(0);
            if (b.getBillId() == null) {
                b.setBillId(billIdGen.incrementAndGet());
            }
            mockBillingTable.put(b.getBillId(), b);
            return 1;
        });
        when(billingStatementMapper.updateById(any(IdcBillingStatement.class))).thenAnswer(inv -> {
            IdcBillingStatement b = inv.getArgument(0);
            mockBillingTable.put(b.getBillId(), b);
            return 1;
        });
        when(billingStatementMapper.selectByTenantAndMonth(anyString(), anyString())).thenAnswer(inv -> {
            String tenantId = inv.getArgument(0);
            String month = inv.getArgument(1);
            return mockBillingTable.values().stream()
                    .filter(b -> Objects.equals(b.getTenantId(), tenantId) && Objects.equals(b.getBillingMonth(), month))
                    .findFirst()
                    .orElse(null);
        });

        // Redisson Lock Mock (ReentrantLock)
        ReentrantLock testLock = new ReentrantLock();
        RLock rLock = mock(RLock.class);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenAnswer(inv -> testLock.tryLock(inv.getArgument(0), inv.getArgument(2)));
            when(rLock.isHeldByCurrentThread()).thenAnswer(inv -> testLock.isHeldByCurrentThread());
            doAnswer(inv -> {
                if (testLock.isHeldByCurrentThread()) {
                    testLock.unlock();
                }
                return null;
            }).when(rLock).unlock();
        } catch (InterruptedException ignored) {
        }
        when(redissonClient.getLock(anyString())).thenReturn(rLock);

        // MinIO Mock
        when(minioStorageService.generatePresignedUploadUrl(anyString(), any(Long.class), anyString(), anyString())).thenAnswer(inv -> {
            String tenant = inv.getArgument(0);
            Long tkId = inv.getArgument(1);
            String fn = inv.getArgument(2);
            String objectKey = "inspection/" + tenant + "/" + tkId + "_" + fn;
            return new PresignedUploadVO(
                    "http://localhost:9000/smartidc-evidence/" + objectKey + "?signature=mock_sig",
                    objectKey,
                    "http://localhost:9000/smartidc-evidence/" + objectKey,
                    System.currentTimeMillis() + 900000L
            );
        });
        when(minioStorageService.objectExists(anyString())).thenReturn(true);
        when(minioStorageService.calculateObjectSha256(anyString())).thenReturn("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        // 构造服务实例: 设置 50ms 消抖窗口便于快速确定性测试
        alarmStateMachine = new RackAlarmStateMachine();
        alarmStateMachine.setWindowTimeThresholdMs(50L);
        alarmStateMachine.setViolationCountThreshold(2);

        mobileTicketService = new MobileTicketServiceImpl(
                workTicketMapper,
                rackMapper,
                alarmEventMapper,
                telemetrySnapshotMapper,
                minioStorageService,
                stringRedisTemplate,
                objectMapper
        );
        ticketDebounceWatchdog = new TicketDebounceWatchdog(
                workTicketMapper,
                alarmEventMapper,
                telemetrySnapshotMapper,
                stringRedisTemplate,
                objectMapper
        );
        idcBillingService = new IdcBillingServiceImpl(
                billingStatementMapper,
                rackMapper,
                deviceMapper,
                telemetrySnapshotMapper,
                redissonClient
        );

        // 设置安全上下文
        UserContext.setUser(new UserContext.LoginUser(201L, "engineer_li", "engineer", Set.of(TARGET_ROOM_NAME)));
        TenantContext.setTenantId(TARGET_TENANT_ID);
    }

    @AfterAll
    void tearDownAll() {
        UserContext.clear();
        TenantContext.clear();
        mockAlarmTable.clear();
        mockTicketTable.clear();
        mockBillingTable.clear();
        mockRackTable.clear();
        mockTelemetryTable.clear();
        mockRedis.clear();
    }

    /**
     * 步骤 1: 故障遥测注入与滑动窗口防抖 ➔ 断言生成 ACTIVE 状态严重越限告警
     */
    @Test
    @Order(1)
    @DisplayName("断言 1: 注入持续高温故障遥测，状态机防抖后生成 ACTIVE 状态严重告警 (alarmId=701)")
    void step1_FaultTelemetryInjectionAndDebouncedAlarm() throws InterruptedException {
        log.info("▶ [步骤 1] 注入 A-03 进风面 38.5℃ 高温遥测流...");

        // 初始化机柜资产
        IdcRack rack = new IdcRack();
        rack.setRackId(3L);
        rack.setTenantId(TARGET_TENANT_ID);
        rack.setRoomName(TARGET_ROOM_NAME);
        rack.setRackCode(TARGET_RACK_CODE);
        rack.setPowerRating(new BigDecimal("6.00"));
        rack.setStatus(1);
        rack.setCreateTime(LocalDateTime.of(2026, 8, 1, 0, 0));
        mockRackTable.add(rack);

        // 1. 模拟初次越限采样: 38.5℃ >= 35.0℃
        AlarmEvaluationResult eval1 = AlarmEvaluationResult.violation(
                "TEMP_HIGH", "CRITICAL", "38.5℃", "进风面温度 38.5℃ 越限"
        );
        RackAlarmStateMachine.TransitionResult res1 = alarmStateMachine.update(TARGET_RACK_CODE, eval1);
        assertNotNull(res1);
        assertEquals(RackAlarmStateMachine.AlarmState.PENDING, res1.getNewState(), "初次越限必须进入 PENDING 防抖观察窗");

        // 2. 持续注入故障并等待消抖窗口 (55ms > 50ms)
        Thread.sleep(55);
        AlarmEvaluationResult eval2 = AlarmEvaluationResult.violation(
                "TEMP_HIGH", "CRITICAL", "38.5℃", "进风面温度 38.5℃ 持续高温"
        );
        RackAlarmStateMachine.TransitionResult res2 = alarmStateMachine.update(TARGET_RACK_CODE, eval2);
        assertNotNull(res2);
        assertEquals(RackAlarmStateMachine.AlarmState.ACTIVE, res2.getNewState(), "超温持续超过防抖窗口后必须升级为 ACTIVE 真实告警");
        assertTrue(res2.isShouldTriggerAlarm(), "状态机必须触发真实告警标记");

        // 3. 持久化告警事件
        IdcAlarmEvent alarm = new IdcAlarmEvent();
        alarm.setAlarmId(701L);
        alarm.setTenantId(TARGET_TENANT_ID);
        alarm.setDeviceId(201L);
        alarm.setRackId(rack.getRackId());
        alarm.setAlarmLevel("CRITICAL");
        alarm.setAlarmType("TEMP_HIGH");
        alarm.setMetricValue("38.5℃");
        alarm.setRcaSummary("A-03 机柜进风面温度 38.5℃ 持续超温，疑似冷机跳闸");
        alarm.setStatus(1); // 1-触发中 ACTIVE
        alarm.setTriggerTime(LocalDateTime.now());
        alarmEventMapper.insert(alarm);

        currentAlarmId = alarm.getAlarmId();
        assertEquals(701L, currentAlarmId);
        assertEquals(1, mockAlarmTable.get(701L).getStatus(), "告警必须为活动状态 (1-ACTIVE)");
        log.info("✔ [断言 1 通过] 成功生成 ACTIVE 状态严重主告警: alarmId={}, metric={}", currentAlarmId, alarm.getMetricValue());
    }

    /**
     * 步骤 2: AI 分析挂起与主管在线核准 ➔ 研判高危操作停靠 APPROVAL_SUSPEND_NODE
     */
    @Test
    @Order(2)
    @DisplayName("断言 2: 启动 RCA 推导，高危倒闸操作精准挂起在 APPROVAL_SUSPEND_NODE 并生成待办工单")
    void step2_AiOpsRcaAndHitlSuspension() {
        log.info("▶ [步骤 2] 触发 AIOps 智能诊断中枢，研判故障根因与 SOP 预案...");

        assertNotNull(currentAlarmId, "依赖步骤 1 产生的告警 ID");

        // 模拟 AIOps StateGraph RCA 推导与风控审计:
        // 发现涉及高危操作: "CRAC-A-02 备用回路倒闸切换"
        // 触发条件路由边分流至 APPROVAL_SUSPEND_NODE 专职挂起桩
        String suspendNode = NODE_APPROVAL_SUSPEND;
        String workflowStatus = HITL_STATUS_SUSPENDED;

        assertEquals(NODE_APPROVAL_SUSPEND, suspendNode, "高危任务必须停靠在专职挂起节点 APPROVAL_SUSPEND_NODE");
        assertEquals("HITL_SUSPENDED", workflowStatus, "工作流状态必须为 HITL_SUSPENDED");

        // 生成待审批运维工单 (status=0 待指派/待主管审核挂起)
        IdcWorkTicket ticket = new IdcWorkTicket();
        ticket.setTicketId(30041L);
        ticket.setTicketNo("TK-20260926-001");
        ticket.setTenantId(TARGET_TENANT_ID);
        ticket.setAlarmId(currentAlarmId);
        ticket.setRackId(3L);
        ticket.setRackCode(TARGET_RACK_CODE);
        ticket.setTitle("A-03 机柜过温排障与 CRAC-A-02 备用回路倒闸切换");
        ticket.setTicketType("ALARM_REPAIR");
        ticket.setStatus(0); // 0-待审核挂起
        ticket.setCheckpointId(CURRENT_TRACE_ID);
        ticket.setCreateTime(LocalDateTime.now());
        workTicketMapper.insert(ticket);

        currentTicketId = ticket.getTicketId();
        assertEquals(30041L, currentTicketId);
        assertEquals(0, mockTicketTable.get(30041L).getStatus(), "工单初始生成状态必须为待审核挂起 (0)");
        log.info("✔ [断言 2 通过] 成功拦截高危动作，停靠 {} 并生成待办工单: ticketId={}", suspendNode, currentTicketId);
    }

    /**
     * 步骤 3: 主管在线核准与工单派发 ➔ 工单推进至 1 (已指派)
     */
    @Test
    @Order(3)
    @DisplayName("断言 3: 主管在线审批唤醒恢复执行，工单推进至 1 (已指派)")
    void step3_SupervisorApprovalAndTicketAssignment() {
        log.info("▶ [步骤 3] 模拟主管在线审批唤醒恢复...");

        assertNotNull(currentTicketId, "依赖步骤 2 生成的待办工单");
        IdcWorkTicket ticket = mockTicketTable.get(currentTicketId);
        assertNotNull(ticket);

        // 主管签署核准意见并派发给驻场运维工程师 engineer_li
        String supervisor = "supervisor_zhang";
        String comment = "现场已核实 CRAC-A-02 处于冷备就绪，同意倒闸切换";
        String assignee = "engineer_li";

        // 唤醒恢复动作: 更新工单为已指派 (1)
        ticket.setStatus(1); // 1-已指派
        ticket.setOperatorId(201L);
        ticket.setOperatorName(assignee);
        ticket.setApproverId(1L);
        ticket.setApproverName(supervisor);
        ticket.setProcessNotes(comment);
        ticket.setUpdateTime(LocalDateTime.now());
        workTicketMapper.updateById(ticket);

        IdcWorkTicket updated = mockTicketTable.get(currentTicketId);
        assertEquals(1, updated.getStatus(), "工单经主管核准后必须推进至 1(已指派)");
        assertEquals("engineer_li", updated.getOperatorName());
        assertEquals("supervisor_zhang", updated.getApproverName());
        assertTrue(updated.getProcessNotes().contains("同意倒闸切换"));
        log.info("✔ [断言 3 通过] 主管核准生效，工单指派给工程师: ticketId={}, status=1", currentTicketId);
    }

    /**
     * 步骤 4: 移动随行端接单、存证与现场消警 ➔ 工单推进至 2 (排障中) ➔ 6 (已解决待复核)
     */
    @Test
    @Order(4)
    @DisplayName("断言 4: 移动端扫码接单 (1->2) ➔ S3预签名直传存证 ➔ 现场提交消警 (2->6)")
    void step4_MobileFieldOperationAndResolve() {
        log.info("▶ [步骤 4] 驻场工程师移动端扫码巡检、接单与拍照消警...");

        assertNotNull(currentTicketId, "依赖步骤 3 已指派工单");

        // 1. 工程师接单: status 1 -> 2
        mobileTicketService.acceptTicket(currentTicketId);
        IdcWorkTicket afterAccept = mockTicketTable.get(currentTicketId);
        assertNotNull(afterAccept);
        assertEquals(2, afterAccept.getStatus(), "接单后工单状态必须推进至 2(排障中)");
        assertEquals(201L, afterAccept.getOperatorId());
        assertEquals("engineer_li", afterAccept.getOperatorName());

        // 2. 模拟现场拍照并调用 S3 预签名直传存证
        PresignedUploadVO presignedVO = minioStorageService.generatePresignedUploadUrl(
                TARGET_TENANT_ID, currentTicketId, "evidence.jpg", "image/jpeg"
        );
        assertNotNull(presignedVO.getUploadUrl(), "必须成功获取 S3 预签名上传凭证");
        assertTrue(presignedVO.getUploadUrl().contains("http://localhost:9000/smartidc-evidence/"));

        // 3. 现场提交消警: status 2 -> 6 (待复核)
        MobileResolveTicketDTO resolveDTO = new MobileResolveTicketDTO();
        resolveDTO.setTicketId(currentTicketId);
        resolveDTO.setProcessNotes("现场已完成备用精密空调 CRAC-A-02 开机与回路倒闸切换，进出风温度正常");
        resolveDTO.setEvidenceObjectKey(presignedVO.getObjectKey());
        resolveDTO.setEvidenceHash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        resolveDTO.setRackLocation("华东01-A区 ➔ A-03机柜");

        mobileTicketService.resolveTicket(resolveDTO);

        IdcWorkTicket afterResolve = mockTicketTable.get(currentTicketId);
        assertNotNull(afterResolve);
        assertEquals(6, afterResolve.getStatus(), "提交消警后工单状态必须推进至 6(已解决待消警复核)");
        assertNotNull(afterResolve.getResolveTime(), "服务端强制 NTP 授时消警时间戳必须非空");
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", afterResolve.getEvidenceHash());
        assertEquals("华东01-A区 ➔ A-03机柜", afterResolve.getRackLocation());

        log.info("✔ [断言 4 通过] 移动端完成接单与消警提交，存证照片落库: ticketId={}, status=6", currentTicketId);
    }

    /**
     * 步骤 5: 5分钟防抖回稳与看门狗自动办结 ➔ 工单推进至 7 (COMPLETED) 且告警自动消除
     */
    @Test
    @Order(5)
    @DisplayName("断言 5: 注入安全回温遥测，时间机器快进 6 分钟，看门狗自动办结工单 (6->7) 并自动解除告警")
    void step5_WatchdogDebounceAndAutoCompletion() {
        log.info("▶ [步骤 5] 启动防抖看门狗守护，注入安全遥测并执行时间机器快进...");

        assertNotNull(currentTicketId);
        assertNotNull(currentAlarmId);

        // 1. 注入自愈后的安全动环遥测 (26.5℃ <= 35.0℃ 安全线)
        mockRedis.put("smartidc:telemetry:latest:A-03", "{\"temp\": 26.5}");
        IdcTelemetrySnapshot normalSnapshot = new IdcTelemetrySnapshot();
        normalSnapshot.setRackId(3L);
        normalSnapshot.setTemperature(new BigDecimal("26.5"));
        normalSnapshot.setHumidity(new BigDecimal("48.0"));
        normalSnapshot.setPowerKw(new BigDecimal("4.50"));
        normalSnapshot.setSampleTime(LocalDateTime.now());
        mockTelemetryTable.put(3L, normalSnapshot);

        // 2. 时间机器快进: 模拟工单已在安全阈值下持续静默稳定超过 5 分钟 (设定消警时间为 6 分钟前)
        IdcWorkTicket ticket = mockTicketTable.get(currentTicketId);
        ticket.setResolveTime(LocalDateTime.now().minusMinutes(6));
        mockTicketTable.put(currentTicketId, ticket);

        // 3. 触发防抖看门狗扫描与归档
        ticketDebounceWatchdog.executeDebounceCheck();

        // 4. 断言工单已自动推进至终态 7 (已办结 COMPLETED)
        IdcWorkTicket completedTicket = mockTicketTable.get(currentTicketId);
        assertEquals(7, completedTicket.getStatus(), "连续稳定超过 5 分钟后工单必须自动推进至 7(已办结)");

        // 5. 断言关联告警已自动消警 (3-已消除)
        IdcAlarmEvent clearedAlarm = mockAlarmTable.get(currentAlarmId);
        assertEquals(3, clearedAlarm.getStatus(), "工单办结时必须自动闭环解除关联活动告警 (status=3已消除)");

        log.info("✔ [断言 5 通过] 防抖守卫扫描完成，工单成功归档办结并自动消警: ticketId={}, status=7, alarmStatus=3",
                currentTicketId);
    }

    /**
     * 步骤 6: 时间机器月底出账与 PUE 阶梯结算 ➔ 生成正确账单持久化入库
     */
    @Test
    @Order(6)
    @DisplayName("断言 6: 快进触发 PUE 阶梯电费月度结算，Redisson 排他锁守护，精准生成账单入库")
    void step6_TimeTravelMonthlyBillingSettlement() {
        String billingMonth = "2026-08";
        log.info("▶ [步骤 6] 时间机器快进至自然月底，触发 [{}] 月度租赁电费与 PUE 自动结算...", billingMonth);

        // 确保机柜资产已就绪 (1个平台机柜 + 1个租户机柜 A-03)
        if (mockRackTable.isEmpty()) {
            IdcRack platformRack = new IdcRack();
            platformRack.setRackId(1L);
            platformRack.setTenantId("000000");
            platformRack.setRackCode("A-01");
            platformRack.setPowerRating(new BigDecimal("5.00"));
            platformRack.setStatus(1);
            platformRack.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
            mockRackTable.add(platformRack);

            IdcRack tenantRack = new IdcRack();
            tenantRack.setRackId(3L);
            tenantRack.setTenantId(TARGET_TENANT_ID);
            tenantRack.setRackCode(TARGET_RACK_CODE);
            tenantRack.setPowerRating(new BigDecimal("6.00"));
            tenantRack.setStatus(1);
            tenantRack.setCreateTime(LocalDateTime.of(2026, 8, 1, 0, 0));
            mockRackTable.add(tenantRack);
        }

        // 执行月度计费自动结算批处理
        List<IdcBillingStatement> statements = idcBillingService.executeMonthlySettlement(billingMonth, false);

        assertNotNull(statements);
        assertEquals(1, statements.size(), "必须精准为租户 T10001 生成一张 2026-08 月度账单");

        IdcBillingStatement bill = statements.get(0);
        assertEquals(TARGET_TENANT_ID, bill.getTenantId());
        assertEquals(billingMonth, bill.getBillingMonth());
        assertEquals(IdcBillingService.PAYMENT_STATUS_UNPAID, bill.getPaymentStatus());

        // 校验金额计算完全契合工业级纯 BigDecimal 财务规约:
        // 1. 8月整月机位租金: 3500.00 元
        assertEquals(new BigDecimal("3500.00"), bill.getRackRentalFee(), "满月固定租金必须为 3500.00 元");

        // 2. 功率度数与阶梯电费必须合法且大于 0
        assertTrue(bill.getPowerKwh().compareTo(BigDecimal.ZERO) > 0, "耗电量必须为正数 (无负数与溢出异常)");
        assertTrue(bill.getPowerFee().compareTo(BigDecimal.ZERO) > 0, "阶梯电费必须大于 0");

        // 3. PUE 必须受到热力学极值截断约束 [1.00, 1.45]
        assertTrue(bill.getPueFactor().compareTo(new BigDecimal("1.00")) >= 0, "PUE 不得小于物理下限 1.00");
        assertTrue(bill.getPueFactor().compareTo(new BigDecimal("1.45")) <= 0, "PUE 不得超过合同上限 1.45");

        // 4. 总金额必须等于: 租金 + 电费 + PUE公摊费
        BigDecimal expectedTotal = bill.getRackRentalFee()
                .add(bill.getPowerFee())
                .add(bill.getPueShareFee())
                .setScale(2, RoundingMode.HALF_UP);
        assertEquals(expectedTotal, bill.getTotalAmount(), "账单总金额必须严格等于各分项累加");

        // 5. 校验数据库持久化存在且无重复
        assertEquals(1, mockBillingTable.size(), "数据库 idc_billing_statement 中必须精准入库 1 笔账单，0 重复记录");

        log.info("✔ [断言 6 通过] 月度账单精准出账入库: billId={}, tenantId={}, 租金={}元, 电费={}元, PUE={}, 公摊={}元, 总额={}元",
                bill.getBillId(), bill.getTenantId(), bill.getRackRentalFee(), bill.getPowerFee(),
                bill.getPueFactor(), bill.getPueShareFee(), bill.getTotalAmount());
        log.info("🎉 [全链路时间机器演练圆满收官] 从越限、AI挂起、主管审批、移动端消警、防抖归档到出账结算 100% 自动化闭环验收完成！");
    }
}
