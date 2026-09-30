package com.smartidc.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartidc.biz.domain.dto.CreateWorkTicketDTO;
import com.smartidc.biz.domain.dto.WorkTicketQueryDTO;
import com.smartidc.biz.domain.entity.IdcAlarmEvent;
import com.smartidc.biz.domain.entity.IdcRack;
import com.smartidc.biz.domain.entity.IdcWorkTicket;
import com.smartidc.biz.domain.vo.WorkTicketDetailVO;
import com.smartidc.biz.domain.vo.WorkTicketStatsVO;
import com.smartidc.biz.mapper.IdcAlarmEventMapper;
import com.smartidc.biz.mapper.IdcRackMapper;
import com.smartidc.biz.mapper.IdcWorkTicketMapper;
import com.smartidc.biz.service.IdcAlarmEventService;
import com.smartidc.biz.service.IdcWorkTicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 运维排障工单流转服务实现类
 */
@Service
public class IdcWorkTicketServiceImpl extends ServiceImpl<IdcWorkTicketMapper, IdcWorkTicket>
        implements IdcWorkTicketService {

    private static final Logger log = LoggerFactory.getLogger(IdcWorkTicketServiceImpl.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IdcWorkTicketMapper ticketMapper;
    private final IdcAlarmEventMapper alarmEventMapper;
    private final IdcRackMapper rackMapper;
    private final IdcAlarmEventService alarmEventService;

    public IdcWorkTicketServiceImpl(IdcWorkTicketMapper ticketMapper,
                                    IdcAlarmEventMapper alarmEventMapper,
                                    IdcRackMapper rackMapper,
                                    IdcAlarmEventService alarmEventService) {
        this.ticketMapper = ticketMapper;
        this.alarmEventMapper = alarmEventMapper;
        this.rackMapper = rackMapper;
        this.alarmEventService = alarmEventService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkTicketDetailVO createTicket(CreateWorkTicketDTO dto) {
        IdcWorkTicket ticket = new IdcWorkTicket();
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int rand = ThreadLocalRandom.current().nextInt(1000, 9999);
        ticket.setTicketNo("TK-" + dateStr + "-" + rand);

        ticket.setTitle(dto.getTitle() != null && !dto.getTitle().isBlank()
                ? dto.getTitle().trim()
                : "[排障工单] " + (dto.getRackCode() != null ? dto.getRackCode() : "IDC") + " 动环异常");

        ticket.setTicketType(dto.getTicketType() != null && !dto.getTicketType().isBlank()
                ? dto.getTicketType().trim()
                : "ALARM_REPAIR");

        ticket.setTenantId(dto.getTenantId() != null && !dto.getTenantId().isBlank()
                ? dto.getTenantId().trim()
                : "000000");

        ticket.setRackCode(dto.getRackCode() != null ? dto.getRackCode().trim() : null);
        ticket.setAlarmId(dto.getAlarmId());
        ticket.setSopGuide(dto.getSopGuide());

        // 解析机柜ID
        if (ticket.getRackCode() != null) {
            IdcRack rack = rackMapper.selectOne(
                    new LambdaQueryWrapper<IdcRack>().eq(IdcRack::getRackCode, ticket.getRackCode()).last("LIMIT 1")
            );
            if (rack != null) {
                ticket.setRackId(rack.getRackId());
                if ("000000".equals(ticket.getTenantId()) && rack.getTenantId() != null) {
                    ticket.setTenantId(rack.getTenantId());
                }
            }
        }

        // 指派工程师
        if (dto.getOperatorName() != null && !dto.getOperatorName().isBlank()) {
            ticket.setOperatorName(dto.getOperatorName().trim());
            ticket.setStatus(1); // 1-已指派
        } else {
            ticket.setStatus(0); // 0-待分配
        }

        LocalDateTime now = LocalDateTime.now();
        ticket.setCreateTime(now);
        ticket.setUpdateTime(now);

        ticketMapper.insert(ticket);
        log.info("[WorkTicket] 成功创建排障工单: ticketId={}, ticketNo={}, rackCode={}, alarmId={}",
                ticket.getTicketId(), ticket.getTicketNo(), ticket.getRackCode(), ticket.getAlarmId());

        // 若关联了活动告警，将该告警标记为“已派单 (status=2)”
        if (dto.getAlarmId() != null && dto.getAlarmId() > 0) {
            try {
                IdcAlarmEvent alarm = alarmEventMapper.selectById(dto.getAlarmId());
                if (alarm != null && alarm.getStatus() != null && alarm.getStatus() == 1) {
                    alarm.setStatus(2); // 2-已派单处理
                    alarmEventMapper.updateById(alarm);
                    log.info("[WorkTicket] 已联动将告警 #{} 状态更新为 2 (已派单)", alarm.getAlarmId());
                }
            } catch (Exception e) {
                log.warn("[WorkTicket] 联动更新告警状态异常: {}", e.getMessage());
            }
        }

        return convertToVO(ticket);
    }

    @Override
    public Page<WorkTicketDetailVO> pageTickets(WorkTicketQueryDTO query) {
        Page<IdcWorkTicket> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<IdcWorkTicket> wrapper = new LambdaQueryWrapper<>();

        if (query.getStatus() != null) {
            wrapper.eq(IdcWorkTicket::getStatus, query.getStatus());
        }
        if (query.getTicketType() != null && !query.getTicketType().isBlank()) {
            wrapper.eq(IdcWorkTicket::getTicketType, query.getTicketType().trim());
        }
        if (query.getRackCode() != null && !query.getRackCode().isBlank()) {
            wrapper.eq(IdcWorkTicket::getRackCode, query.getRackCode().trim());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(IdcWorkTicket::getTicketNo, kw).or().like(IdcWorkTicket::getTitle, kw));
        }
        if (query.getTenantId() != null && !query.getTenantId().isBlank()) {
            wrapper.eq(IdcWorkTicket::getTenantId, query.getTenantId().trim());
        }

        wrapper.orderByDesc(IdcWorkTicket::getCreateTime);
        Page<IdcWorkTicket> result = ticketMapper.selectPage(page, wrapper);

        Page<WorkTicketDetailVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<WorkTicketDetailVO> voList = result.getRecords().stream().map(this::convertToVO).toList();
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public WorkTicketDetailVO getTicketDetail(Long ticketId) {
        if (ticketId == null) return null;
        IdcWorkTicket ticket = ticketMapper.selectById(ticketId);
        return convertToVO(ticket);
    }

    @Override
    public WorkTicketDetailVO assignTicket(Long ticketId, Long operatorId, String operatorName) {
        IdcWorkTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("未找到工单 #" + ticketId);
        }
        ticket.setOperatorId(operatorId);
        ticket.setOperatorName(operatorName);
        ticket.setStatus(1); // 已指派
        ticket.setUpdateTime(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        log.info("[WorkTicket] 工单 #{} 已指派给: {}", ticketId, operatorName);
        return convertToVO(ticket);
    }

    @Override
    public WorkTicketDetailVO startProcessing(Long ticketId) {
        IdcWorkTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("未找到工单 #" + ticketId);
        }
        ticket.setStatus(2); // 排障中
        ticket.setUpdateTime(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        log.info("[WorkTicket] 工单 #{} 工程师已接单开工", ticketId);
        return convertToVO(ticket);
    }

    @Override
    public WorkTicketDetailVO resolveTicket(Long ticketId, String processNotes) {
        IdcWorkTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("未找到工单 #" + ticketId);
        }
        ticket.setProcessNotes(processNotes);
        ticket.setStatus(6); // 待复核
        ticket.setUpdateTime(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        log.info("[WorkTicket] 工单 #{} 提交处置记录，进入待复核状态", ticketId);
        return convertToVO(ticket);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkTicketDetailVO completeTicket(Long ticketId, Boolean closeAlarm) {
        IdcWorkTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("未找到工单 #" + ticketId);
        }
        LocalDateTime now = LocalDateTime.now();
        ticket.setStatus(7); // 已办结
        ticket.setFinishTime(now);
        ticket.setUpdateTime(now);
        ticketMapper.updateById(ticket);
        log.info("[WorkTicket] 工单 #{} 已复核结案归档", ticketId);

        // 联动消除关联告警
        if (Boolean.TRUE.equals(closeAlarm) && ticket.getAlarmId() != null && ticket.getAlarmId() > 0) {
            try {
                alarmEventService.closeAlarm(ticket.getAlarmId());
                log.info("[WorkTicket] 工单结案，已自动联动消除关联告警 #{}", ticket.getAlarmId());
            } catch (Exception e) {
                log.warn("[WorkTicket] 联动消除告警异常: {}", e.getMessage());
            }
        }

        return convertToVO(ticket);
    }

    @Override
    public WorkTicketStatsVO getTicketStats() {
        WorkTicketStatsVO vo = new WorkTicketStatsVO();

        vo.setTotalCount(ticketMapper.selectCount(null));
        vo.setCreatedCount(ticketMapper.selectCount(new LambdaQueryWrapper<IdcWorkTicket>().eq(IdcWorkTicket::getStatus, 0)));
        vo.setAssignedCount(ticketMapper.selectCount(new LambdaQueryWrapper<IdcWorkTicket>().eq(IdcWorkTicket::getStatus, 1)));
        vo.setProcessingCount(ticketMapper.selectCount(new LambdaQueryWrapper<IdcWorkTicket>().eq(IdcWorkTicket::getStatus, 2)));
        vo.setResolvedCount(ticketMapper.selectCount(new LambdaQueryWrapper<IdcWorkTicket>().eq(IdcWorkTicket::getStatus, 6)));
        vo.setCompletedCount(ticketMapper.selectCount(new LambdaQueryWrapper<IdcWorkTicket>().eq(IdcWorkTicket::getStatus, 7)));

        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        vo.setTodayCount(ticketMapper.selectCount(
                new LambdaQueryWrapper<IdcWorkTicket>().ge(IdcWorkTicket::getCreateTime, todayStart)
        ));

        return vo;
    }

    private WorkTicketDetailVO convertToVO(IdcWorkTicket ticket) {
        if (ticket == null) return null;
        WorkTicketDetailVO vo = new WorkTicketDetailVO();
        vo.setTicketId(ticket.getTicketId());
        vo.setTicketNo(ticket.getTicketNo());
        vo.setTenantId(ticket.getTenantId());
        vo.setAlarmId(ticket.getAlarmId());
        vo.setRackId(ticket.getRackId());
        vo.setRackCode(ticket.getRackCode());
        vo.setTitle(ticket.getTitle());
        vo.setTicketType(ticket.getTicketType());
        vo.setStatus(ticket.getStatus());
        vo.setStatusLabel(getStatusLabel(ticket.getStatus()));
        vo.setOperatorId(ticket.getOperatorId());
        vo.setOperatorName(ticket.getOperatorName());
        vo.setApproverId(ticket.getApproverId());
        vo.setApproverName(ticket.getApproverName());
        vo.setCheckpointId(ticket.getCheckpointId());
        vo.setSopGuide(ticket.getSopGuide());
        vo.setProcessNotes(ticket.getProcessNotes());

        if (ticket.getCreateTime() != null) {
            vo.setCreateTime(ticket.getCreateTime().format(DATE_TIME_FORMATTER));
        }
        if (ticket.getUpdateTime() != null) {
            vo.setUpdateTime(ticket.getUpdateTime().format(DATE_TIME_FORMATTER));
        }
        if (ticket.getFinishTime() != null) {
            vo.setFinishTime(ticket.getFinishTime().format(DATE_TIME_FORMATTER));
        }

        // 丰富机柜与告警详情
        if (ticket.getRackCode() != null) {
            IdcRack rack = rackMapper.selectOne(
                    new LambdaQueryWrapper<IdcRack>().eq(IdcRack::getRackCode, ticket.getRackCode()).last("LIMIT 1")
            );
            if (rack != null) {
                vo.setRoomName(rack.getRoomName());
            }
        }

        if (ticket.getAlarmId() != null && ticket.getAlarmId() > 0) {
            IdcAlarmEvent alarm = alarmEventMapper.selectById(ticket.getAlarmId());
            if (alarm != null) {
                vo.setAlarmLevel(alarm.getAlarmLevel());
                vo.setAlarmType(alarm.getAlarmType());
                vo.setAlarmMetricValue(alarm.getMetricValue());
            }
        }

        return vo;
    }

    private String getStatusLabel(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待分配";
            case 1 -> "已指派";
            case 2 -> "排障中";
            case 3 -> "挂起待审批";
            case 4 -> "已批准";
            case 5 -> "已拒绝";
            case 6 -> "待复核";
            case 7 -> "已办结";
            default -> "流转中";
        };
    }
}
