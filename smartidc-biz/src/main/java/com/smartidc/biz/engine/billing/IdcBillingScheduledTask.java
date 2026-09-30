package com.smartidc.biz.engine.billing;

import com.smartidc.biz.service.IdcBillingService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 月度租赁电费自动结算批处理定时任务
 * 集群环境下由 Redisson 分布式锁保障多 Pod 并发安全
 */
@Component
public class IdcBillingScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(IdcBillingScheduledTask.class);

    private final IdcBillingService idcBillingService;
    private final RedissonClient redissonClient;

    public IdcBillingScheduledTask(IdcBillingService idcBillingService, RedissonClient redissonClient) {
        this.idcBillingService = idcBillingService;
        this.redissonClient = redissonClient;
    }

    /**
     * 每月 1 日凌晨 02:00 自动触发上月账单结算
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void runMonthlyBillingSettlement() {
        LocalDate lastMonthDate = LocalDate.now().minusMonths(1);
        String billingMonth = lastMonthDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        String lockKey = "smartidc:billing:lock:monthly:" + billingMonth;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试加锁 0 秒，持有锁最长 30 分钟，抢锁失败立即退出
            if (lock.tryLock(0, 30, TimeUnit.MINUTES)) {
                try {
                    log.info("[月度计费任务] 成功获取分布式调度锁，开始结算 [{}] 账单", billingMonth);
                    idcBillingService.executeMonthlySettlement(billingMonth, false);
                    log.info("[月度计费任务] [{}] 账单结算圆满完成", billingMonth);
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                log.info("[月度计费任务] 其他集群节点正在执行 [{}] 账单结算，本节点跳过", billingMonth);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[月度计费任务] 调度加锁被中断", e);
        }
    }
}
