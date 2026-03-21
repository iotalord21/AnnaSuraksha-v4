package com.rationchain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.rationchain.model.*;


import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fraud Risk Scoring Engine — produces a 0.0–1.0 composite fraud score
 * for every beneficiary in the database.
 *
 * Architecture:
 *  ┌────────────────────────────────────┐
 *  │  FraudFeatureExtractor             │  per-beneficiary raw feature values
 *  │  (this service, extractFeatures)  │
 *  └───────────────┬────────────────────┘
 *                  │
 *  ┌───────────────▼────────────────────┐
 *  │  FraudModel                        │  weighted linear combination
 *  │  (this service, computeScore)      │  + non-linear boosts for critical signals
 *  └───────────────┬────────────────────┘
 *                  │
 *  ┌───────────────▼────────────────────┐
 *  │  FraudRiskScore                    │  0.0–1.0 + LOW/MEDIUM/HIGH + topFactors
 *  └────────────────────────────────────┘
 *
 * Weights (sum to 1.0):
 *   duplicateAadhaar       0.30  ← strongest dedup signal
 *   impossibleTravel       0.25  ← physically impossible = near-certain fraud
 *   crossStateFraud        0.15  ← non-ONORC cross-state claim
 *   rationUsageAnomaly     0.10  ← over-entitlement
 *   categoryMismatch       0.08  ← AAY for tiny household
 *   dealerDiversionRate    0.07  ← dealer flagged deliveries
 *   claimFrequencyAnomaly  0.03  ← abnormal claim rate
 *   multiShopClaim         0.02
 *
 * Non-linear boost: if ANY of {duplicateAadhaar, impossibleTravel} == 1.0,
 * the final score is clamped to at least 0.70 (HIGH territory).
 */

@Service

public class FraudRiskScoringService {
    private static final Logger log = LoggerFactory.getLogger(FraudRiskScoringService.class);


    private final BeneficiaryRepository    beneRepo;
    private final FpsDeliveryRepository    fpsRepo;
    private final ImpossibleTravelDetector travelDetector;

    public FraudRiskScoringService(com.rationchain.model.BeneficiaryRepository beneRepo, com.rationchain.model.FpsDeliveryRepository fpsRepo, ImpossibleTravelDetector travelDetector) {
        this.beneRepo = beneRepo;
        this.fpsRepo = fpsRepo;
        this.travelDetector = travelDetector;
    }

    // ── Weights (must sum to 1.0) ──────────────────────────────────────────
    private static final double W_DUP_AADHAAR      = 0.30;
    private static final double W_TRAVEL           = 0.25;
    private static final double W_CROSS_STATE      = 0.15;
    private static final double W_RATION_ANOMALY   = 0.10;
    private static final double W_CAT_MISMATCH     = 0.08;
    private static final double W_DEALER_DIVERSION = 0.07;
    private static final double W_CLAIM_FREQ       = 0.03;
    private static final double W_MULTI_SHOP       = 0.02;

    /** Score every ACTIVE beneficiary and return sorted (highest risk first). */
    public List<FraudRiskScore> scoreAll() {
        List<Beneficiary> all = beneRepo.findAll();

        // Pre-compute: duplicate Aadhaar hash index
        Map<String, Long> aadhaarCount = all.stream()
            .filter(b -> b.getAadhaarHash() != null)
            .collect(Collectors.groupingBy(Beneficiary::getAadhaarHash, Collectors.counting()));

        // Pre-compute: state claim frequency statistics (mean + stddev)
        Map<String, DoubleSummaryStatistics> stateClaimStats = all.stream()
            .filter(b -> b.getClaimCount() != null && b.getStateCode() != null)
            .collect(Collectors.groupingBy(
                Beneficiary::getStateCode,
                Collectors.summarizingDouble(b -> b.getClaimCount())
            ));

        // Pre-compute: dealer delivery flags per beneficiary
        Map<Long, long[]> dealerFlags = buildDealerFlagIndex(all);

        List<FraudRiskScore> scores = new ArrayList<>();
        for (Beneficiary b : all) {
            try {
                FraudFeatureVector features = extractFeatures(b, aadhaarCount,
                                                               stateClaimStats, dealerFlags);
                FraudRiskScore score = computeScore(b, features);
                scores.add(score);
            } catch (Exception e) {
                log.warn("Scoring failed for beneficiary {}: {}", b.getId(), e.getMessage());
            }
        }

        scores.sort(Comparator.comparingDouble(FraudRiskScore::riskScore).reversed());
        log.info("Fraud scoring complete — {} beneficiaries scored. HIGH: {}, MEDIUM: {}, LOW: {}",
            scores.size(),
            scores.stream().filter(s -> s.riskLevel() == FraudRiskScore.RiskLevel.HIGH).count(),
            scores.stream().filter(s -> s.riskLevel() == FraudRiskScore.RiskLevel.MEDIUM).count(),
            scores.stream().filter(s -> s.riskLevel() == FraudRiskScore.RiskLevel.LOW).count()
        );
        return scores;
    }

