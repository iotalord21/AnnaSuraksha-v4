package com.rationchain.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClaimRepository extends JpaRepository<ClaimRecord, Long> {
    List<ClaimRecord> findByBeneficiaryIdOrderByClaimedAtDesc(Long beneficiaryId);
    List<ClaimRecord> findTop20ByOrderByClaimedAtDesc();
}

