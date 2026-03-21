package com.rationchain.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FpsDeliveryRepository extends JpaRepository<FpsDelivery, Long> {
    List<FpsDelivery> findByFpsShopIdOrderByDealerConfirmedAtDesc(String fpsShopId);
    List<FpsDelivery> findTop20ByOrderByDealerConfirmedAtDesc();
    List<FpsDelivery> findByFlaggedTrue();
    List<FpsDelivery> findByBeneficiaryStatus(String status);
    List<FpsDelivery> findByBeneficiaryId(Long beneficiaryId);

    /** Count of confirmed-clean deliveries — used for FPS dashboard stat card. */
    long countByBeneficiaryStatusAndFlagged(String beneficiaryStatus, Boolean flagged);

    /** Check for duplicate claim: same beneficiary, same month. */
    boolean existsByBeneficiaryIdAndDeliveryMonth(Long beneficiaryId, String deliveryMonth);

    /** Count deliveries for a beneficiary in a given month — for multi-claim detection. */
    long countByBeneficiaryIdAndDeliveryMonth(Long beneficiaryId, String deliveryMonth);
}