    /** Score a single beneficiary by ID. */
    public Optional<FraudRiskScore> scoreById(Long beneficiaryId) {
        return beneRepo.findById(beneficiaryId).map(b -> {
            List<Beneficiary> all = beneRepo.findAll();
            Map<String, Long> aadhaarCount = all.stream()
                .filter(x -> x.getAadhaarHash() != null)
                .collect(Collectors.groupingBy(Beneficiary::getAadhaarHash, Collectors.counting()));
            Map<String, DoubleSummaryStatistics> stateClaimStats = all.stream()
                .filter(x -> x.getClaimCount() != null && x.getStateCode() != null)
                .collect(Collectors.groupingBy(
                    Beneficiary::getStateCode,
                    Collectors.summarizingDouble(x -> x.getClaimCount())));
            Map<Long, long[]> dealerFlags = buildDealerFlagIndex(all);
            FraudFeatureVector features = extractFeatures(b, aadhaarCount, stateClaimStats, dealerFlags);
            return computeScore(b, features);
        });
    }

    /** Return only HIGH-risk beneficiaries. */
    public List<FraudRiskScore> getHighRisk() {
        return scoreAll().stream()
            .filter(s -> s.riskLevel() == FraudRiskScore.RiskLevel.HIGH)
            .toList();
    }

    /** Return only MEDIUM-or-above risk beneficiaries. */
    public List<FraudRiskScore> getMediumAndAbove() {
        return scoreAll().stream()
            .filter(s -> s.riskLevel() != FraudRiskScore.RiskLevel.LOW)
            .toList();
    }

    // ── Feature extraction ─────────────────────────────────────────────────

    FraudFeatureVector extractFeatures(
            Beneficiary b,
            Map<String, Long> aadhaarCount,
            Map<String, DoubleSummaryStatistics> stateClaimStats,
            Map<Long, long[]> dealerFlags) {

        // 1. Duplicate Aadhaar signal
        double dupAadhaar = (b.getAadhaarHash() != null
            && aadhaarCount.getOrDefault(b.getAadhaarHash(), 0L) > 1) ? 1.0 : 0.0;

        // 2. Duplicate voter ID (approximated by ghostLayer label)
        double dupVoterId = "LAYER_1_DUPLICATE".equals(b.getGhostLayer()) ? 0.5 : 0.0;

        // 3. Ration usage anomaly: ratio of rice entitlement to NFSA max for family
        double rationAnomaly = 0.0;
        if (b.getRiceKg() != null && b.getFamilySize() != null && b.getFamilySize() > 0
                && !"AAY".equals(b.getCategory())) {
            double maxLegal = b.getFamilySize() * 7.0;
            double ratio    = b.getRiceKg() / maxLegal;
            rationAnomaly   = Math.min(1.0, Math.max(0.0, (ratio - 0.9) * 10.0)); // only anomalous above 90%
        }

        // 4. Category mismatch: AAY for tiny household
        double catMismatch = 0.0;
        if ("AAY".equals(b.getCategory()) && b.getFamilySize() != null) {
            if      (b.getFamilySize() < 2) catMismatch = 1.0;
            else if (b.getFamilySize() < 3) catMismatch = 0.6;
        }

        // 5. Dealer diversion rate from FPS delivery table
        long[] flagInfo    = dealerFlags.getOrDefault(b.getId(), new long[]{0, 0});
        double divRate     = flagInfo[1] == 0 ? 0.0 : (double) flagInfo[0] / flagInfo[1];

        // 6. Multi-shop claim (claim count as proxy — high count = suspicious)
        int    claimCount  = b.getClaimCount() != null ? b.getClaimCount() : 0;
        double multiShop   = claimCount > 24 ? Math.min(1.0, (claimCount - 24) / 12.0) : 0.0;

        // 7. Impossible travel signal
        double travelSignal = 0.0;
        if (b.getClaimState() != null && b.getStateCode() != null
                && !b.getClaimState().equals(b.getStateCode())
                && b.getRegisteredAt() != null && b.getLastClaimAt() != null) {
            double hours = Math.abs(ChronoUnit.HOURS.between(
                b.getRegisteredAt(), b.getLastClaimAt()));
            ImpossibleTravelDetector.TravelAnalysis ta =
                travelDetector.analyse(b.getStateCode(), b.getClaimState(), hours);
            travelSignal = ta.isImpossible() ? 1.0 : 0.0;
        }

        // 8. Cross-state fraud signal
        double crossState = 0.0;
        if (b.getClaimState() != null && !b.getClaimState().isBlank()
                && !b.getClaimState().equals(b.getStateCode())) {
            crossState = Boolean.TRUE.equals(b.getMigrant()) ? 0.3 : 1.0;
        }

        // 9. Claim frequency anomaly (z-score normalised)
        double claimFreq = 0.0;
        DoubleSummaryStatistics stats = stateClaimStats.get(b.getStateCode());
        if (stats != null && stats.getCount() > 1 && stats.getAverage() > 0) {
            double mean    = stats.getAverage();
            double count   = stats.getCount();
            double sumSq   = stats.getSum(); // approximation — we use count/avg for stddev
            double stddev  = Math.sqrt(mean);  // simplified Poisson σ = √μ
            double z       = (claimCount - mean) / Math.max(stddev, 1.0);
            claimFreq      = Math.min(1.0, Math.max(0.0, (z - 2.0) / 3.0)); // flag z > 2
        }

        return new FraudFeatureVector(
            dupAadhaar, dupVoterId, rationAnomaly, catMismatch,
            divRate, multiShop, travelSignal, crossState, claimFreq,
            b.getId(), b.getFullName(), b.getStateCode(), b.getCategory()
        );
    }

