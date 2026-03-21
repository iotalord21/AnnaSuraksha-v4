package com.rationchain.config;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sliding-window in-memory rate limiter.
 *
 * Uses a per-IP deque of request timestamps. Requests older than
 * the window are purged on each check. Thread-safe via ConcurrentHashMap
 * + synchronized deque access.
 *
 * Limits (configurable via constructor or application.properties):
 *   - Default: 60 requests per 60 seconds per IP
 *   - API key authenticated: 300 requests per 60 seconds
 *
 * At national scale this would be replaced with Redis + Lua sliding
 * window or a dedicated rate-limit sidecar (e.g. Envoy, Kong).
 */
@Component
public class InMemoryRateLimiter {

    private static final int  WINDOW_SECONDS   = 60;
    private static final int  ANON_LIMIT        = 30;    // unauthenticated
    private static final int  AUTH_LIMIT        = 300;   // with valid API key
    private static final long PURGE_EVERY_MS   = 300_000; // purge stale entries every 5 min

    private final ConcurrentHashMap<String, Deque<Long>> windowMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private volatile long lastPurgeMs = System.currentTimeMillis();

    /**
     * Check and record a request attempt.
     *
     * @param clientIp      Remote IP address
     * @param authenticated Whether the request carries a valid API key
     * @return              true if allowed, false if rate-limited
     */
    public boolean tryAcquire(String clientIp, boolean authenticated) {
        maybePurgeStale();

        int limit = authenticated ? AUTH_LIMIT : ANON_LIMIT;
        long now  = Instant.now().toEpochMilli();
        long windowStart = now - (WINDOW_SECONDS * 1000L);

        Deque<Long> timestamps = windowMap.computeIfAbsent(clientIp,
            k -> new ArrayDeque<>());

        synchronized (timestamps) {
            // Remove timestamps outside the window
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limit) {
                return false; // rate limited
            }
            timestamps.addLast(now);
            return true;
        }
    }

    /** How many requests remain for this IP in the current window. */
    public int remaining(String clientIp, boolean authenticated) {
        int limit = authenticated ? AUTH_LIMIT : ANON_LIMIT;
        long windowStart = Instant.now().toEpochMilli() - (WINDOW_SECONDS * 1000L);
        Deque<Long> ts = windowMap.getOrDefault(clientIp, new ArrayDeque<>());
        synchronized (ts) {
            long inWindow = ts.stream().filter(t -> t >= windowStart).count();
            return (int) Math.max(0, limit - inWindow);
        }
    }

    /** Periodic cleanup of stale IP entries to prevent memory leak. */
    private void maybePurgeStale() {
        long now = System.currentTimeMillis();
        if (now - lastPurgeMs < PURGE_EVERY_MS) return;
        lastPurgeMs = now;
        long windowStart = now - (WINDOW_SECONDS * 1000L);
        windowMap.entrySet().removeIf(entry -> {
            Deque<Long> ts = entry.getValue();
            synchronized (ts) {
                while (!ts.isEmpty() && ts.peekFirst() < windowStart) ts.pollFirst();
                return ts.isEmpty();
            }
        });
    }
}
