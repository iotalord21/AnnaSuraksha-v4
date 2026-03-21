package com.rationchain.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.rationchain.service.AuditLogService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

/**
 * Security filter applied to all /api/** routes.
 *
 * Responsibilities:
 *   1. API key authentication (via X-Api-Key header or ?apiKey= query param)
 *   2. Sliding-window rate limiting (per-IP)
 *   3. CORS headers for dashboard SPA
 *   4. Request audit logging
 *   5. Security headers on all responses
 *
 * Authentication:
 *   - In production: validate against a secrets manager or HMAC-signed token.
 *   - For demo: the API key is set via app.api.master-key in application.properties.
 *   - Public transparency endpoints (/api/transparency/**) are exempt from auth
 *     (intentionally public data).
 *   - /api/stats is also exempt (existing demo endpoint).
 *
 * Response on 401: {"error": "Missing or invalid API key"}
 * Response on 429: {"error": "Rate limit exceeded. Retry after 60s."}
 */

@Component

public class ApiSecurityFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ApiSecurityFilter.class);


    private final InMemoryRateLimiter rateLimiter;
    private final AuditLogService     auditLog;

    @Value("${app.api.master-key:DEMO-ANNASURAKSHA-2025}")
    private String masterKey;

    /** Endpoints under /api/** that require NO authentication (public data). */
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
        "/api/transparency",
        "/api/stats"
    );

    public ApiSecurityFilter(InMemoryRateLimiter rateLimiter, AuditLogService auditLog) {
        this.rateLimiter = rateLimiter;
        this.auditLog = auditLog;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only filter /api/** paths
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        long   start    = System.currentTimeMillis();
        String clientIp = extractClientIp(request);
        String path     = request.getRequestURI();

        // ── Security headers ──────────────────────────────────────────────
        response.setHeader("X-Content-Type-Options",  "nosniff");
        response.setHeader("X-Frame-Options",          "DENY");
        response.setHeader("X-XSS-Protection",         "1; mode=block");
        response.setHeader("Cache-Control",            "no-store");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Headers",
            "Content-Type, X-Api-Key, Authorization");

        // ── OPTIONS pre-flight ─────────────────────────────────────────────
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        // ── Public endpoints — skip auth, apply rate limit only ───────────
        boolean isPublic = PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);

        // ── Rate limiting ─────────────────────────────────────────────────
        String apiKey       = extractApiKey(request);
        boolean authed      = isPublic || validateKey(apiKey);

        if (!rateLimiter.tryAcquire(clientIp, authed)) {
            auditLog.logRateLimit(clientIp, path);
            log.warn("Rate limit exceeded — IP: {} path: {}", clientIp, path);
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Retry after 60s.\","
                + "\"retryAfterSeconds\":60}");
            return;
        }

        // ── Authentication (non-public routes) ────────────────────────────
        if (!isPublic && !authed) {
            String keyHash = apiKey != null ? sha256(apiKey) : "none";
            auditLog.logAuthEvent(false, clientIp, keyHash, path);
            log.warn("Unauthorized API access — IP: {} path: {}", clientIp, path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Missing or invalid API key. "
                + "Pass X-Api-Key header or ?apiKey= query param.\"}");
            return;
        }

        // ── Add rate-limit headers ─────────────────────────────────────────
        response.setIntHeader("X-RateLimit-Remaining",
            rateLimiter.remaining(clientIp, authed));
        response.setHeader("X-RateLimit-Window", "60s");

        // ── Continue ──────────────────────────────────────────────────────
        chain.doFilter(request, response);

        // ── Post-filter audit log ─────────────────────────────────────────
        long duration = System.currentTimeMillis() - start;
        String keyHash = apiKey != null ? sha256(apiKey) : null;
        auditLog.logApiAccess(clientIp, keyHash, request.getMethod(),
            path, response.getStatus(), duration);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private boolean validateKey(String key) {
        return key != null && key.equals(masterKey);
    }

    private String extractApiKey(HttpServletRequest request) {
        String header = request.getHeader("X-Api-Key");
        if (header != null && !header.isBlank()) return header;
        return request.getParameter("apiKey");
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "hash-error";
        }
    }
}
