package com.smartidc.biz.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartidc.biz.domain.dto.mobile.MobileResolveTicketDTO;
import com.smartidc.biz.domain.entity.IdcAlarmEvent;
import com.smartidc.biz.domain.entity.IdcRack;
import com.smartidc.biz.domain.entity.IdcWorkTicket;
import com.smartidc.biz.domain.vo.mobile.MobileTicketDetailVO;
import com.smartidc.biz.domain.vo.mobile.PresignedUploadVO;
import com.smartidc.biz.mapper.IdcAlarmEventMapper;
import com.smartidc.biz.mapper.IdcRackMapper;
import com.smartidc.biz.mapper.IdcTelemetrySnapshotMapper;
import com.smartidc.biz.mapper.IdcWorkTicketMapper;
import com.smartidc.biz.service.MinioStorageService;
import com.smartidc.biz.service.impl.MinioStorageServiceImpl;
import com.smartidc.biz.service.impl.MobileTicketServiceImpl;
import com.smartidc.biz.service.watchdog.TicketDebounceWatchdog;
import com.smartidc.common.exception.ServiceException;
import com.smartidc.framework.security.UserContext;
import com.smartidc.framework.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 智维云 (SmartIDC) Phase 5.3 移动端工单流转、存证与防抖自动归档全生命周期测试
 * 对标 implementation_plan5.3.md 第四节验收清单 (V2, V3, V4)
 */
public class Phase5TicketLifecycleAndWatchdogTest {

    private IdcWorkTicketMapper idcWorkTicketMapper;
    private IdcRackMapper idcRackMapper;
    private IdcAlarmEventMapper idcAlarmEventMapper;
    private IdcTelemetrySnapshotMapper idcTelemetrySnapshotMapper;
    private MinioStorageService minioStorageService;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;

    private MobileTicketServiceImpl mobileTicketService;
    private TicketDebounceWatchdog ticketDebounceWatchdog;

    private final Map<Long, IdcWorkTicket> mockTicketTable = new ConcurrentHashMap<>();
    private final Map<Long, IdcAlarmEvent> mockAlarmTable = new ConcurrentHashMap<>();
    private final Map<String, String> mockRedis = new ConcurrentHashMap<>();

    private IdcWorkTicket testTicketTenant1; // T10001
    private IdcWorkTicket testTicketTenant2; // T20002
    private IdcAlarmEvent testAlarm;
    private IdcRack testRack;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() {
        TenantContext.setTenantId("T10001");
        UserContext.setUserId(201L);
        UserContext.setUsername("engineer_li");
        UserContext.setRoleKey("engineer");

        objectMapper = new ObjectMapper();

        // 1. Mock IdcWorkTicketMapper
        idcWorkTicketMapper = mock(IdcWorkTicketMapper.class);
        when(idcWorkTicketMapper.selectByIdIgnoreTenant(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return mockTicketTable.get(id);
        });
        when(idcWorkTicketMapper.updateById(any(IdcWorkTicket.class))).thenAnswer(inv -> {
            IdcWorkTicket t = inv.getArgument(0);
            mockTicketTable.put(t.getTicketId(), t);
            return 1;
        });
        when(idcWorkTicketMapper.selectTicketsByStatusIgnoreTenant(any())).thenAnswer(inv -> {
            Integer st = inv.getArgument(0);
            List<IdcWorkTicket> list = new ArrayList<>();
            for (IdcWorkTicket t : mockTicketTable.values()) {
                if (Objects.equals(t.getStatus(), st)) {
                    list.add(t);
                }
            }
            return list;
        });

        // 2. Mock IdcAlarmEventMapper
        idcAlarmEventMapper = mock(IdcAlarmEventMapper.class);
        when(idcAlarmEventMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return mockAlarmTable.get(id);
        });
        when(idcAlarmEventMapper.updateById(any(IdcAlarmEvent.class))).thenAnswer(inv -> {
            IdcAlarmEvent a = inv.getArgument(0);
            mockAlarmTable.put(a.getAlarmId(), a);
            return 1;
        });

