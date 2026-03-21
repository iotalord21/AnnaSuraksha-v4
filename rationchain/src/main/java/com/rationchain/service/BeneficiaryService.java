package com.rationchain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.rationchain.model.*;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;


@Service

public class BeneficiaryService {
    private static final Logger log = LoggerFactory.getLogger(BeneficiaryService.class);


    private final BeneficiaryRepository beneRepo;
    private final ClaimRepository       claimRepo;
    private final BlockchainService     chain;
    private final ReferenceDataService  refData;

    public BeneficiaryService(BeneficiaryRepository beneRepo,
                               ClaimRepository claimRepo,
                               BlockchainService chain,
                               ReferenceDataService refData) {
        this.beneRepo  = beneRepo;
        this.claimRepo = claimRepo;
        this.chain     = chain;
        this.refData   = refData;
    }

    /**
     * Register a new beneficiary — mines a block.
     *
     * Duplicate Aadhaar detection (Layer 1 pre-check):
     *   - The EXISTING record is the legitimate one — never touch it.
     *   - The NEW registration is the fraud — it is saved as GHOST immediately.
     *   - The full ghost detection pass (GhostDetectionService.runAllLayers) will
     *     also catch this pair, but the pre-check ensures the UI reflects correct
     *     status right away without needing a separate detect-ghosts run.
     */
    @Transactional
    public Beneficiary register(String aadhaarRaw, String name, String stateCode,
                                String category, int familySize, String phone,
                                boolean migrant) {

        String aadhaarHash   = chain.hashAadhaar(aadhaarRaw);
        String maskedAadhaar = mask(aadhaarRaw);

        // ── Layer 1 pre-check: duplicate Aadhaar? ──────────────────────
        boolean isDuplicate        = beneRepo.existsByAadhaarHash(aadhaarHash);
        String  initialStatus      = "ACTIVE";
        String  initialGhostReason = null;
        String  initialGhostLayer  = null;
        LocalDateTime initialFlaggedAt = null;

        if (isDuplicate) {
            String existingState = beneRepo.findByAadhaarHash(aadhaarHash)
                .map(Beneficiary::getStateCode).orElse("unknown");
            initialStatus      = "GHOST";
            initialGhostReason = "Duplicate Aadhaar — already registered in " + existingState;
            initialGhostLayer  = "LAYER_1_DUPLICATE";
            initialFlaggedAt   = LocalDateTime.now();
            log.warn("Layer 1 pre-check: duplicate Aadhaar at registration — new record flagged GHOST ({})", name);
        }

        // ── Calculate entitlement ───────────────────────────────────────
        Map<String, Integer> ent = refData.calculateEntitlement(category, familySize);

        // ── Mine block ─────────────────────────────────────────────────
        String prevHash  = chain.getLatestHash();
        long   height    = chain.getNextBlockHeight();
        String blockHash = chain.computeBlockHash(prevHash, aadhaarHash, name, stateCode, category);
        String fpsShopId = "FPS-" + stateCode + "-" + String.format("%04d", (int)(Math.random() * 9999));

        Beneficiary b = Beneficiary.builder()
            .aadhaarHash(aadhaarHash)
            .maskedAadhaar(maskedAadhaar)
            .fullName(name)
            .stateCode(stateCode)
            .stateName(refData.stateName(stateCode))
            .district(stateCode + "-District")
            .category(category)
            .familySize(familySize)
            .phone(phone)
            .riceKg(ent.get("rice"))
            .wheatKg(ent.get("wheat"))
            .sugarKg(ent.get("sugar"))
            .keroseneL(category.equals("BPL") || category.equals("AAY") ? 3 : 0)
            .fpsShopId(fpsShopId)
            .status(initialStatus)
            .ghostReason(initialGhostReason)
            .ghostLayer(initialGhostLayer)
            .flaggedAt(initialFlaggedAt)
            .migrant(migrant)
            .blockHash(blockHash)
            .prevBlockHash(prevHash)
            .blockHeight(height)
            .build();

        return beneRepo.save(b);
    }

    /** Record a ration claim with biometric verification simulation */
    @Transactional
    public ClaimRecord recordClaim(Long beneficiaryId, String claimStateCode,
                                   boolean biometricOk) {
        Beneficiary b = beneRepo.findById(beneficiaryId)
            .orElseThrow(() -> new RuntimeException("Beneficiary not found"));

        if ("GHOST".equals(b.getStatus())) {
            throw new RuntimeException("Claim blocked — beneficiary flagged as ghost");
        }

        if (claimStateCode != null && !claimStateCode.isBlank()) {
            b.setClaimState(claimStateCode);
        }
        b.setLastClaimAt(LocalDateTime.now());
        b.setClaimCount(b.getClaimCount() + 1);
        beneRepo.save(b);

        long epochMs = System.currentTimeMillis();
        ClaimRecord claim = ClaimRecord.builder()
            .beneficiaryId(beneficiaryId)
            .beneficiaryName(b.getFullName())
            .aadhaarHash(b.getAadhaarHash())
            .stateCode(b.getStateCode())
            .claimStateCode(claimStateCode != null ? claimStateCode : b.getStateCode())
            .fpsShopId(b.getFpsShopId())
            .category(b.getCategory())
            .riceKg(b.getRiceKg())
            .wheatKg(b.getWheatKg())
            .sugarKg(b.getSugarKg())
            .txHash(chain.generateTxHash(beneficiaryId, epochMs))
            .blockHeight(b.getBlockHeight())
            .biometricStatus(biometricOk ? "VERIFIED" : "FAILED")
            .build();

        return claimRepo.save(claim);
    }

    public List<Beneficiary> searchByAadhaarLast4(String last4) {
        return beneRepo.findByAadhaarLast4(last4);
    }

    public List<Beneficiary> getAll() {
        return beneRepo.findRecentFirst();
    }

    public Optional<Beneficiary> findById(Long id) {
        return beneRepo.findById(id);
    }

    public List<Beneficiary> getByState(String stateCode) {
        return beneRepo.findByStateCodeOrderByRegisteredAtDesc(stateCode);
    }

    public List<ClaimRecord> getRecentClaims() {
        return claimRepo.findTop20ByOrderByClaimedAtDesc();
    }

    public List<ClaimRecord> getClaimsForBeneficiary(Long id) {
        return claimRepo.findByBeneficiaryIdOrderByClaimedAtDesc(id);
    }

    /**
     * Stats — uses count queries; never loads full entity lists into memory.
     */
    public Map<String, Long> getStats() {
        Map<String, Long> s = new LinkedHashMap<>();
        s.put("total",    beneRepo.count());
        s.put("active",   beneRepo.countByStatus("ACTIVE"));
        s.put("ghosts",   beneRepo.countByStatus("GHOST"));
        s.put("migrants", beneRepo.countByMigrantTrue());
        return s;
    }

    private String mask(String aadhaar) {
        String digits = aadhaar.replaceAll("[^0-9]", "");
        if (digits.length() < 4) return "XXXX-XXXX-" + digits;
        return "XXXX-XXXX-" + digits.substring(digits.length() - 4);
    }
}
