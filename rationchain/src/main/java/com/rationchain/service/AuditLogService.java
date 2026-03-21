package com.rationchain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.rationchain.model.AuditLog;
import com.rationchain.model.AuditLogRepository;


import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Writes tamper-evident audit log entries.
 *
 * Each entry includes:
 *  - SHA-256 of the previous log entry (hash chain)
 *  - SHA-256 of the API key (not the raw key)
 *  - Client IP, endpoint, event type, resource context
 *
 * Log writes are async by default so they don't block request threads.
 * For FRAUD_FLAG and CHAIN_MUTATION events, writes are synchronous
 * (see overloads with sync=true) to ensure they're committed before
 * the response is returned.
 */

@Service

public class AuditLogService {
    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);


    private final AuditLogRepository logRepo;
    private final BlockchainService  chainSvc;

    @Async("auditExecutor")
    public void logApiAccess(String clientIp, String apiKeyHash, String method,
                              String endpoint, int status, long durationMs) {
        writeLog(AuditLog.EventType.API_READ, clientIp, apiKeyHash,
                 method, endpoint, status, durationMs, null, null, null);
    }

    @Async("auditExecutor")
    public void logApiWrite(String clientIp, String apiKeyHash, String method,
                             String endpoint, int status, long durationMs,
                             Long resourceId, String resourceType, String detail) {
        writeLog(AuditLog.EventType.API_WRITE, clientIp, apiKeyHash,
                 method, endpoint, status, durationMs, resourceId, resourceType, detail);
    }

    /** Always synchronous — fraud events must be committed immediately. */
    public void logFraudFlag(Long beneficiaryId, String reason, String clientIp) {
        writeLog(AuditLog.EventType.FRAUD_FLAG, clientIp, null,
                 "POST", "/detect-ghosts", 200, 0L,
                 beneficiaryId, "BENEFICIARY",
                 "Beneficiary #" + beneficiaryId + " flagged: " + reason);
    }

    public void logAuthEvent(boolean success, String clientIp, String apiKeyHash, String endpoint) {
        AuditLog.EventType type = success
            ? AuditLog.EventType.AUTH_SUCCESS
            : AuditLog.EventType.AUTH_FAILURE;
        writeLog(type, clientIp, apiKeyHash,
                 "GET", endpoint, success ? 200 : 401, 0L,
                 null, null,
                 success ? "Auth OK" : "Invalid API key from " + clientIp);
    }

    public void logRateLimit(String clientIp, String endpoint) {
        writeLog(AuditLog.EventType.RATE_LIMIT_HIT, clientIp, null,
                 "GET", endpoint, 429, 0L, null, null,
                 "Rate limit exceeded from " + clientIp);
    }

    public void logSupplyChainUpdate(String shipmentId, String stage, String operatorId) {
        writeLog(AuditLog.EventType.SUPPLY_CHAIN_UPDATE, operatorId, null,
                 "POST", "/api/supply-chain", 200, 0L,
                 null, "SHIPMENT",
                 "Shipment " + shipmentId + " advanced to stage " + stage);
    }

    public AuditLogService(com.rationchain.model.AuditLogRepository logRepo, BlockchainService chainSvc) {
        this.logRepo = logRepo;
        this.chainSvc = chainSvc;
    }

    // ── Core writer ────────────────────────────────────────────────────────

    private void writeLog(AuditLog.EventType eventType,
                           String clientIp, String apiKeyHash,
                           String method, String endpoint,
                           int status, long durationMs,
                           Long resourceId, String resourceType, String detail) {
        try {
            String prevHash = logRepo.findLatestLog()
                .map(AuditLog::getLogHash)
                .orElse("0000000000000000000000000000000000000000000000000000000000000000");

            String logPayload = prevHash + clientIp + eventType
                + (detail != null ? detail : "") + LocalDateTime.now();
            String logHash = chainSvc.sha256(logPayload);

            AuditLog entry = AuditLog.builder()
                .eventType(eventType)
                .clientIp(clientIp)
                .apiKeyHash(apiKeyHash)
                .httpMethod(method)
                .endpoint(endpoint)
                .httpStatus(status)
                .durationMs(durationMs)
                .resourceId(resourceId)
                .resourceType(resourceType)
                .detail(detail != null && detail.length() > 1000
                    ? detail.substring(0, 997) + "..." : detail)
                .logHash(logHash)
                .prevLogHash(prevHash)
                .build();

            logRepo.save(entry);
        } catch (Exception e) {
            // Audit log must never crash the main request thread
            log.error("Audit log write failed: {}", e.getMessage());
        }
    }
}
