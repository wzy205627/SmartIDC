package com.smartidc.biz.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartidc.biz.domain.entity.IdcAlarmEvent;
import com.smartidc.biz.domain.entity.IdcDevice;
import com.smartidc.biz.domain.entity.IdcRack;
import com.smartidc.biz.domain.entity.IdcTelemetrySnapshot;
import com.smartidc.biz.domain.entity.IdcWorkTicket;
import com.smartidc.biz.domain.vo.mobile.MobileActiveAlarmVO;
import com.smartidc.biz.domain.vo.mobile.MobileRackProfileVO;
import com.smartidc.biz.domain.vo.mobile.MobileRackProfileVO.*;
import com.smartidc.biz.domain.vo.mobile.MobileRackTelemetryVO;
import com.smartidc.biz.domain.vo.mobile.MobileWorkTicketPageVO;
import com.smartidc.biz.domain.vo.mobile.MobileWorkTicketPageVO.MobileWorkTicketItemVO;
import com.smartidc.biz.mapper.IdcAlarmEventMapper;
import com.smartidc.biz.mapper.IdcDeviceMapper;
import com.smartidc.biz.mapper.IdcRackMapper;
import com.smartidc.biz.mapper.IdcTelemetrySnapshotMapper;
import com.smartidc.biz.mapper.IdcWorkTicketMapper;
import com.smartidc.biz.service.MobileRackService;
import com.smartidc.common.exception.ServiceException;
import com.smartidc.framework.security.UserContext;
import com.smartidc.framework.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 移动随行端机柜微画像、动环直读与工单分页服务实现
 */
@Service
public class MobileRackServiceImpl implements MobileRackService {

    private static final Logger log = LoggerFactory.getLogger(MobileRackServiceImpl.class);

    private static final String REDIS_PREFIX_TELEMETRY = "smartidc:telemetry:latest:";

