package com.example.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

    @Test
    void allowsBurstUpToCapacity() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, 0);
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void refillsOverTime() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 100);
        assertTrue(limiter.tryAcquire());
        Thread.sleep(30); // 30ms * 100/s = 3 个令牌，达到容量后补回 1 个
        assertTrue(limiter.tryAcquire());
    }
}
