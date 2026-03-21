package com.rationchain.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {

    Optional<Beneficiary> findByAadhaarHash(String hash);

    List<Beneficiary> findByStateCodeOrderByRegisteredAtDesc(String stateCode);

    List<Beneficiary> findAllByOrderByBlockHeightAsc();

    List<Beneficiary> findByStatus(String status);

    List<Beneficiary> findByMigrantTrue();

    boolean existsByAadhaarHash(String hash);

    long countByStatus(String status);

    /** Count query — avoids loading full entity list for migrants stats. */
    long countByMigrantTrue();

    @Query("SELECT b FROM Beneficiary b WHERE b.maskedAadhaar LIKE %:last4%")
    List<Beneficiary> findByAadhaarLast4(String last4);

    @Query("SELECT b FROM Beneficiary b ORDER BY b.registeredAt DESC")
    List<Beneficiary> findRecentFirst();
}
