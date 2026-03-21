package com.rationchain.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fraud risk assessment result for a single beneficiary or FPS dealer.
 * Returned by FraudRiskScoringService.
 */
public record FraudRiskScore(

    Long              subjectId,
    String            subjectName,
    String            subjectType,        // "BENEFICIARY" | "DEALER"
    String            stateCode,

    /** Weighted composite score ∈ [0.0, 1.0]. Higher = more suspicious. */
    double            riskScore,

    /** LOW (<0.35), MEDIUM (0.35–0.65), HIGH (>0.65) */
    RiskLevel         riskLevel,

    /** Ordered list of top contributing fraud signals. */
    List<String>      topFactors,

    /** Raw feature vector used to produce the score (for auditability). */
    FraudFeatureVector features,

    LocalDateTime     computedAt

) {
    public enum RiskLevel {
        LOW, MEDIUM, HIGH;

        public static RiskLevel from(double score) {
            if (score < 0.35) return LOW;
            if (score < 0.65) return MEDIUM;
            return HIGH;
        }

        public String badge() {
            return switch (this) {
                case LOW    -> "🟢 LOW";
                case MEDIUM -> "🟡 MEDIUM";
                case HIGH   -> "🔴 HIGH";
            };
        }
    }

    /** Compact summary string for logs and API responses. */
    public String summary() {
        return "[%s] %s – score=%.3f (%s) – factors: %s"
            .formatted(subjectType, subjectName, riskScore,
                       riskLevel.name(), String.join(", ", topFactors));
    }
}
