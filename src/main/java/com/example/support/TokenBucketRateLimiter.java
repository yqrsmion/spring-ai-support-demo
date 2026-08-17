package com.example.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 简单的内存令牌桶限流器：capacity 个突发额度，按 refillPerSecond 匀速补充。
 * 演示用单机实现；生产多实例可换 Redis + Lua 等分布式实现。
 */
@Component
public class TokenBucketRateLimiter {

    private final int capacity;
    private final double refillPerSecond;
    private double tokens;
    private long lastRefillNanos;

    public TokenBucketRateLimiter(
            @Value("${app.rate-limit.capacity:20}") int capacity,
            @Value("${app.rate-limit.refill-per-second:2}") double refillPerSecond) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.tokens = capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    public synchronized boolean tryAcquire() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
        lastRefillNanos = now;
        tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }
}
