package com.smartidc.biz.engine.alarm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 空间关联聚合抑制引擎 (Spatial Deduplication Engine)
 * 职责：
 * 1. 维护机房维度 30 秒滑动时间窗口；
 * 2. 统计同一机房内同类型告警的越限机柜数量；
 * 3. 超过阈值 (>= 3台机柜) 时，自动聚合为机房级主告警并升级为 CRITICAL，抑制单机柜刷屏。
 */
@Component
public class SpatialDeduplicationEngine {

    private static final Logger log = LoggerFactory.getLogger(SpatialDeduplicationEngine.class);

    public static class RackTriggerRecord {
        private final String rackCode;
        private final long timestamp;

        public RackTriggerRecord(String rackCode, long timestamp) {
            this.rackCode = rackCode;
            this.timestamp = timestamp;
        }

        public String getRackCode() {
            return rackCode;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    public static class DeduplicationDecision {
        private final boolean aggregated;
        private final String roomName;
        private final String alarmType;
        private final List<String> involvedRacks;
        private final String masterSummary;
        private final Long masterAlarmId;

        public DeduplicationDecision(boolean aggregated, String roomName, String alarmType,
                                     List<String> involvedRacks, String masterSummary, Long masterAlarmId) {
            this.aggregated = aggregated;
            this.roomName = roomName;
            this.alarmType = alarmType;
            this.involvedRacks = involvedRacks;
            this.masterSummary = masterSummary;
            this.masterAlarmId = masterAlarmId;
        }

        public boolean isAggregated() {
            return aggregated;
        }

        public String getRoomName() {
            return roomName;
        }

        public String getAlarmType() {
            return alarmType;
        }

        public List<String> getInvolvedRacks() {
            return involvedRacks;
        }

        public String getMasterSummary() {
            return masterSummary;
        }

        public Long getMasterAlarmId() {
            return masterAlarmId;
        }
    }

    // 默认空间聚合窗口: 30 秒, 阈值: 3 台机柜
    private long windowMs = 30000L;
    private int clusterRackThreshold = 3;

    /**
     * Key: roomName + ":" + alarmType -> 触发记录队列
     */
    private final Map<String, List<RackTriggerRecord>> roomHistoryMap = new ConcurrentHashMap<>();

    /**
     * 当前处于活动中的机房级主告警 ID
     */
    private final Map<String, Long> masterAlarmMap = new ConcurrentHashMap<>();

    public void setWindowMs(long windowMs) {
        this.windowMs = windowMs;
    }

    public void setClusterRackThreshold(int clusterRackThreshold) {
        this.clusterRackThreshold = clusterRackThreshold;
    }

    /**
     * 空间关联研判
     *
     * @param roomName  机房区域名称 (如: 华东01-A区)
     * @param rackCode  机柜编号 (如: A-03)
     * @param alarmType 告警类型 (如: TEMP_HIGH)
     * @return 聚合决策
     */
    public synchronized DeduplicationDecision evaluate(String roomName, String rackCode, String alarmType) {
        if (roomName == null || roomName.isBlank()) {
            roomName = "默认机房区域";
        }
        String key = roomName.trim() + ":" + alarmType;
        long now = System.currentTimeMillis();

        List<RackTriggerRecord> records = roomHistoryMap.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        records.add(new RackTriggerRecord(rackCode, now));

        // 剔除超出 30s 滑动窗口的过期记录
        records.removeIf(r -> (now - r.getTimestamp()) > windowMs);

        // 统计 30s 内出现的不同机柜列表
        Set<String> distinctRacks = new LinkedHashSet<>();
        for (RackTriggerRecord r : records) {
            distinctRacks.add(r.getRackCode());
        }

        if (distinctRacks.size() >= clusterRackThreshold) {
            // 满足空间聚合阈值！触发集群性主告警
            List<String> racksList = new ArrayList<>(distinctRacks);
            String summary = String.format("[空间聚合主告警] %s 发生集群性 %s 越限，已聚合归并关联机柜 [%s] (共 %d 台)，疑似机房级基础设施 (精密空调/母线) 故障",
                    roomName, getAlarmTypeChinese(alarmType), String.join(", ", racksList), racksList.size());

            Long masterId = masterAlarmMap.get(key);
            log.warn("[SpatialDedup] 触发空间关联抑制与主告警升格! 机房=[{}], 聚合机柜数=[{}], racks={}",
                    roomName, racksList.size(), racksList);

            return new DeduplicationDecision(true, roomName, alarmType, racksList, summary, masterId);
        }

        return new DeduplicationDecision(false, roomName, alarmType, new ArrayList<>(distinctRacks), null, null);
    }

    /**
     * 记录机房级主告警主键 ID
     */
    public void bindMasterAlarmId(String roomName, String alarmType, Long masterAlarmId) {
        String key = (roomName != null ? roomName.trim() : "默认机房区域") + ":" + alarmType;
        masterAlarmMap.put(key, masterAlarmId);
    }

    /**
     * 清理主告警 (当机房告警恢复消除后)
     */
    public void clearMasterAlarm(String roomName, String alarmType) {
        String key = (roomName != null ? roomName.trim() : "默认机房区域") + ":" + alarmType;
        masterAlarmMap.remove(key);
        roomHistoryMap.remove(key);
    }

    public void clearAll() {
        roomHistoryMap.clear();
        masterAlarmMap.clear();
    }

    private String getAlarmTypeChinese(String type) {
        if ("TEMP_HIGH".equalsIgnoreCase(type)) return "高温";
        if ("VOLTAGE_LOW".equalsIgnoreCase(type)) return "欠压";
        if ("POWER_FAIL".equalsIgnoreCase(type)) return "失电断电";
        if ("HUMIDITY_HIGH".equalsIgnoreCase(type)) return "高湿";
        return type;
    }
}
