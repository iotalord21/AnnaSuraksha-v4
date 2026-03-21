package com.rationchain.model;

/**
 * Immutable feature vector capturing all fraud signals for a beneficiary.
 * Used by FraudRiskScoringService to compute a 0-1 risk score.
 *
 * Each field is normalised to [0.0 – 1.0] before being weighted.
 */
public record FraudFeatureVector(

    // ── Identity signals ──────────────────────────────────────────────────
    /** 1.0 if Aadhaar hash appears in multiple states, else 0.0 */
    double duplicateAadhaarSignal,

    /** 1.0 if voter-ID hash is also duplicated cross-state, else 0.0 */
    double duplicateVoterIdSignal,

    // ── Ration-usage anomaly ──────────────────────────────────────────────
    /**
     * Ratio of actual entitlement to maximum legal entitlement for family size.
     * Values > 1.0 are clamped to 1.0 (over-entitlement). Normal range: 0–0.9.
     */
    double rationUsageAnomalyScore,

    /**
     * 1.0 if AAY category is assigned to a household < 3 people,
     * graded (0.5) if household >= 3 but rice_kg/member > 7 kg.
     */
    double categoryMismatchSignal,

    // ── Dealer / FPS signals ──────────────────────────────────────────────
    /**
     * Proportion of FPS deliveries for this beneficiary that were flagged.
     * 0.0 = no flagged deliveries; 1.0 = all flagged.
     */
    double dealerDiversionRate,

    /**
     * Number of distinct FPS shops this beneficiary has claimed from,
     * normalised by maximum seen in dataset (>2 shops is suspicious).
     */
    double multiShopClaimSignal,

    // ── Travel / velocity signals ─────────────────────────────────────────
    /**
     * 1.0 if an impossible-travel event was detected for this beneficiary
     * (claimed in two states within a physically impossible time window).
     */
    double impossibleTravelSignal,

    /**
     * 1.0 if beneficiary is not an ONORC migrant but has a cross-state claim.
     * 0.5 if ONORC migrant with suspicious velocity.
     */
    double crossStateFraudSignal,

    // ── Claim-frequency anomaly ───────────────────────────────────────────
    /**
     * Z-score of claim count (compared to state average), clamped to [0,1].
     * High values indicate abnormally frequent claiming.
     */
    double claimFrequencyAnomaly,

    // ── Metadata ──────────────────────────────────────────────────────────
    Long   beneficiaryId,
    String beneficiaryName,
    String stateCode,
    String category

) {
    /** Convenience: compute total raw signal strength (not yet weighted). */
    public double rawSignalSum() {
        return duplicateAadhaarSignal + duplicateVoterIdSignal
             + rationUsageAnomalyScore + categoryMismatchSignal
             + dealerDiversionRate + multiShopClaimSignal
             + impossibleTravelSignal + crossStateFraudSignal
             + claimFrequencyAnomaly;
    }
}
