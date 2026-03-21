package com.rationchain.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SupplyChainRepository extends JpaRepository<SupplyChainEntry, Long> {

    List<SupplyChainEntry> findByShipmentIdOrderByEntryHeightAsc(String shipmentId);

    List<SupplyChainEntry> findByFpsShopIdOrderByCreatedAtDesc(String fpsShopId);

    List<SupplyChainEntry> findByDiscrepancyFlaggedTrueOrderByCreatedAtDesc();

    List<SupplyChainEntry> findByStateCodeOrderByCreatedAtDesc(String stateCode);

    @Query("SELECT s FROM SupplyChainEntry s ORDER BY s.entryHeight DESC LIMIT 1")
    Optional<SupplyChainEntry> findLatestEntry();

    @Query("SELECT s FROM SupplyChainEntry s WHERE s.discrepancyFlagged = true ORDER BY s.createdAt DESC")
    List<SupplyChainEntry> findAllFlagged();

    long countByDiscrepancyFlaggedTrue();

    @Query("SELECT SUM(s.riceDiscrepancyKg) FROM SupplyChainEntry s WHERE s.discrepancyFlagged = true")
    Long sumRiceDiscrepancy();

    @Query("SELECT SUM(s.wheatDiscrepancyKg) FROM SupplyChainEntry s WHERE s.discrepancyFlagged = true")
    Long sumWheatDiscrepancy();
}
