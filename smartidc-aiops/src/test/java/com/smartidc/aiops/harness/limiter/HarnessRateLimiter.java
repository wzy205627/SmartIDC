package com.smartidc.aiops.harness.limiter;

import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 压测平滑限流器 (对标 implementation_plan4.6.md 任务 2.1)
 * 防止并发压测打满百炼 API 限制产生 HTTP 429 报错
 */
public class HarnessRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(HarnessRateLimiter.class);

    private final RRateLimiter rRateLimiter;

    public HarnessRateLimiter(RedissonClient redissonClient, double permitsPerSecond) {
        this.rRateLimiter = redissonClient.getRateLimiter("smartidc:aiops:harness:ratelimiter");
        long rate = (long) Math.max(1, permitsPerSecond);
        this.rRateLimiter.trySetRate(RateType.OVERALL, rate, 1, RateIntervalUnit.SECONDS);
    }

    public void acquire() {
        rRateLimiter.acquire(1);
    }
}