    private final IdcRackMapper idcRackMapper;
    private final IdcDeviceMapper idcDeviceMapper;
    private final IdcAlarmEventMapper idcAlarmEventMapper;
    private final IdcWorkTicketMapper idcWorkTicketMapper;
    private final IdcTelemetrySnapshotMapper idcTelemetrySnapshotMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public MobileRackServiceImpl(
            IdcRackMapper idcRackMapper,
            IdcDeviceMapper idcDeviceMapper,
            IdcAlarmEventMapper idcAlarmEventMapper,
            IdcWorkTicketMapper idcWorkTicketMapper,
            IdcTelemetrySnapshotMapper idcTelemetrySnapshotMapper,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper) {
        this.idcRackMapper = idcRackMapper;
        this.idcDeviceMapper = idcDeviceMapper;
        this.idcAlarmEventMapper = idcAlarmEventMapper;
        this.idcWorkTicketMapper = idcWorkTicketMapper;
        this.idcTelemetrySnapshotMapper = idcTelemetrySnapshotMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public MobileRackProfileVO getRackProfile(String rackCode) {
        IdcRack rack = resolveRack(rackCode);
        checkBolaPermission(rack);

        // 1. 动环实时指标 (含离线哨兵兜底)
        MobileRackTelemetryVO telemetry = getLatestTelemetryVo(rack);

        // 2. 42U 设备摘要聚合
        List<IdcDevice> devices = idcDeviceMapper.selectListByRackIdIgnoreTenant(rack.getRackId());
        int totalU = rack.getTotalU() != null ? rack.getTotalU() : 42;
        int usedU = 0;
        List<MobileSlotDeviceItemVO> deviceItems = new ArrayList<>();
        if (devices != null) {
            for (IdcDevice dev : devices) {
                int uHeight = dev.getUHeight() != null ? dev.getUHeight() : 1;
                usedU += uHeight;
                deviceItems.add(new MobileSlotDeviceItemVO(
                        dev.getDeviceId(),
                        dev.getIotDeviceKey() != null ? dev.getIotDeviceKey() : ("DEV-" + dev.getDeviceId()),
                        dev.getDeviceName(),
                        dev.getDeviceType(),
                        dev.getStartU() != null ? dev.getStartU() : 1,
                        uHeight,
                        dev.getStatus() != null ? String.valueOf(dev.getStatus()) : "1"
                ));
            }
        }
        int freeU = Math.max(0, totalU - usedU);
        BigDecimal loadRatio = BigDecimal.valueOf(Math.min(100.0, ((double) usedU / totalU) * 100.0))
                .setScale(1, RoundingMode.HALF_UP);

        RackBaseInfoVO baseInfo = new RackBaseInfoVO(
                rack.getRackId(),
                rack.getRackCode(),
                rack.getRoomName() + " " + rack.getRackCode() + "机柜",
                rack.getRoomName(),
                rack.getTenantId(),
                rack.getPowerRating(),
                loadRatio,
                rack.getStatus()
        );

        RackSlotSummaryVO uSlotsSummary = new RackSlotSummaryVO(totalU, usedU, freeU, deviceItems);

        // 3. 当前活动告警 (红灯先亮)
        List<IdcAlarmEvent> alarms = idcAlarmEventMapper.selectActiveAlarmsByRackId(rack.getRackId());
        List<MobileActiveAlarmVO> alarmVos = new ArrayList<>();
        if (alarms != null) {
            for (IdcAlarmEvent alarm : alarms) {
                alarmVos.add(new MobileActiveAlarmVO(
                        alarm.getAlarmId(),
                        alarm.getAlarmLevel(),
                        alarm.getAlarmType(),
                        alarm.getMetricValue(),
                        alarm.getRcaSummary() != null ? alarm.getRcaSummary() : "动环越限告警触发中",
                        alarm.getTriggerTime()
                ));
            }
        }

        if (!alarmVos.isEmpty() && "ONLINE".equals(telemetry.getStatus())) {
            telemetry.setHealthLevel("CRITICAL");
        }

        return new MobileRackProfileVO(baseInfo, telemetry, uSlotsSummary, alarmVos);
    }

    @Override
    public MobileRackTelemetryVO getLatestTelemetry(String rackCode) {
        IdcRack rack = resolveRack(rackCode);
        checkBolaPermission(rack);
        return getLatestTelemetryVo(rack);
    }

    @Override
    public MobileWorkTicketPageVO getRackTickets(String rackCode, int page, int size) {
        IdcRack rack = resolveRack(rackCode);
        checkBolaPermission(rack);

        int pageNum = Math.max(1, page);
        int pageSize = Math.max(1, Math.min(50, size));
        int offset = (pageNum - 1) * pageSize;

        long total = idcWorkTicketMapper.countTicketsByRack(rack.getRackId(), rack.getRackCode());
        List<IdcWorkTicket> list = idcWorkTicketMapper.selectTicketsByRackWithPage(rack.getRackId(), rack.getRackCode(), offset, pageSize);

        List<MobileWorkTicketItemVO> records = new ArrayList<>();
        if (list != null) {
            for (IdcWorkTicket t : list) {
                records.add(new MobileWorkTicketItemVO(
                        t.getTicketId(),
                        t.getTitle(),
                        t.getTicketType(),
                        t.getStatus(),
                        formatTicketStatus(t.getStatus()),
                        t.getOperatorName() != null ? t.getOperatorName() : "待分配",
                        t.getCreateTime()
                ));
            }
        }

        return new MobileWorkTicketPageVO(total, records);
    }

    /**
     * BOLA (Broken Object Level Authorization) 跨租户越权安全防护校验
     */
    private void checkBolaPermission(IdcRack rack) {
        UserContext.LoginUser loginUser = UserContext.getUser();
        String userTenantId = TenantContext.getTenantId();
        String roleKey = loginUser != null ? loginUser.getRoleKey() : "engineer";

        // 白名单放行：平台自营中心 (000000) 或 管理员/值班主管具备全网视角
        boolean isSuperUser = "000000".equals(userTenantId)
                || "admin".equalsIgnoreCase(roleKey)
                || "supervisor".equalsIgnoreCase(roleKey);

        if (!isSuperUser) {
            // 普通租户运维工程师：严格只能查看归属于本租户托管的机柜
            if (!rack.getTenantId().equals(userTenantId)) {
                log.warn("[BOLA拦截] 跨租户非法访问机柜: 请求租户={}, 资产租户={}, rackCode={}",
                        userTenantId, rack.getTenantId(), rack.getRackCode());
                throw new ServiceException(403, "无权查看非本租户所属机柜资产");
            }
        }
    }

    /**
     * 动环热数据直读与离线哨兵兜底 (坚决杜绝 500 NPE)
     */
    private MobileRackTelemetryVO getLatestTelemetryVo(IdcRack rack) {
        String code = rack.getRackCode();
        String rawJson = null;

        // 优先直读 Redis 热数据
        try {
            rawJson = stringRedisTemplate.opsForValue().get(REDIS_PREFIX_TELEMETRY + code);
            if (rawJson == null && !code.startsWith("RACK-")) {
                rawJson = stringRedisTemplate.opsForValue().get(REDIS_PREFIX_TELEMETRY + "RACK-" + code);
            }
        } catch (Exception e) {
            log.warn("[微画像动环] 读取 Redis 热数据异常, 降级至离线哨兵", e);
        }

        if (rawJson != null && !rawJson.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(rawJson);
                BigDecimal temp = getDecimal(node, "temperature", "temp");
                BigDecimal returnTemp = getDecimal(node, "returnTemp", "return_temp");
                if (returnTemp == null && temp != null) {
                    returnTemp = temp.add(BigDecimal.valueOf(4.5)).setScale(1, RoundingMode.HALF_UP);
                }
                BigDecimal humidity = getDecimal(node, "humidity", "hum");
                BigDecimal voltage = getDecimal(node, "voltage", "volt");
                BigDecimal current = getDecimal(node, "currentAmp", "current");
                BigDecimal powerKw = getDecimal(node, "powerKw", "power");

                LocalDateTime updatedAt = LocalDateTime.now();
                if (node.has("sampleTimestamp")) {
                    long ts = node.get("sampleTimestamp").asLong();
                    if (ts > 0) {
                        updatedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault());
                    }
                }

                String health = "HEALTHY";
                if (temp != null) {
                    if (temp.compareTo(BigDecimal.valueOf(32.0)) >= 0) {
                        health = "CRITICAL";
                    } else if (temp.compareTo(BigDecimal.valueOf(28.0)) >= 0) {
                        health = "WARNING";
                    }
                }

                return new MobileRackTelemetryVO(
                        "ONLINE",
                        temp,
                        returnTemp,
                        humidity,
                        voltage,
                        current,
                        powerKw,
                        health,
                        updatedAt
                );
            } catch (Exception e) {
                log.warn("[微画像动环] 解析 Redis 遥测 JSON 异常: {}", rawJson, e);
            }
        }