    // ── Score computation ──────────────────────────────────────────────────

    FraudRiskScore computeScore(Beneficiary b, FraudFeatureVector f) {
        double raw =
            f.duplicateAadhaarSignal()  * W_DUP_AADHAAR
          + f.impossibleTravelSignal()  * W_TRAVEL
          + f.crossStateFraudSignal()   * W_CROSS_STATE
          + f.rationUsageAnomalyScore() * W_RATION_ANOMALY
          + f.categoryMismatchSignal()  * W_CAT_MISMATCH
          + f.dealerDiversionRate()     * W_DEALER_DIVERSION
          + f.claimFrequencyAnomaly()   * W_CLAIM_FREQ
          + f.multiShopClaimSignal()    * W_MULTI_SHOP;

        // Non-linear boost: certain signals guarantee HIGH classification
        if (f.duplicateAadhaarSignal() == 1.0 || f.impossibleTravelSignal() == 1.0) {
            raw = Math.max(raw, 0.70);
        }

        // Clamp to [0, 1]
        double score = Math.min(1.0, Math.max(0.0, raw));

        List<String> topFactors = buildTopFactors(f);

        return new FraudRiskScore(
            b.getId(), b.getFullName(), "BENEFICIARY", b.getStateCode(),
            score, FraudRiskScore.RiskLevel.from(score),
            topFactors, f, LocalDateTime.now()
        );
    }

    private List<String> buildTopFactors(FraudFeatureVector f) {
        List<String> factors = new ArrayList<>();
        if (f.duplicateAadhaarSignal()  > 0) factors.add("DUPLICATE_AADHAAR("      + fmt(f.duplicateAadhaarSignal())  + ")");
        if (f.impossibleTravelSignal()  > 0) factors.add("IMPOSSIBLE_TRAVEL("      + fmt(f.impossibleTravelSignal())  + ")");
        if (f.crossStateFraudSignal()   > 0) factors.add("CROSS_STATE_FRAUD("      + fmt(f.crossStateFraudSignal())   + ")");
        if (f.rationUsageAnomalyScore() > 0) factors.add("RATION_USAGE_ANOMALY("   + fmt(f.rationUsageAnomalyScore()) + ")");
        if (f.categoryMismatchSignal()  > 0) factors.add("CATEGORY_MISMATCH("      + fmt(f.categoryMismatchSignal())  + ")");
        if (f.dealerDiversionRate()     > 0) factors.add("DEALER_DIVERSION("       + fmt(f.dealerDiversionRate())     + ")");
        if (f.claimFrequencyAnomaly()   > 0) factors.add("CLAIM_FREQ_ANOMALY("     + fmt(f.claimFrequencyAnomaly())   + ")");
        factors.sort(Comparator.reverseOrder());
        return factors.stream().limit(4).toList();
    }

    private String fmt(double v) { return String.format("%.2f", v); }

    // ── Helper: build dealer flag index ───────────────────────────────────

    private Map<Long, long[]> buildDealerFlagIndex(List<Beneficiary> all) {
        // [flaggedCount, totalCount] per beneficiary
        Map<Long, long[]> index = new HashMap<>();
        try {
            List<FpsDelivery> deliveries = fpsRepo.findAll();
            for (FpsDelivery d : deliveries) {
                if (d.getBeneficiaryId() == null) continue;
                index.computeIfAbsent(d.getBeneficiaryId(), k -> new long[]{0, 0});
                index.get(d.getBeneficiaryId())[1]++;
                if (Boolean.TRUE.equals(d.getFlagged())) {
                    index.get(d.getBeneficiaryId())[0]++;
                }
            }
        } catch (Exception e) {
            log.warn("Could not build dealer flag index: {}", e.getMessage());
        }
        return index;
    }
}
