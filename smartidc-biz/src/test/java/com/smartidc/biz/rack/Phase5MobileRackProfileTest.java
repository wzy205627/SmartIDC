package com.smartidc.biz.rack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartidc.biz.domain.entity.IdcAlarmEvent;
import com.smartidc.biz.domain.entity.IdcDevice;
import com.smartidc.biz.domain.entity.IdcRack;
import com.smartidc.biz.domain.entity.IdcTelemetrySnapshot;
import com.smartidc.biz.domain.entity.IdcWorkTicket;
import com.smartidc.biz.domain.vo.mobile.MobileRackProfileVO;
import com.smartidc.biz.domain.vo.mobile.MobileRackTelemetryVO;
import com.smartidc.biz.domain.vo.mobile.MobileWorkTicketPageVO;
import com.smartidc.biz.mapper.IdcAlarmEventMapper;
import com.smartidc.biz.mapper.IdcDeviceMapper;
import com.smartidc.biz.mapper.IdcRackMapper;
import com.smartidc.biz.mapper.IdcTelemetrySnapshotMapper;
import com.smartidc.biz.mapper.IdcWorkTicketMapper;
import com.smartidc.biz.service.impl.MobileRackServiceImpl;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 智维云 (SmartIDC) 阶段 5.2：机柜微画像、BOLA 跨租户越权防护与 Redis 离线哨兵兜底测试
 * 对标 implementation_plan5.2.md 第四节验收清单 (V2, V3, 42U聚合)
 */
public class Phase5MobileRackProfileTest {

    private IdcRackMapper idcRackMapper;
    private IdcDeviceMapper idcDeviceMapper;
    private IdcAlarmEventMapper idcAlarmEventMapper;
    private IdcWorkTicketMapper idcWorkTicketMapper;
    private IdcTelemetrySnapshotMapper idcTelemetrySnapshotMapper;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private MobileRackServiceImpl mobileRackService;

    private final Map<String, String> mockRedis = new ConcurrentHashMap<>();

    // 预设三个租户的测试机柜
    private IdcRack rackPlatform; // 租户 000000 (A-03)
    private IdcRack rackTenant1;  // 租户 T10001 (B-01)
    private IdcRack rackTenant2;  // 租户 T20002 (B-02)

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockRedis.clear();
        objectMapper = new ObjectMapper();

        // 1. 初始化机柜 Mock
        idcRackMapper = mock(IdcRackMapper.class);

        rackPlatform = new IdcRack();
        rackPlatform.setRackId(3L);
        rackPlatform.setTenantId("000000");
        rackPlatform.setRoomName("华东01-A区");
        rackPlatform.setRackCode("A-03");
        rackPlatform.setTotalU(42);
        rackPlatform.setPowerRating(BigDecimal.valueOf(6.0));
        rackPlatform.setStatus(1);

        rackTenant1 = new IdcRack();
        rackTenant1.setRackId(7L);
        rackTenant1.setTenantId("T10001");
        rackTenant1.setRoomName("华东02-B区");
        rackTenant1.setRackCode("B-01");
        rackTenant1.setTotalU(42);
        rackTenant1.setPowerRating(BigDecimal.valueOf(6.0));
        rackTenant1.setStatus(1);

        rackTenant2 = new IdcRack();
        rackTenant2.setRackId(8L);
        rackTenant2.setTenantId("T20002");
        rackTenant2.setRoomName("华东02-B区");
        rackTenant2.setRackCode("B-02");
        rackTenant2.setTotalU(42);
        rackTenant2.setPowerRating(BigDecimal.valueOf(5.0));
        rackTenant2.setStatus(1);

        when(idcRackMapper.selectOneByCodeIgnoreTenant("A-03")).thenReturn(rackPlatform);
        when(idcRackMapper.selectOneByCodeIgnoreTenant("RACK-A-03")).thenReturn(rackPlatform);
        when(idcRackMapper.selectOneByCodeIgnoreTenant("RACK-A03")).thenReturn(rackPlatform);
        when(idcRackMapper.selectOneByCodeIgnoreTenant("B-01")).thenReturn(rackTenant1);
        when(idcRackMapper.selectOneByCodeIgnoreTenant("RACK-B-01")).thenReturn(rackTenant1);
        when(idcRackMapper.selectOneByCodeIgnoreTenant("RACK-B01")).thenReturn(rackTenant1);
        when(idcRackMapper.selectOneByCodeIgnoreTenant("B-02")).thenReturn(rackTenant2);
        when(idcRackMapper.selectOneByCodeIgnoreTenant("RACK-B-02")).thenReturn(rackTenant2);
        when(idcRackMapper.selectOneByCodeIgnoreTenant("RACK-B02")).thenReturn(rackTenant2);

        // 2. 初始化设备 Mock
        idcDeviceMapper = mock(IdcDeviceMapper.class);
        List<IdcDevice> rack3Devices = new ArrayList<>();
        IdcDevice dev1 = new IdcDevice();
        dev1.setDeviceId(101L);
        dev1.setIotDeviceKey("TH-A03-001");
        dev1.setDeviceName("温湿度传感器");
        dev1.setDeviceType("SENSOR_TEMP");
        dev1.setStartU(1);
        dev1.setUHeight(1);
        dev1.setStatus(1);
        rack3Devices.add(dev1);

