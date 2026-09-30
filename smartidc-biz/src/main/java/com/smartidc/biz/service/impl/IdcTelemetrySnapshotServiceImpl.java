package com.smartidc.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartidc.biz.domain.entity.IdcAlarmEvent;
import com.smartidc.biz.domain.entity.IdcRack;
import com.smartidc.biz.domain.entity.IdcTelemetrySnapshot;
import com.smartidc.biz.domain.vo.TelemetryTimelineVO;
import com.smartidc.biz.mapper.IdcAlarmEventMapper;
import com.smartidc.biz.mapper.IdcRackMapper;
import com.smartidc.biz.mapper.IdcTelemetrySnapshotMapper;
import com.smartidc.biz.service.IdcTelemetrySnapshotService;
import com.smartidc.iot.domain.dto.TelemetryMetricDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动环遥测时序快照服务实现类
 */
@Service
public class IdcTelemetrySnapshotServiceImpl extends ServiceImpl<IdcTelemetrySnapshotMapper, IdcTelemetrySnapshot>
        implements IdcTelemetrySnapshotService {

    private static final Logger log = LoggerFactory.getLogger(IdcTelemetrySnapshotServiceImpl.class);

    private static final String REDIS_KEY_PREFIX = "smartidc:telemetry:latest:";

    private final IdcTelemetrySnapshotMapper snapshotMapper;
    private final IdcRackMapper rackMapper;
    private final IdcAlarmEventMapper alarmEventMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 机架编码 ➔ 机架主键ID 本地内存缓存，避免高频遥测下频繁查库
     */
    private final Map<String, Long> rackCodeCache = new ConcurrentHashMap<>();

    public IdcTelemetrySnapshotServiceImpl(IdcTelemetrySnapshotMapper snapshotMapper,
                                           IdcRackMapper rackMapper,
                                           IdcAlarmEventMapper alarmEventMapper,
                                           StringRedisTemplate redisTemplate) {
        this.snapshotMapper = snapshotMapper;
        this.rackMapper = rackMapper;
        this.alarmEventMapper = alarmEventMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void handle(List<TelemetryMetricDTO> batch) {
        batchSave(batch);
    }

    @Override
    public void batchSave(List<TelemetryMetricDTO> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return;
        }

        List<IdcTelemetrySnapshot> snapshots = new ArrayList<>(metrics.size());
        for (TelemetryMetricDTO dto : metrics) {
            Long rackId = getOrLoadRackId(dto.getRackCode());
            if (rackId == null) {
                log.warn("[TelemetryBatch] 未识别的机架编码: {}, 暂无法入库该遥测数据", dto.getRackCode());
                continue;
            }

            IdcTelemetrySnapshot snapshot = new IdcTelemetrySnapshot();
            snapshot.setRackId(rackId);
            // 设备ID默认为0 (未绑定独立传感器) 或后续由设备别名关联
            snapshot.setDeviceId(0L);
            snapshot.setTemperature(dto.getTemperature());
            snapshot.setHumidity(dto.getHumidity());
            snapshot.setVoltage(dto.getVoltage());
            snapshot.setCurrentAmp(dto.getCurrentAmp());
            snapshot.setPowerKw(dto.getPowerKw());

            long ts = dto.getSampleTimestamp() != null && dto.getSampleTimestamp() > 0
                    ? dto.getSampleTimestamp()
                    : System.currentTimeMillis();
            snapshot.setSampleTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()));

            snapshots.add(snapshot);
        }

        if (snapshots.isEmpty()) {
            return;
        }

        try {
            int inserted = snapshotMapper.insertBatch(snapshots);
            log.info("[TelemetryBatch] 批量持久化成功: 插入条数=[{}], 涉及机柜=[{}]",
                    inserted, snapshots.stream().map(IdcTelemetrySnapshot::getRackId).distinct().toList());
        } catch (Exception e) {
            log.error("[TelemetryBatch] 批量持久化写入异常: {}", e.getMessage(), e);
        }
    }

    @Override
    public Map<Object, Object> getLatestByRackCode(String rackCode) {
        if (rackCode == null || rackCode.isBlank()) {
            return Map.of();
        }

        // 1. 优先从 Redis Hash 读取热数据
        String key = REDIS_KEY_PREFIX + rackCode.trim();
        Map<Object, Object> redisData = redisTemplate.opsForHash().entries(key);
        if (redisData != null && !redisData.isEmpty()) {
            return redisData;
        }

        // 2. Redis 未命中或已过期，回退查询 MySQL 快照表最新一条
        Long rackId = getOrLoadRackId(rackCode);
        if (rackId == null) {
            return Map.of();
        }

        List<IdcTelemetrySnapshot> list = snapshotMapper.selectList(
                new LambdaQueryWrapper<IdcTelemetrySnapshot>()
                        .eq(IdcTelemetrySnapshot::getRackId, rackId)
                        .orderByDesc(IdcTelemetrySnapshot::getSampleTime)
                        .last("LIMIT 1")
        );

        if (list.isEmpty()) {
            return Map.of();
        }

        IdcTelemetrySnapshot snapshot = list.get(0);
        Map<Object, Object> result = new HashMap<>();
        result.put("rackCode", rackCode);
        result.put("temperature", snapshot.getTemperature() != null ? snapshot.getTemperature().toPlainString() : "");
        result.put("humidity", snapshot.getHumidity() != null ? snapshot.getHumidity().toPlainString() : "");
        result.put("voltage", snapshot.getVoltage() != null ? snapshot.getVoltage().toPlainString() : "");
        result.put("currentAmp", snapshot.getCurrentAmp() != null ? snapshot.getCurrentAmp().toPlainString() : "");
        result.put("powerKw", snapshot.getPowerKw() != null ? snapshot.getPowerKw().toPlainString() : "");
        result.put("sampleTime", snapshot.getSampleTime() != null ? snapshot.getSampleTime().toString() : "");
        result.put("fromDb", "true");
        return result;
    }

    @Override
    public List<IdcTelemetrySnapshot> getHistoryByRackCode(String rackCode, int limit) {
        if (rackCode == null || rackCode.isBlank()) {
            return List.of();
        }
        Long rackId = getOrLoadRackId(rackCode);
        if (rackId == null) {
            return List.of();
        }
        int maxLimit = Math.min(Math.max(limit, 1), 200);

        return snapshotMapper.selectList(
                new LambdaQueryWrapper<IdcTelemetrySnapshot>()
                        .eq(IdcTelemetrySnapshot::getRackId, rackId)
                        .orderByDesc(IdcTelemetrySnapshot::getSampleTime)
                        .last("LIMIT " + maxLimit)
        );
    }

    @Override
    public TelemetryTimelineVO getTimelineByRackCode(String rackCode, String timeRange, String metrics) {
        TelemetryTimelineVO vo = new TelemetryTimelineVO();
        if (rackCode == null || rackCode.isBlank()) {
            return vo;
        }
        String cleanCode = rackCode.trim();
        vo.setRackCode(cleanCode);

        // 1. 查询机柜基础信息
        List<IdcRack> racks = rackMapper.selectList(
                new LambdaQueryWrapper<IdcRack>()
                        .eq(IdcRack::getRackCode, cleanCode)
                        .last("LIMIT 1")
        );
        if (racks.isEmpty()) {
            return vo;
        }
        IdcRack rack = racks.get(0);
        vo.setRackId(rack.getRackId());
        vo.setRoomName(rack.getRoomName());

        // 2. 解析时间范围与聚合步长
        String range = (timeRange == null || timeRange.isBlank()) ? "24h" : timeRange.trim().toLowerCase();
        vo.setTimeRange(range);

        int bucketStepSeconds;
        long windowSeconds;
        switch (range) {
            case "1h" -> {
                bucketStepSeconds = 30;
                windowSeconds = 3600;
            }
            case "6h" -> {
                bucketStepSeconds = 120;
                windowSeconds = 21600;
            }
            case "7d" -> {
                bucketStepSeconds = 1800;
                windowSeconds = 604800;
            }
            case "24h" -> {
                bucketStepSeconds = 300;
                windowSeconds = 86400;
            }
            default -> {
                range = "24h";
                vo.setTimeRange("24h");
                bucketStepSeconds = 300;
                windowSeconds = 86400;
            }
        }
        vo.setBucketStepSeconds(bucketStepSeconds);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusSeconds(windowSeconds);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // 3. 查询时间范围内的动环时序快照 (仅查询必要字段)
        List<IdcTelemetrySnapshot> snapshots = snapshotMapper.selectList(
                new LambdaQueryWrapper<IdcTelemetrySnapshot>()
                        .select(IdcTelemetrySnapshot::getTemperature,
                                IdcTelemetrySnapshot::getHumidity,
                                IdcTelemetrySnapshot::getVoltage,
                                IdcTelemetrySnapshot::getCurrentAmp,
                                IdcTelemetrySnapshot::getPowerKw,
                                IdcTelemetrySnapshot::getSampleTime)
                        .eq(IdcTelemetrySnapshot::getRackId, rack.getRackId())
                        .ge(IdcTelemetrySnapshot::getSampleTime, startTime)
                        .le(IdcTelemetrySnapshot::getSampleTime, now)
                        .orderByAsc(IdcTelemetrySnapshot::getSampleTime)
        );

        // 4. 自适应时间桶降采样聚合 (Bucket Aggregation)
        List<String> timestamps = new ArrayList<>();
        List<BigDecimal> tempSeries = new ArrayList<>();
        List<BigDecimal> tempMaxSeries = new ArrayList<>();
        List<BigDecimal> humSeries = new ArrayList<>();
        List<BigDecimal> voltSeries = new ArrayList<>();
        List<BigDecimal> currentSeries = new ArrayList<>();
        List<BigDecimal> powerSeries = new ArrayList<>();

        if (!snapshots.isEmpty()) {
            ZoneId zoneId = ZoneId.systemDefault();
            long startEpoch = startTime.atZone(zoneId).toEpochSecond();

            // 按 bucketIndex 归类分组
            Map<Long, List<IdcTelemetrySnapshot>> bucketMap = new TreeMap<>();
            long minBucket = Long.MAX_VALUE;
            long maxBucket = Long.MIN_VALUE;

            for (IdcTelemetrySnapshot snap : snapshots) {
                if (snap.getSampleTime() == null) continue;
                long snapEpoch = snap.getSampleTime().atZone(zoneId).toEpochSecond();
                long bucketIdx = (snapEpoch - startEpoch) / bucketStepSeconds;
                bucketMap.computeIfAbsent(bucketIdx, k -> new ArrayList<>()).add(snap);
                minBucket = Math.min(minBucket, bucketIdx);
                maxBucket = Math.max(maxBucket, bucketIdx);
            }

            // 保持连续性，从 minBucket 遍历到 maxBucket
            BigDecimal lastTemp = null;
            BigDecimal lastTempMax = null;
            BigDecimal lastHum = null;
            BigDecimal lastVolt = null;
            BigDecimal lastCurrent = null;
            BigDecimal lastPower = null;

            for (long idx = minBucket; idx <= maxBucket; idx++) {
                LocalDateTime bucketTime = startTime.plusSeconds(idx * bucketStepSeconds);
                timestamps.add(bucketTime.format(formatter));

                List<IdcTelemetrySnapshot> bucketSnaps = bucketMap.get(idx);
                if (bucketSnaps != null && !bucketSnaps.isEmpty()) {
                    double sumTemp = 0, maxTemp = -999.0;
                    double sumHum = 0, sumVolt = 0, sumCurrent = 0, sumPower = 0;
                    int count = 0;

                    for (IdcTelemetrySnapshot s : bucketSnaps) {
                        count++;
                        if (s.getTemperature() != null) {
                            double t = s.getTemperature().doubleValue();
                            sumTemp += t;
                            if (t > maxTemp) maxTemp = t;
                        }
                        if (s.getHumidity() != null) sumHum += s.getHumidity().doubleValue();
                        if (s.getVoltage() != null) sumVolt += s.getVoltage().doubleValue();
                        if (s.getCurrentAmp() != null) sumCurrent += s.getCurrentAmp().doubleValue();
                        if (s.getPowerKw() != null) sumPower += s.getPowerKw().doubleValue();
                    }

                    lastTemp = BigDecimal.valueOf(sumTemp / count).setScale(1, RoundingMode.HALF_UP);
                    lastTempMax = BigDecimal.valueOf(maxTemp > -990 ? maxTemp : sumTemp / count).setScale(1, RoundingMode.HALF_UP);
                    lastHum = BigDecimal.valueOf(sumHum / count).setScale(1, RoundingMode.HALF_UP);
                    lastVolt = BigDecimal.valueOf(sumVolt / count).setScale(1, RoundingMode.HALF_UP);
                    lastCurrent = BigDecimal.valueOf(sumCurrent / count).setScale(2, RoundingMode.HALF_UP);
                    lastPower = BigDecimal.valueOf(sumPower / count).setScale(2, RoundingMode.HALF_UP);
                }

                tempSeries.add(lastTemp);
                tempMaxSeries.add(lastTempMax);
                humSeries.add(lastHum);
                voltSeries.add(lastVolt);
                currentSeries.add(lastCurrent);
                powerSeries.add(lastPower);
            }
        }

        vo.setTimestamps(timestamps);
        Map<String, List<BigDecimal>> seriesMap = new HashMap<>();
        seriesMap.put("temperature", tempSeries);
        seriesMap.put("temperatureMax", tempMaxSeries);
        seriesMap.put("humidity", humSeries);
        seriesMap.put("voltage", voltSeries);
        seriesMap.put("currentAmp", currentSeries);
        seriesMap.put("powerKw", powerSeries);
        vo.setSeries(seriesMap);

        // 5. 关联查询该时间段内的机柜越限告警区间
        try {
            List<IdcAlarmEvent> alarms = alarmEventMapper.selectList(
                    new LambdaQueryWrapper<IdcAlarmEvent>()
                            .eq(IdcAlarmEvent::getRackId, rack.getRackId())
                            .le(IdcAlarmEvent::getTriggerTime, now)
                            .and(w -> w.ge(IdcAlarmEvent::getClearTime, startTime).or().isNull(IdcAlarmEvent::getClearTime))
                            .ne(IdcAlarmEvent::getStatus, 4)
                            .orderByAsc(IdcAlarmEvent::getTriggerTime)
            );

            List<TelemetryTimelineVO.AlarmIntervalVO> intervalList = new ArrayList<>();
            for (IdcAlarmEvent alarm : alarms) {
                TelemetryTimelineVO.AlarmIntervalVO interval = new TelemetryTimelineVO.AlarmIntervalVO();
                interval.setAlarmId(alarm.getAlarmId());
                interval.setAlarmLevel(alarm.getAlarmLevel());
                interval.setAlarmType(alarm.getAlarmType());
                interval.setStartTime(alarm.getTriggerTime() != null ? alarm.getTriggerTime().format(formatter) : "");
                interval.setEndTime(alarm.getClearTime() != null ? alarm.getClearTime().format(formatter) : null);
                interval.setMetricValue(alarm.getMetricValue());
                interval.setSummary(alarm.getRcaSummary());
                intervalList.add(interval);
            }
            vo.setAlarmIntervals(intervalList);
        } catch (Exception e) {
            log.warn("[Timeline] 查询告警区间异常: {}", e.getMessage());
        }

        return vo;
    }

    /**
     * 获取机架 ID (带本地内存字典缓存)
     */
    private Long getOrLoadRackId(String rackCode) {
        if (rackCode == null || rackCode.isBlank()) {
            return null;
        }
        String cleanCode = rackCode.trim();
        return rackCodeCache.computeIfAbsent(cleanCode, code -> {
            List<IdcRack> racks = rackMapper.selectList(
                    new LambdaQueryWrapper<IdcRack>()
                            .eq(IdcRack::getRackCode, code)
                            .last("LIMIT 1")
            );
            if (!racks.isEmpty()) {
                return racks.get(0).getRackId();
            }
            return null;
        });
    }
}