        // 3. Mock IdcRackMapper
        idcRackMapper = mock(IdcRackMapper.class);
        testRack = new IdcRack();
        testRack.setRackId(3L);
        testRack.setRackCode("A-03");
        testRack.setRoomName("华东01-A区");
        testRack.setTenantId("T10001");
        when(idcRackMapper.selectById(3L)).thenReturn(testRack);
        when(idcRackMapper.selectOneByCodeIgnoreTenant("A-03")).thenReturn(testRack);

        // 4. Mock IdcTelemetrySnapshotMapper
        idcTelemetrySnapshotMapper = mock(IdcTelemetrySnapshotMapper.class);

        // 5. MinioStorageService
        minioStorageService = mock(MinioStorageService.class);
        when(minioStorageService.objectExists(anyString())).thenReturn(true);
        when(minioStorageService.getViewUrl(anyString())).thenAnswer(inv -> "http://localhost:9000/smartidc-inspection/" + inv.getArgument(0));
        when(minioStorageService.calculateSha256(any())).thenReturn("a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90");
        when(minioStorageService.calculateObjectSha256(anyString())).thenReturn("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        // 6. Mock StringRedisTemplate
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(inv -> mockRedis.get((String) inv.getArgument(0)));

        // 初始化预设工单与告警数据
        testTicketTenant1 = new IdcWorkTicket();
        testTicketTenant1.setTicketId(101L);
        testTicketTenant1.setTicketNo("TK-20260926-001");
        testTicketTenant1.setTenantId("T10001");
        testTicketTenant1.setRackId(3L);
        testTicketTenant1.setRackCode("A-03");
        testTicketTenant1.setTitle("[高温排障] A-03 机柜温度超温越限");
        testTicketTenant1.setTicketType("ALARM_REPAIR");
        testTicketTenant1.setStatus(1); // 1-已指派
        testTicketTenant1.setAlarmId(501L);
        testTicketTenant1.setCreateTime(LocalDateTime.now().minusHours(1));
        mockTicketTable.put(101L, testTicketTenant1);

        testTicketTenant2 = new IdcWorkTicket();
        testTicketTenant2.setTicketId(102L);
        testTicketTenant2.setTicketNo("TK-20260926-002");
        testTicketTenant2.setTenantId("T20002"); // 属于外部租户
        testTicketTenant2.setRackId(8L);
        testTicketTenant2.setRackCode("B-02");
        testTicketTenant2.setTitle("[机柜检查] 跨租户机柜");
        testTicketTenant2.setStatus(1);
        mockTicketTable.put(102L, testTicketTenant2);

        testAlarm = new IdcAlarmEvent();
        testAlarm.setAlarmId(501L);
        testAlarm.setRackId(3L);
        testAlarm.setAlarmLevel("CRITICAL");
        testAlarm.setAlarmType("TEMP_HIGH");
        testAlarm.setStatus(1); // 1-触发中
        mockAlarmTable.put(501L, testAlarm);

        mobileTicketService = new MobileTicketServiceImpl(
                idcWorkTicketMapper,
                idcRackMapper,
                idcAlarmEventMapper,
                idcTelemetrySnapshotMapper,
                minioStorageService,
                stringRedisTemplate,
                objectMapper
        );

        ticketDebounceWatchdog = new TicketDebounceWatchdog(
                idcWorkTicketMapper,
                idcAlarmEventMapper,
                idcTelemetrySnapshotMapper,
                stringRedisTemplate,
                objectMapper
        );
    }

    @AfterEach
    public void tearDown() {
        TenantContext.clear();
        UserContext.clear();
        mockTicketTable.clear();
        mockAlarmTable.clear();
        mockRedis.clear();
    }

