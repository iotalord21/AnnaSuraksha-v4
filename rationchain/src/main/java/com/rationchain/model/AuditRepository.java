package com.rationchain.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditRepository extends JpaRepository<AuditReport, Long> {
    List<AuditReport> findTop5ByOrderByGeneratedAtDesc();
}
