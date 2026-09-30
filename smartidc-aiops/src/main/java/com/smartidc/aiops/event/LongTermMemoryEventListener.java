package com.smartidc.aiops.event;

import com.smartidc.aiops.memory.LongTermMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 长期记忆异步自进化事件监听器 (对标 implementation_plan4.5.md 任务 6.2)
 * 异步解耦，将排障成功病历沉淀至 pgvector 长期记忆向量库
 */
@Component
public class LongTermMemoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryEventListener.class);

    private final LongTermMemoryStore longTermMemoryStore;

    public LongTermMemoryEventListener(LongTermMemoryStore longTermMemoryStore) {
        this.longTermMemoryStore = longTermMemoryStore;
    }

    @Async
    @EventListener
    public void onTicketResolved(AioPsTicketResolvedEvent event) {
        log.info("🧠 [长期记忆自进化] 监听到工单成功闭环事件，开始异步提炼病历, rackCode: {}, faultType: {}",
                event.getRackCode(), event.getFaultType());
        try {
            longTermMemoryStore.recordNewInsight(
                    event.getRackCode(),
                    event.getFaultType(),
                    event.getInsightSummary(),
                    event.getTenantId()
            );
        } catch (Exception e) {
            log.warn("⚠️ 异步沉淀长期记忆异常: {}", e.getMessage(), e);
        }
    }
}
