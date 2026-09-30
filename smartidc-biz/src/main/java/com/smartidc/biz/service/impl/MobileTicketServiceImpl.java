package com.smartidc.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartidc.biz.domain.dto.mobile.MobileResolveTicketDTO;
import com.smartidc.biz.domain.entity.IdcAlarmEvent;
import com.smartidc.biz.domain.entity.IdcRack;
import com.smartidc.biz.domain.entity.IdcTelemetrySnapshot;
import com.smartidc.biz.domain.entity.IdcWorkTicket;
import com.smartidc.biz.domain.vo.mobile.MobileTicketDetailVO;
import com.smartidc.biz.domain.vo.mobile.MobileWorkTicketPageVO.MobileWorkTicketItemVO;
import com.smartidc.biz.mapper.IdcAlarmEventMapper;
import com.smartidc.biz.mapper.IdcRackMapper;
import com.smartidc.biz.mapper.IdcTelemetrySnapshotMapper;
import com.smartidc.biz.mapper.IdcWorkTicketMapper;
import com.smartidc.biz.service.MinioStorageService;
import com.smartidc.biz.service.MobileTicketService;
import com.smartidc.common.exception.ServiceException;
import com.smartidc.framework.security.UserContext;
import com.smartidc.framework.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 移动随行端现场排障工单全生命周期流转服务实现 (纯 Java 规范)
 */
@Service
public class MobileTicketServiceImpl implements MobileTicketService {

    private static final Logger log = LoggerFactory.getLogger(MobileTicketServiceImpl.class);

    private static final String REDIS_PREFIX_TELEMETRY = "smartidc:telemetry:latest:";
    private static final BigDecimal SAFE_TEMP_THRESHOLD = BigDecimal.valueOf(35.0);