        IdcDevice dev2 = new IdcDevice();
        dev2.setDeviceId(102L);
        dev2.setIotDeviceKey("SRV-A03-001");
        dev2.setDeviceName("GPU训练节点");
        dev2.setDeviceType("IT_SERVER");
        dev2.setStartU(10);
        dev2.setUHeight(4);
        dev2.setStatus(1);
        rack3Devices.add(dev2);

        when(idcDeviceMapper.selectListByRackIdIgnoreTenant(3L)).thenReturn(rack3Devices);
        when(idcDeviceMapper.selectListByRackIdIgnoreTenant(7L)).thenReturn(Collections.emptyList());
        when(idcDeviceMapper.selectListByRackIdIgnoreTenant(8L)).thenReturn(Collections.emptyList());

        // 3. 告警与工单 Mock
        idcAlarmEventMapper = mock(IdcAlarmEventMapper.class);
        List<IdcAlarmEvent> activeAlarms = new ArrayList<>();
        IdcAlarmEvent alarm = new IdcAlarmEvent();
        alarm.setAlarmId(501L);
        alarm.setAlarmLevel("CRITICAL");
        alarm.setAlarmType("TEMP_HIGH");
        alarm.setMetricValue("32.5℃");
        alarm.setRcaSummary("精密空调出风口回风受阻导致微过温");
        alarm.setTriggerTime(LocalDateTime.now().minusMinutes(5));
        activeAlarms.add(alarm);

        when(idcAlarmEventMapper.selectActiveAlarmsByRackId(3L)).thenReturn(activeAlarms);
        when(idcAlarmEventMapper.selectActiveAlarmsByRackId(7L)).thenReturn(Collections.emptyList());
        when(idcAlarmEventMapper.selectActiveAlarmsByRackId(8L)).thenReturn(Collections.emptyList());

        idcWorkTicketMapper = mock(IdcWorkTicketMapper.class);
        List<IdcWorkTicket> mockTickets = new ArrayList<>();
        IdcWorkTicket ticket = new IdcWorkTicket();
        ticket.setTicketId(9001L);
        ticket.setTitle("A-03 机柜温度超标排查工单");
        ticket.setTicketType("ALARM_REPAIR");
        ticket.setStatus(2);
        ticket.setOperatorName("李工");
        ticket.setCreateTime(LocalDateTime.now().minusHours(1));
        mockTickets.add(ticket);

        when(idcWorkTicketMapper.countTicketsByRack(eq(3L), anyString())).thenReturn(1L);
        when(idcWorkTicketMapper.selectTicketsByRackWithPage(eq(3L), anyString(), eq(0), anyInt())).thenReturn(mockTickets);

        // 4. 时序快照 Mock
        idcTelemetrySnapshotMapper = mock(IdcTelemetrySnapshotMapper.class);
        IdcTelemetrySnapshot snapshot = new IdcTelemetrySnapshot();
        snapshot.setSampleTime(LocalDateTime.now().minusMinutes(30));
        when(idcTelemetrySnapshotMapper.selectLatestSnapshotByRackId(any())).thenReturn(snapshot);

        // 5. Redis Mock
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        doAnswer(invocation -> mockRedis.get(invocation.getArgument(0))).when(valueOperations).get(anyString());
        doAnswer(invocation -> {
            mockRedis.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString());

        mobileRackService = new MobileRackServiceImpl(
                idcRackMapper,
                idcDeviceMapper,
                idcAlarmEventMapper,
                idcWorkTicketMapper,
                idcTelemetrySnapshotMapper,
                stringRedisTemplate,
                objectMapper
        );
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        TenantContext.clear();
    }

    @Test
    @DisplayName("验收指标 V2：BOLA 跨租户越权安全防护测试")
    void testV2_BolaCrossTenantAccessProtection() {
        // 场景 A: 租户 T10001 工程师尝试越权查看租户 T20002 专属机柜 RACK-B02
        TenantContext.setTenantId("T10001");
        UserContext.setUser(new UserContext.LoginUser(10L, "tenant1_eng", "engineer", Set.of("华东02-B区")));

        ServiceException ex = assertThrows(ServiceException.class, () -> {
            mobileRackService.getRackProfile("RACK-B02");
        });
        assertEquals(403, ex.getCode(), "跨租户访问机柜必须强制返回 403 权限被拒绝");
        assertTrue(ex.getMessage().contains("无权查看非本租户所属机柜资产"));

        // 场景 B: 租户 T10001 工程师查看本租户所属机柜 RACK-B01 -> 允许放行
        MobileRackProfileVO profileB01 = mobileRackService.getRackProfile("RACK-B01");
        assertNotNull(profileB01);
        assertEquals("B-01", profileB01.getRackInfo().getRackCode());
        assertEquals("T10001", profileB01.getRackInfo().getTenantId());

        // 场景 C: 平台值班主管 (000000 / supervisor) 具备全机房统一视察权限 -> 跨租户机柜全放行
        TenantContext.setTenantId("000000");
        UserContext.setUser(new UserContext.LoginUser(2L, "supervisor_zhang", "supervisor", Collections.emptySet()));

        MobileRackProfileVO supervisorViewA03 = mobileRackService.getRackProfile("RACK-A03");
        assertNotNull(supervisorViewA03);
        assertEquals("A-03", supervisorViewA03.getRackInfo().getRackCode());

        MobileRackProfileVO supervisorViewB02 = mobileRackService.getRackProfile("RACK-B02");
        assertNotNull(supervisorViewB02);
        assertEquals("B-02", supervisorViewB02.getRackInfo().getRackCode());
    }

