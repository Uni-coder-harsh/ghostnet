package com.ghostnet.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe Token Bucket rate limiter (Req 209).
 * Protects HTTP and WebSocket endpoints from abuse, spam, and DoS without external dependencies.
 */
@Component
public class TokenBucketRateLimiter {

    @Value("${ghostnet.security.rate-limit-per-minute:120}")
    private int capacity;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, capacity / 60.0));
        return bucket.tryConsume();
    }

    @Scheduled(fixedRate = 300000) // Cleanup stale buckets every 5 minutes
    public void cleanupStaleBuckets() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry -> now - entry.getValue().lastRefillTimestamp > 600000);
    }

    private static class Bucket {
        private final double capacity;
        private final double refillRatePerSecond;
        private double tokens;
        private long lastRefillTimestamp;

        public Bucket(double capacity, double refillRatePerSecond) {
            this.capacity = capacity;
            this.refillRatePerSecond = refillRatePerSecond;
            this.tokens = capacity;
            this.lastRefillTimestamp = System.currentTimeMillis();
        }

        public synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            double secondsPassed = (now - lastRefillTimestamp) / 1000.0;
            if (secondsPassed > 0) {
                tokens = Math.min(capacity, tokens + (secondsPassed * refillRatePerSecond));
                lastRefillTimestamp = now;
            }
        }
    }
}