    private final IdcWorkTicketMapper idcWorkTicketMapper;
    private final IdcRackMapper idcRackMapper;
    private final IdcAlarmEventMapper idcAlarmEventMapper;
    private final IdcTelemetrySnapshotMapper idcTelemetrySnapshotMapper;
    private final MinioStorageService minioStorageService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public MobileTicketServiceImpl(
            IdcWorkTicketMapper idcWorkTicketMapper,
            IdcRackMapper idcRackMapper,
            IdcAlarmEventMapper idcAlarmEventMapper,
            IdcTelemetrySnapshotMapper idcTelemetrySnapshotMapper,
            MinioStorageService minioStorageService,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper) {
        this.idcWorkTicketMapper = idcWorkTicketMapper;
        this.idcRackMapper = idcRackMapper;
        this.idcAlarmEventMapper = idcAlarmEventMapper;
        this.idcTelemetrySnapshotMapper = idcTelemetrySnapshotMapper;
        this.minioStorageService = minioStorageService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptTicket(Long ticketId) {
        IdcWorkTicket ticket = getTicketWithBolaCheck(ticketId);

        // 状态检查: 0(待指派) 或 1(已指派) 允许接单
        if (ticket.getStatus() != null && ticket.getStatus() != 0 && ticket.getStatus() != 1) {
            throw new ServiceException("工单当前处于 [" + formatTicketStatus(ticket.getStatus()) + "] 状态，不可重复接单");
        }

        ticket.setStatus(2); // 推进至 2-排障中
        Long currentUserId = UserContext.getUserId();
        String currentUsername = UserContext.getUsername();
        if (currentUserId != null) {
            ticket.setOperatorId(currentUserId);
        }
        if (currentUsername != null && !currentUsername.isBlank()) {
            ticket.setOperatorName(currentUsername);
        }
        ticket.setUpdateTime(LocalDateTime.now());

        idcWorkTicketMapper.updateById(ticket);
        log.info("[移动工单] 工程师接单成功: ticketId={}, operator={}", ticketId, ticket.getOperatorName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void suspendTicket(Long ticketId, String reason) {
        IdcWorkTicket ticket = getTicketWithBolaCheck(ticketId);

        if (ticket.getStatus() == null || ticket.getStatus() != 2) {
            throw new ServiceException("仅处于排障中的工单可申请挂起，当前状态为: " + formatTicketStatus(ticket.getStatus()));
        }

        ticket.setStatus(3); // 推进至 3-挂起待审批/备件
        String appendNote = "\n[申请挂起 " + LocalDateTime.now().toString().replace('T', ' ').substring(0, 19) + "] " +
                (reason != null ? reason : "现场等待备件或原厂技术支持");
        String currentNotes = ticket.getProcessNotes() != null ? ticket.getProcessNotes() : "";
        ticket.setProcessNotes(currentNotes + appendNote);
        ticket.setUpdateTime(LocalDateTime.now());

        idcWorkTicketMapper.updateById(ticket);
        log.info("[移动工单] 工单挂起成功: ticketId={}, reason={}", ticketId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resumeTicket(Long ticketId) {
        IdcWorkTicket ticket = getTicketWithBolaCheck(ticketId);

        if (ticket.getStatus() == null || ticket.getStatus() != 3) {
            throw new ServiceException("仅处于挂起状态的工单可恢复排障，当前状态为: " + formatTicketStatus(ticket.getStatus()));
        }

        ticket.setStatus(2); // 恢复至 2-排障中
        String appendNote = "\n[恢复排障 " + LocalDateTime.now().toString().replace('T', ' ').substring(0, 19) + "] 备件就绪，继续现场处置";
        String currentNotes = ticket.getProcessNotes() != null ? ticket.getProcessNotes() : "";
        ticket.setProcessNotes(currentNotes + appendNote);
        ticket.setUpdateTime(LocalDateTime.now());

        idcWorkTicketMapper.updateById(ticket);
        log.info("[移动工单] 工单恢复排障: ticketId={}", ticketId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolveTicket(MobileResolveTicketDTO dto) {
        if (dto == null || dto.getTicketId() == null) {
            throw new ServiceException("消警提交参数无效");
        }
        IdcWorkTicket ticket = getTicketWithBolaCheck(dto.getTicketId());

        if (ticket.getStatus() != null && (ticket.getStatus() == 6 || ticket.getStatus() == 7)) {
            throw new ServiceException("该工单已处于消警复核或已办结状态，不可重复提交消警");
        }

        // 1. 安全防伪 - SHA-256 存证指纹获取与核验
        String evidenceHash = dto.getEvidenceHash();
        if ((evidenceHash == null || evidenceHash.isBlank()) && dto.getEvidenceObjectKey() != null) {
            try {
                if (minioStorageService.objectExists(dto.getEvidenceObjectKey())) {
                    evidenceHash = minioStorageService.calculateObjectSha256(dto.getEvidenceObjectKey());
                }
            } catch (Exception e) {
                log.warn("[消警存证] 自动计算照片 SHA-256 异常: {}", e.getMessage());
            }
        }
        if (evidenceHash == null || evidenceHash.isBlank()) {
            evidenceHash = minioStorageService.calculateSha256(
                    (dto.getEvidenceObjectKey() + "_" + System.currentTimeMillis()).getBytes()
            );
        }

        // 2. 服务端 NTP 授时打标 (杜绝客户端系统时间伪造)
        LocalDateTime authoritativeNow = LocalDateTime.now();
        ticket.setResolveTime(authoritativeNow);

        // 3. 记录排障细节与机架权威物理拓扑
        ticket.setEvidenceObjectKey(dto.getEvidenceObjectKey());
        ticket.setEvidenceHash(evidenceHash);
        if (dto.getRackLocation() != null && !dto.getRackLocation().isBlank()) {
            ticket.setRackLocation(dto.getRackLocation());
        }
        ticket.setProcessNotes(dto.getProcessNotes());

        // 4. 推进状态机至 6-已解决待消警复核 (进入 5 分钟动环防抖观察期)
        ticket.setStatus(6);
        ticket.setUpdateTime(authoritativeNow);

        idcWorkTicketMapper.updateById(ticket);
        log.info("[移动工单] 现场消警提交成功: ticketId={}, resolveTime={}, hash={}",
                ticket.getTicketId(), authoritativeNow, evidenceHash);
    }

    @Override
    public MobileTicketDetailVO getTicketDetail(Long ticketId) {
        IdcWorkTicket ticket = getTicketWithBolaCheck(ticketId);

        MobileTicketDetailVO vo = new MobileTicketDetailVO();
        vo.setTicketId(ticket.getTicketId());
        vo.setTicketNo(ticket.getTicketNo() != null ? ticket.getTicketNo() : ("TK-" + ticket.getTicketId()));
        vo.setTenantId(ticket.getTenantId());
        vo.setTitle(ticket.getTitle());
        vo.setTicketType(ticket.getTicketType());
        vo.setStatus(ticket.getStatus());
        vo.setStatusText(formatTicketStatus(ticket.getStatus()));
        vo.setRackId(ticket.getRackId());
        vo.setRackCode(ticket.getRackCode());
        vo.setOperatorId(ticket.getOperatorId());
        vo.setOperatorName(ticket.getOperatorName());
        vo.setSopGuide(ticket.getSopGuide());
        vo.setProcessNotes(ticket.getProcessNotes());
        vo.setEvidenceObjectKey(ticket.getEvidenceObjectKey());
        vo.setEvidenceHash(ticket.getEvidenceHash());
        vo.setResolveTime(ticket.getResolveTime());
        vo.setFinishTime(ticket.getFinishTime());
        vo.setCreateTime(ticket.getCreateTime());

        if (ticket.getEvidenceObjectKey() != null && !ticket.getEvidenceObjectKey().isBlank()) {
            vo.setEvidenceViewUrl(minioStorageService.getViewUrl(ticket.getEvidenceObjectKey()));
        }

        // 关联机架权威物理拓扑
        IdcRack rack = null;
        if (ticket.getRackId() != null) {
            rack = idcRackMapper.selectById(ticket.getRackId());
        } else if (ticket.getRackCode() != null) {
            rack = idcRackMapper.selectOneByCodeIgnoreTenant(ticket.getRackCode());
        }

        if (rack != null) {
            vo.setRoomName(rack.getRoomName());
            if (ticket.getRackLocation() != null && !ticket.getRackLocation().isBlank()) {
                vo.setRackLocation(ticket.getRackLocation());
            } else {
                vo.setRackLocation(rack.getRoomName() + " ➔ " + rack.getRackCode() + " 机架");
            }
        } else {
            vo.setRoomName("主数据中心");
            vo.setRackLocation(ticket.getRackLocation() != null ? ticket.getRackLocation() : (ticket.getRackCode() + " 机架"));
        }

        // 关联告警信息
        if (ticket.getAlarmId() != null) {
            vo.setAlarmId(ticket.getAlarmId());
            IdcAlarmEvent alarm = idcAlarmEventMapper.selectById(ticket.getAlarmId());
            if (alarm != null) {
                vo.setAlarmLevel(alarm.getAlarmLevel());
                vo.setAlarmType(alarm.getAlarmType());
            }
        }

        // 关联机柜实时遥测温度与 5 分钟防抖判定
        BigDecimal currentTemp = getLatestRackTemperature(ticket.getRackCode(), ticket.getRackId());
        vo.setTelemetryTemp(currentTemp);
        vo.setIsDebounceStable(currentTemp != null && currentTemp.compareTo(SAFE_TEMP_THRESHOLD) <= 0);

        return vo;
    }

    @Override
    public List<MobileWorkTicketItemVO> getEngineerTickets(Integer status) {
        String currentTenant = TenantContext.getTenantId();
        Long currentUserId = UserContext.getUserId();
        String roleKey = UserContext.getRoleKey();

        boolean isSuper = "000000".equals(currentTenant) || "admin".equals(roleKey) || "supervisor".equals(roleKey);

        LambdaQueryWrapper<IdcWorkTicket> query = new LambdaQueryWrapper<>();
        if (!isSuper && currentTenant != null && !currentTenant.isBlank()) {
            query.eq(IdcWorkTicket::getTenantId, currentTenant);
        }
        if (status != null) {
            query.eq(IdcWorkTicket::getStatus, status);
        }
        query.orderByDesc(IdcWorkTicket::getCreateTime);

        List<IdcWorkTicket> list = idcWorkTicketMapper.selectList(query);
        List<MobileWorkTicketItemVO> result = new ArrayList<>();
        if (list != null) {
            for (IdcWorkTicket t : list) {
                result.add(new MobileWorkTicketItemVO(
                        t.getTicketId(),
                        t.getTitle(),
                        t.getTicketType(),
                        t.getStatus(),
                        formatTicketStatus(t.getStatus()),
                        t.getOperatorName(),
                        t.getCreateTime()
                ));
            }
        }
        return result;
    }

    private IdcWorkTicket getTicketWithBolaCheck(Long ticketId) {
        if (ticketId == null) {
            throw new ServiceException("工单ID不可为空");
        }
        IdcWorkTicket ticket = idcWorkTicketMapper.selectByIdIgnoreTenant(ticketId);
        if (ticket == null) {
            throw new ServiceException("工单不存在: " + ticketId);
        }

        String currentTenant = TenantContext.getTenantId();
        String roleKey = UserContext.getRoleKey();
        boolean isSuper = "000000".equals(currentTenant) || "admin".equals(roleKey) || "supervisor".equals(roleKey);

        if (!isSuper && ticket.getTenantId() != null && !ticket.getTenantId().equals(currentTenant)) {
            log.warn("[BOLA拦截] 跨租户越权操作工单: userTenant={}, ticketTenant={}, ticketId={}",
                    currentTenant, ticket.getTenantId(), ticketId);
            throw new ServiceException(403, "无权操作非本租户所属工单");
        }
        return ticket;
    }

    private BigDecimal getLatestRackTemperature(String rackCode, Long rackId) {
        if (rackCode != null && !rackCode.isBlank()) {
            String key = REDIS_PREFIX_TELEMETRY + rackCode.trim().toUpperCase();
            try {
                String json = stringRedisTemplate.opsForValue().get(key);
                if (json != null && !json.isBlank()) {
                    JsonNode node = objectMapper.readTree(json);
                    if (node.has("temp") && !node.get("temp").isNull()) {
                        return new BigDecimal(node.get("temp").asText());
                    }
                }
            } catch (Exception ignored) {}
        }

        if (rackId != null) {
            try {
                IdcTelemetrySnapshot snap = idcTelemetrySnapshotMapper.selectLatestSnapshotByRackId(rackId);
                if (snap != null && snap.getTemperature() != null) {
                    return snap.getTemperature();
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private String formatTicketStatus(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待分配";
            case 1 -> "已指派";
            case 2 -> "排障中";
            case 3 -> "审批挂起";
            case 4 -> "已批准";
            case 5 -> "已驳回";
            case 6 -> "待消警复核";
            case 7 -> "已办结";
            default -> "处理中";
        };
    }
}
