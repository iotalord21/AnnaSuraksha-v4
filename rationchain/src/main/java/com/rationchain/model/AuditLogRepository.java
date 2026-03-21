package com.rationchain.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop50ByOrderByOccurredAtDesc();

    List<AuditLog> findByEventTypeOrderByOccurredAtDesc(AuditLog.EventType eventType);

    List<AuditLog> findByClientIpOrderByOccurredAtDesc(String ip);

    long countByClientIpAndOccurredAtAfter(String ip, LocalDateTime since);

    @Query("SELECT a FROM AuditLog a ORDER BY a.id DESC LIMIT 1")
    Optional<AuditLog> findLatestLog();

    List<AuditLog> findByResourceIdAndResourceType(Long resourceId, String resourceType);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.eventType = 'AUTH_FAILURE' AND a.occurredAt > :since")
    long countAuthFailuresSince(LocalDateTime since);
}