    @Test
    @DisplayName("V3: 验证现场工单完整生命周期状态流转 (接单 ➔ 挂起 ➔ 恢复 ➔ 消警复核)")
    public void testV3_TicketLifecycleStateTransitions() {
        // 1. 工程师现场一键接单: 1(已指派) ➔ 2(排障中)
        mobileTicketService.acceptTicket(101L);
        IdcWorkTicket afterAccept = mockTicketTable.get(101L);
        assertEquals(2, afterAccept.getStatus(), "接单后工单状态必须推进至 2 (排障中)");
        assertEquals(201L, afterAccept.getOperatorId());
        assertEquals("engineer_li", afterAccept.getOperatorName());

        // 2. 现场发现缺件，申请挂起: 2(排障中) ➔ 3(挂起待审批/备件)
        mobileTicketService.suspendTicket(101L, "等待核心交换机光模块备件到货");
        IdcWorkTicket afterSuspend = mockTicketTable.get(101L);
        assertEquals(3, afterSuspend.getStatus(), "挂起后工单状态必须推进至 3 (挂起待审批/备件)");
        assertTrue(afterSuspend.getProcessNotes().contains("光模块备件"));

        // 3. 备件送达，恢复现场排障: 3(挂起) ➔ 2(排障中)
        mobileTicketService.resumeTicket(101L);
        IdcWorkTicket afterResume = mockTicketTable.get(101L);
        assertEquals(2, afterResume.getStatus(), "恢复排障后工单状态必须回到 2 (排障中)");

        // 4. 完成处置，提交现场消警与双步防爆存证: 2(排障中) ➔ 6(已解决待复核)
        MobileResolveTicketDTO dto = new MobileResolveTicketDTO();
        dto.setTicketId(101L);
        dto.setProcessNotes("已清理百叶窗滤网积灰，并调整备用空调风阀至40%");
        dto.setEvidenceObjectKey("inspection/T10001/202609/TK101_1790432000.jpg");
        dto.setRackLocation("华东01-A区 ➔ A-03机柜");
        dto.setEvidenceHash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        mobileTicketService.resolveTicket(dto);
        IdcWorkTicket afterResolve = mockTicketTable.get(101L);
        assertEquals(6, afterResolve.getStatus(), "提交消警后工单状态必须推进至 6 (已解决待消警复核)");
        assertNotNull(afterResolve.getResolveTime(), "服务端强制 NTP 授时打标不能为空");
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", afterResolve.getEvidenceHash());
        assertEquals("华东01-A区 ➔ A-03机柜", afterResolve.getRackLocation());
    }

    @Test
    @DisplayName("V3: 验证 BOLA 跨租户工单越权拦截防护 (403 Forbidden)")
    public void testV3_BolaCrossTenantTicketProtection() {
        // 当前用户隶属租户 T10001，尝试操作 T20002 的工单 102
        ServiceException ex = assertThrows(ServiceException.class, () -> {
            mobileTicketService.acceptTicket(102L);
        });
        assertEquals(403, ex.getCode(), "跨租户操作工单必须拦截并返回 403 FORBIDDEN");
        assertTrue(ex.getMessage().contains("无权操作非本租户所属工单"));

        // 验证平台方主管 000000 具备跨租户巡检权限
        TenantContext.setTenantId("000000");
        UserContext.setRoleKey("supervisor");
        assertDoesNotThrow(() -> {
            mobileTicketService.acceptTicket(102L);
        }, "平台总控主管应对全机房工单具备管理权限");
        assertEquals(2, mockTicketTable.get(102L).getStatus());
    }

    @Test
    @DisplayName("V2: 验证 MinIO S3 预签名直传凭证与存证回显")
    public void testV2_MinioPresignedUploadAndDetailView() {
        PresignedUploadVO uploadVO = new PresignedUploadVO(
                "http://localhost:9000/smartidc-inspection/inspection/T10001/202609/TK101.jpg?X-Amz-Signature=test",
                "inspection/T10001/202609/TK101.jpg",
                "http://localhost:9000/smartidc-inspection/inspection/T10001/202609/TK101.jpg",
                System.currentTimeMillis() + 900000L
        );
        when(minioStorageService.generatePresignedUploadUrl(anyString(), any(), anyString(), anyString()))
                .thenReturn(uploadVO);

        PresignedUploadVO result = minioStorageService.generatePresignedUploadUrl("T10001", 101L, "photo.jpg", "image/jpeg");
        assertNotNull(result);
        assertTrue(result.getUploadUrl().contains("X-Amz-Signature="));
        assertEquals("inspection/T10001/202609/TK101.jpg", result.getObjectKey());

        // 测试工单详情接口正确回显存证 URL
        testTicketTenant1.setStatus(6);
        testTicketTenant1.setEvidenceObjectKey("inspection/T10001/202609/TK101.jpg");
        testTicketTenant1.setEvidenceHash("abcd1234abcd5678");

        MobileTicketDetailVO detail = mobileTicketService.getTicketDetail(101L);
        assertNotNull(detail);
        assertEquals("http://localhost:9000/smartidc-inspection/inspection/T10001/202609/TK101.jpg", detail.getEvidenceViewUrl());
        assertEquals("abcd1234abcd5678", detail.getEvidenceHash());
        assertEquals(101L, detail.getTicketId());
    }