    @Test
    @DisplayName("验收指标 V3：Redis 动环离线哨兵兜底与 0 个 NPE 健壮性测试")
    void testV3_RedisOfflineSentinelFallback() {
        // 确保 Redis 中没有任何遥测缓存 (模拟传感器通信网关断线或 Key 淘汰)
        mockRedis.clear();

        TenantContext.setTenantId("000000");
        UserContext.setUser(new UserContext.LoginUser(1L, "admin", "admin", Collections.emptySet()));

        MobileRackProfileVO profile = mobileRackService.getRackProfile("RACK-A03");

        assertNotNull(profile, "接口严禁返回 null 或 500");
        assertNotNull(profile.getTelemetry());
        assertEquals("OFFLINE", profile.getTelemetry().getStatus(), "离线哨兵必须标记状态为 OFFLINE");
        assertNull(profile.getTelemetry().getTemp(), "离线状态下实时温度应为 null");
        assertEquals("UNKNOWN", profile.getTelemetry().getHealthLevel());
        assertNotNull(profile.getTelemetry().getUpdatedAt(), "必须从历史快照追溯最近一次心跳时间");

        // 验证机柜基础信息与 42U 设备摘要正常聚合，不受动环离线影响
        assertNotNull(profile.getRackInfo());
        assertEquals("A-03", profile.getRackInfo().getRackCode());
        assertEquals(42, profile.getuSlotsSummary().getTotalU());
        assertEquals(5, profile.getuSlotsSummary().getUsedU()); // 1U + 4U = 5U
        assertEquals(37, profile.getuSlotsSummary().getFreeU());
        assertEquals(2, profile.getuSlotsSummary().getMountedDevices().size());
        assertEquals(1, profile.getActiveAlarms().size());
    }

    @Test
    @DisplayName("功能测试：Redis 热遥测直读与活动告警高亮联动")
    void testOnlineTelemetryAndAlarmLinkage() {
        // 模拟物联网网关向 Redis 写入最新动环 Hash / JSON
        String telemetryJson = """
                {
                  "temperature": 32.8,
                  "humidity": 48.5,
                  "voltage": 221.0,
                  "currentAmp": 15.6,
                  "powerKw": 3.45,
                  "sampleTimestamp": 1727376000000
                }
                """;
        mockRedis.put("smartidc:telemetry:latest:A-03", telemetryJson);

        TenantContext.setTenantId("000000");
        UserContext.setUser(new UserContext.LoginUser(1L, "admin", "admin", Collections.emptySet()));

        MobileRackProfileVO profile = mobileRackService.getRackProfile("A-03");

        assertNotNull(profile);
        MobileRackTelemetryVO telem = profile.getTelemetry();
        assertEquals("ONLINE", telem.getStatus());
        assertEquals(new BigDecimal("32.8"), telem.getTemp());
        assertEquals(new BigDecimal("48.5"), telem.getHumidity());
        assertEquals(new BigDecimal("3.45"), telem.getPowerKw());
        // 因为存在活动严重告警 (TEMP_HIGH 32.5℃)，健康度联动置为 CRITICAL (红灯先亮)
        assertEquals("CRITICAL", telem.getHealthLevel());

        // 测试轻量 5 秒轮询端点
        MobileRackTelemetryVO pollVo = mobileRackService.getLatestTelemetry("A-03");
        assertEquals("ONLINE", pollVo.getStatus());
        assertEquals(new BigDecimal("32.8"), pollVo.getTemp());
    }

    @Test
    @DisplayName("功能测试：维保历史工单独立按需分页加载")
    void testGetRackTicketsPagination() {
        TenantContext.setTenantId("000000");
        UserContext.setUser(new UserContext.LoginUser(1L, "admin", "admin", Collections.emptySet()));

        MobileWorkTicketPageVO pageVo = mobileRackService.getRackTickets("A-03", 1, 5);

        assertNotNull(pageVo);
        assertEquals(1L, pageVo.getTotal());
        assertEquals(1, pageVo.getRecords().size());
        assertEquals("A-03 机柜温度超标排查工单", pageVo.getRecords().get(0).getTitle());
        assertEquals("李工", pageVo.getRecords().get(0).getOperatorName());
        assertEquals("排障中", pageVo.getRecords().get(0).getStatusText());
    }
}