        // Redis 离线哨兵兜底：追溯历史快照最近一条心跳时间
        LocalDateTime lastHeartbeat = null;
        try {
            IdcTelemetrySnapshot snapshot = idcTelemetrySnapshotMapper.selectLatestSnapshotByRackId(rack.getRackId());
            if (snapshot != null) {
                lastHeartbeat = snapshot.getSampleTime();
            }
        } catch (Exception ignored) {}

        return MobileRackTelemetryVO.offlineFallback(lastHeartbeat);
    }

    private BigDecimal getDecimal(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            if (node.has(field) && !node.get(field).isNull()) {
                try {
                    return new BigDecimal(node.get(field).asText());
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /**
     * 机柜编码鲁棒容错查找
     */
    private IdcRack resolveRack(String rackCode) {
        if (rackCode == null || rackCode.isBlank()) {
            throw new ServiceException("机柜编号不可为空");
        }
        String cleanCode = rackCode.trim().toUpperCase();

        IdcRack rack = idcRackMapper.selectOneByCodeIgnoreTenant(cleanCode);
        if (rack == null && cleanCode.startsWith("RACK-")) {
            String stripped = cleanCode.substring(5);
            rack = idcRackMapper.selectOneByCodeIgnoreTenant(stripped);
            if (rack == null && stripped.matches("^[A-Z]+[0-9]+$")) {
                String split = stripped.replaceAll("([A-Z]+)([0-9]+)", "$1-$2");
                rack = idcRackMapper.selectOneByCodeIgnoreTenant(split);
            }
        }
        if (rack == null && !cleanCode.startsWith("RACK-")) {
            rack = idcRackMapper.selectOneByCodeIgnoreTenant("RACK-" + cleanCode);
        }
        if (rack == null && cleanCode.contains("_")) {
            rack = idcRackMapper.selectOneByCodeIgnoreTenant(cleanCode.replace('_', '-'));
        }
        if (rack == null && cleanCode.matches("^[A-Z]+[0-9]+$")) {
            String split = cleanCode.replaceAll("([A-Z]+)([0-9]+)", "$1-$2");
            rack = idcRackMapper.selectOneByCodeIgnoreTenant(split);
        }

        if (rack == null) {
            throw new ServiceException("机柜资产不存在: " + rackCode);
        }
        return rack;
    }

    private String formatTicketStatus(Integer status) {
        if (status == null) return "未知状态";
        return switch (status) {
            case 0 -> "待指派";
            case 1 -> "已派单";
            case 2 -> "排障中";
            case 3 -> "审批挂起";
            case 4 -> "已批准";
            case 5 -> "已驳回";
            case 6 -> "待消警复核";
            case 7 -> "已办结";
            default -> "流转中";
        };
    }
}
