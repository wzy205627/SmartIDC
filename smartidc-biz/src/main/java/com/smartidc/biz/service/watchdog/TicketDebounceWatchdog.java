package com.smartidc.biz.service.watchdog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartidc.biz.domain.entity.IdcAlarmEvent;
import com.smartidc.biz.domain.entity.IdcTelemetrySnapshot;
import com.smartidc.biz.domain.entity.IdcWorkTicket;
import com.smartidc.biz.mapper.IdcAlarmEventMapper;
import com.smartidc.biz.mapper.IdcTelemetrySnapshotMapper;
import com.smartidc.biz.mapper.IdcWorkTicketMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 动环温度滞后防抖自动归档与 30 分钟超时逃生守护任务 (对标 implementation_plan5.3.md 任务 5)
 */
@Component
public class TicketDebounceWatchdog {

    private static final Logger log = LoggerFactory.getLogger(TicketDebounceWatchdog.class);

    public static final BigDecimal SAFE_TEMP_THRESHOLD = BigDecimal.valueOf(35.0);
    private static final String REDIS_PREFIX_TELEMETRY = "smartidc:telemetry:latest:";

    private final IdcWorkTicketMapper idcWorkTicketMapper;
    private final IdcAlarmEventMapper idcAlarmEventMapper;
    private final IdcTelemetrySnapshotMapper idcTelemetrySnapshotMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public TicketDebounceWatchdog(
            IdcWorkTicketMapper idcWorkTicketMapper,
            IdcAlarmEventMapper idcAlarmEventMapper,
            IdcTelemetrySnapshotMapper idcTelemetrySnapshotMapper,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper) {
        this.idcWorkTicketMapper = idcWorkTicketMapper;
        this.idcAlarmEventMapper = idcAlarmEventMapper;
        this.idcTelemetrySnapshotMapper = idcTelemetrySnapshotMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 每 1 分钟扫描所有处于 6(RESOLVED 已解决待消警复核) 的工单
     */
    @Scheduled(fixedDelay = 60000)
    public void executeDebounceCheck() {
        List<IdcWorkTicket> resolvedTickets = idcWorkTicketMapper.selectTicketsByStatusIgnoreTenant(6);
        if (resolvedTickets == null || resolvedTickets.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        log.info("[防抖守卫] 开始扫描待复核工单, 待评估工单数: {}", resolvedTickets.size());

        for (IdcWorkTicket ticket : resolvedTickets) {
            try {
                processSingleTicketDebounce(ticket, now);
            } catch (Exception e) {
                log.error("[防抖守卫] 评估工单 [{}] 防抖异常: {}", ticket.getTicketId(), e.getMessage(), e);
            }
        }
    }

    public void processSingleTicketDebounce(IdcWorkTicket ticket, LocalDateTime now) {
        LocalDateTime resolveTime = ticket.getResolveTime();
        if (resolveTime == null) {
            resolveTime = ticket.getUpdateTime() != null ? ticket.getUpdateTime() : ticket.getCreateTime();
        }

        long elapsedMinutes = Duration.between(resolveTime, now).toMinutes();
        BigDecimal currentTemp = getLatestTemperature(ticket.getRackCode(), ticket.getRackId());

        boolean isTempSafe = currentTemp != null && currentTemp.compareTo(SAFE_TEMP_THRESHOLD) <= 0;

        // 场景 A: 连续 5 分钟稳定在安全阈值以下 (如 <= 35.0℃) ➔ 推进为 7(已办结归档) 并消除活动告警
        if (isTempSafe) {
            if (elapsedMinutes >= 5) {
                ticket.setStatus(7); // 7-已办结
                ticket.setFinishTime(now);
                ticket.setUpdateTime(now);

                String note = "\n[防抖守卫自动归档 " + now.toString().replace('T', ' ').substring(0, 19) + "] " +
                        "机柜动环温度连续稳定在安全阈值以下 (" + currentTemp + "℃ <= " + SAFE_TEMP_THRESHOLD + "℃)，已自动办结归档并消除联动告警。";
                String currentNotes = ticket.getProcessNotes() != null ? ticket.getProcessNotes() : "";
                ticket.setProcessNotes(currentNotes + note);

                idcWorkTicketMapper.updateById(ticket);

                // 联动消除活动告警
                if (ticket.getAlarmId() != null) {
                    IdcAlarmEvent alarm = idcAlarmEventMapper.selectById(ticket.getAlarmId());
                    if (alarm != null && (alarm.getStatus() == 1 || alarm.getStatus() == 2)) {
                        alarm.setStatus(3); // 3-已消除
                        alarm.setClearTime(now);
                        idcAlarmEventMapper.updateById(alarm);
                        log.info("[防抖守卫] 联动消除告警成功: alarmId={}", alarm.getAlarmId());
                    }
                }
                log.info("[防抖守卫] 工单 [{}] 连续稳定 {} 分钟，成功自动办结归档 (COMPLETED)",
                        ticket.getTicketId(), elapsedMinutes);
            } else {
                log.debug("[防抖守卫] 工单 [{}] 动环已回温至安全区间 ({}℃)，等待 5 分钟防抖判定周期 [{}min/5min]",
                        ticket.getTicketId(), currentTemp, elapsedMinutes);
            }
            return;
        }

        // 场景 B: 超过 30 分钟仍未回稳 (持续越限或温度未上报) ➔ 触发 30 分钟超时逃生通道，打回至 2(排障中)
        if (elapsedMinutes >= 30) {
            ticket.setStatus(2); // 打回至 2-排障中
            ticket.setUpdateTime(now);

            String escapeNote = "\n[超时逃生通道触发 " + now.toString().replace('T', ' ').substring(0, 19) + "] " +
                    "消警申请已超过 30 分钟，但机柜温度 (" + (currentTemp != null ? currentTemp + "℃" : "无实时遥测") +
                    " > " + SAFE_TEMP_THRESHOLD + "℃) 仍未回稳，判定现场消警未见实效，已自动打回排障中并通知工程师复查。";
            String currentNotes = ticket.getProcessNotes() != null ? ticket.getProcessNotes() : "";
            ticket.setProcessNotes(currentNotes + escapeNote);

            idcWorkTicketMapper.updateById(ticket);
            log.warn("[防抖守卫] 工单 [{}] 超过 30 分钟未回稳，已触发逃生通道打回排障中: elapsedMinutes={}",
                    ticket.getTicketId(), elapsedMinutes);
        } else {
            log.debug("[防抖守卫] 工单 [{}] 温度持续越限 ({}℃ > 35℃)，观察期中 [{}min/30min]",
                    ticket.getTicketId(), currentTemp, elapsedMinutes);
        }
    }

    private BigDecimal getLatestTemperature(String rackCode, Long rackId) {
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
}