    @Test
    @DisplayName("V4: 验证动环连续 5 分钟安全回温 (<35℃) 自动防抖归档并消除联动告警")
    public void testV4_WatchdogDebounceAutoCompletion() {
        // 设置工单为 6(已解决待复核)，消警时间为 6 分钟前 (> 5 分钟)
        LocalDateTime sixMinutesAgo = LocalDateTime.now().minusMinutes(6);
        testTicketTenant1.setStatus(6);
        testTicketTenant1.setResolveTime(sixMinutesAgo);

        // 模拟 Redis 中机柜 A-03 温度平稳回落至 26.5℃ (<= 35.0℃ 安全区间)
        mockRedis.put("smartidc:telemetry:latest:A-03", "{\"temp\": 26.5, \"humidity\": 45.0}");

        // 执行防抖守卫扫描
        ticketDebounceWatchdog.executeDebounceCheck();

        IdcWorkTicket afterWatchdog = mockTicketTable.get(101L);
        assertEquals(7, afterWatchdog.getStatus(), "动环连续稳定 5 分钟以上必须自动推进为 7 (COMPLETED 已办结归档)");
        assertNotNull(afterWatchdog.getFinishTime(), "办结时间不能为空");
        assertTrue(afterWatchdog.getProcessNotes().contains("防抖守卫自动归档"));

        // 验证联动告警消除
        IdcAlarmEvent alarm = mockAlarmTable.get(501L);
        assertEquals(3, alarm.getStatus(), "办结时联动关联告警必须自动流转为 3 (已消除)");
        assertNotNull(alarm.getClearTime(), "告警消除时间不能为空");
    }

    @Test
    @DisplayName("V4: 验证动环震荡反弹超温时不误关单，超过 30 分钟未回稳触发逃生通道打回排障中")
    public void testV4_WatchdogFlappingAnd30MinEscape() {
        // 场景 1: 消警已达 6 分钟，但机柜温度依然处于 42.0℃ 高温越限 (Flapping 边缘震荡)
        LocalDateTime sixMinutesAgo = LocalDateTime.now().minusMinutes(6);
        testTicketTenant1.setStatus(6);
        testTicketTenant1.setResolveTime(sixMinutesAgo);
        mockRedis.put("smartidc:telemetry:latest:A-03", "{\"temp\": 42.0, \"humidity\": 48.0}");

        ticketDebounceWatchdog.executeDebounceCheck();

        IdcWorkTicket flappingTicket = mockTicketTable.get(101L);
        assertEquals(6, flappingTicket.getStatus(), "温度超温越限时，绝对不可提前误关单，应保持在 6 观察期");

        // 场景 2: 消警已过去 32 分钟 (> 30 分钟逃生阈值)，温度仍持续超温 42.0℃
        LocalDateTime thirtyTwoMinutesAgo = LocalDateTime.now().minusMinutes(32);
        testTicketTenant1.setResolveTime(thirtyTwoMinutesAgo);

        ticketDebounceWatchdog.executeDebounceCheck();

        IdcWorkTicket escapeTicket = mockTicketTable.get(101L);
        assertEquals(2, escapeTicket.getStatus(), "超过 30 分钟未回稳必须触发超时逃生通道，打回至 2 (排障中)");
        assertTrue(escapeTicket.getProcessNotes().contains("超时逃生通道触发"));
    }
}
