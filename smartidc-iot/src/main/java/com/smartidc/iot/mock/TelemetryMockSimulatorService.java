package com.smartidc.iot.mock;

import com.smartidc.iot.client.MqttMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 动环物联网遥测 Mock 模拟器与混沌故障演练核心服务
 * 提供全机房所有在管机架 (支持跨多租户) 定时合规心跳发生与一键高危故障注入能力
 */
@Service
public class TelemetryMockSimulatorService implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TelemetryMockSimulatorService.class);

    private final MqttMessageSender mqttMessageSender;
    private final TelemetryMockGenerator mockGenerator = new TelemetryMockGenerator();

    @Autowired(required = false)
    private RackLoadMetricsProvider rackLoadMetricsProvider;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong totalPacketsSent = new AtomicLong(0L);

    private volatile ChaosMode activeChaosMode = ChaosMode.NORMAL;
    private volatile String targetRack = "A-03";
    private volatile long intervalMs = 2000L;

    private ScheduledExecutorService scheduler;

    public TelemetryMockSimulatorService(MqttMessageSender mqttMessageSender) {
        this.mqttMessageSender = mqttMessageSender;
    }

    public void setRackLoadMetricsProvider(RackLoadMetricsProvider rackLoadMetricsProvider) {
        this.rackLoadMetricsProvider = rackLoadMetricsProvider;
    }

    @Override
    public void afterPropertiesSet() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "smartidc-mock-simulator");
            t.setDaemon(true);
            return t;
        });

        // 默认自动启动模拟器 (每 2 秒周期发送全域在管机柜正常心跳)
        this.running.set(true);
        this.scheduler.scheduleAtFixedRate(this::doDispatchRound, 1000L, intervalMs, TimeUnit.MILLISECONDS);
        log.info("[MockSimulator] 动环物联网遥测 Mock 模拟器已启动，周期={}ms，支持跨多租户全域在管机架", intervalMs);
    }

    @Override
    public void destroy() {
        this.running.set(false);
        if (this.scheduler != null && !this.scheduler.isShutdown()) {
            this.scheduler.shutdownNow();
        }
        log.info("[MockSimulator] 动环 Mock 模拟器已停止");
    }

    private List<ManagedRackMeta> defaultFallbackRacks() {
        List<ManagedRackMeta> list = new ArrayList<>();
        for (String r : Arrays.asList("A-01", "A-02", "A-03", "A-04", "A-05", "A-06")) {
            list.add(new ManagedRackMeta("000000", r));
        }
        return list;
    }

    /**
     * 单轮广播投递全机房遥测报文 (跨所有租户的在管物理机柜)
     */
    private void doDispatchRound() {
        if (!running.get()) {
            return;
        }

        try {
            ChaosMode currentMode = this.activeChaosMode;
            String currentTarget = this.targetRack;

            List<ManagedRackMeta> racksToSimulate = null;
            if (rackLoadMetricsProvider != null) {
                try {
                    racksToSimulate = rackLoadMetricsProvider.listAllManagedRacks();
                } catch (Exception ex) {
                    log.debug("[MockSimulator] 动态拉取机柜清单异常: {}", ex.getMessage());
                }
            }
            if (racksToSimulate == null || racksToSimulate.isEmpty()) {
                racksToSimulate = defaultFallbackRacks();
            }

            for (ManagedRackMeta rack : racksToSimulate) {
                String rTenant = rack.getTenantId();
                String rCode = rack.getRackCode();

                RackLoadInfo loadInfo = null;
                if (rackLoadMetricsProvider != null) {
                    try {
                        loadInfo = rackLoadMetricsProvider.getRackLoad(rTenant, rCode);
                    } catch (Exception ex) {
                        log.debug("[MockSimulator] 查询机柜 [{}] (租户={}) 负荷失败: {}", rCode, rTenant, ex.getMessage());
                    }
                }
                String payload = mockGenerator.generatePayload(rTenant, rCode, currentMode, currentTarget, loadInfo);
                mqttMessageSender.sendTelemetry(rTenant, rCode, payload);
                totalPacketsSent.incrementAndGet();
            }

            // 若当前为瞬态毛刺模式，触发 1 轮后自动重置回 NORMAL
            if (currentMode == ChaosMode.GLITCH) {
                this.activeChaosMode = ChaosMode.NORMAL;
                log.info("[MockSimulator] 瞬态信号毛刺单次注入完毕，已自动回退至 NORMAL 正常工况");
            }
        } catch (Exception e) {
            log.error("[MockSimulator] 定时投递动环遥测报文异常: {}", e.getMessage(), e);
        }
    }

    // ==================== 混沌演练与故障注入控制接口 ====================

    /**
     * 启动/恢复模拟器
     */
    public synchronized void start() {
        this.running.set(true);
        log.info("[MockSimulator] 模拟器已手动激活运行");
    }

    /**
     * 暂停模拟器
     */
    public synchronized void stop() {
        this.running.set(false);
        log.info("[MockSimulator] 模拟器已手动暂停运行");
    }

    /**
     * 注入单机架持续严重超温 (38.5℃)
     */
    public void injectOverheat(String rackCode) {
        this.targetRack = (rackCode != null && !rackCode.isBlank()) ? rackCode.trim() : "A-03";
        this.activeChaosMode = ChaosMode.OVERHEAT;
        this.running.set(true);
        log.warn("[MockSimulator] 【故障注入】已触发机架 [{}] 严重超温故障 (模式: OVERHEAT)", this.targetRack);
    }

    /**
     * 注入单机架市电中断断电 (0V / 0A)
     */
    public void injectBlackout(String rackCode) {
        this.targetRack = (rackCode != null && !rackCode.isBlank()) ? rackCode.trim() : "A-01";
        this.activeChaosMode = ChaosMode.BLACKOUT;
        this.running.set(true);
        log.warn("[MockSimulator] 【故障注入】已触发机架 [{}] 供电母线断电故障 (模式: BLACKOUT)", this.targetRack);
    }

    /**
     * 注入机房集群告警风暴 (A-01, A-02, A-03 同时高温)
     */
    public void injectStorm() {
        this.activeChaosMode = ChaosMode.STORM;
        this.running.set(true);
        log.warn("[MockSimulator] 【故障注入】已触发同机房多机柜并发过温告警风暴 (模式: STORM)");
    }

    /**
     * 注入瞬态偶发毛刺 (单次 42℃，随后立即恢复)
     */
    public void injectGlitch(String rackCode) {
        this.targetRack = (rackCode != null && !rackCode.isBlank()) ? rackCode.trim() : "A-03";
        this.activeChaosMode = ChaosMode.GLITCH;
        this.running.set(true);
        log.info("[MockSimulator] 【故障注入】已向机架 [{}] 注入单次瞬态 42℃ 毛刺信号", this.targetRack);
    }

    /**
     * 一键重置恢复全机房正常安全工况
     */
    public void resetToNormal() {
        this.activeChaosMode = ChaosMode.NORMAL;
        log.info("[MockSimulator] 【工况恢复】全机架已一键恢复标准安全工况 (模式: NORMAL)");
    }

    /**
     * 查询模拟器当前演练状态
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("running", this.running.get());
        status.put("chaosMode", this.activeChaosMode.name());
        status.put("chaosTitle", this.activeChaosMode.getTitle());
        status.put("chaosDescription", this.activeChaosMode.getDescription());
        status.put("targetRack", this.targetRack);
        status.put("intervalMs", this.intervalMs);

        List<ManagedRackMeta> racks = (rackLoadMetricsProvider != null) ? rackLoadMetricsProvider.listAllManagedRacks() : defaultFallbackRacks();
        List<String> rackDescs = racks.stream().map(r -> r.getRackCode() + " (" + r.getTenantId() + ")").toList();
        status.put("managedRacks", rackDescs);
        status.put("totalPacketsSent", this.totalPacketsSent.get());
        return status;
    }
}
