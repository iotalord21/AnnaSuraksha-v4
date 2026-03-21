package com.rationchain.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.rationchain.model.*;
import com.rationchain.service.*;


import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fraud Analytics REST API — STEP 6.
 *
 * All endpoints are protected by ApiSecurityFilter (X-Api-Key required).
 *
 * Endpoints:
 *   GET  /api/fraud/high-risk-beneficiaries    — top scored HIGH-risk beneficiaries
 *   GET  /api/fraud/suspicious-dealers         — FPS shops with diversion flags
 *   GET  /api/fraud/duplicate-identities       — Layer 1 duplicate Aadhaar groups
 *   GET  /api/fraud/impossible-travel          — Layer 3 velocity flags
 *   GET  /api/fraud/score/{id}                 — Score + LLM explanation for one beneficiary
 *   GET  /api/fraud/summary                    — Overview counts + estimated losses
 */

@RestController
@RequestMapping("/api/fraud")

public class FraudAnalyticsController {
    private static final Logger log = LoggerFactory.getLogger(FraudAnalyticsController.class);


    private final BeneficiaryRepository    beneRepo;
    private final FpsDeliveryRepository    fpsRepo;
    private final FraudRiskScoringService  scoringSvc;
    private final FraudExplanationService  explanationSvc;
    private final ImpossibleTravelDetector travelDetector;
    private final GhostDetectionService    ghostSvc;

    // ── 1. High-risk beneficiaries ─────────────────────────────────────────

    public FraudAnalyticsController(com.rationchain.model.BeneficiaryRepository beneRepo, com.rationchain.model.FpsDeliveryRepository fpsRepo, FraudRiskScoringService scoringSvc, FraudExplanationService explanationSvc, ImpossibleTravelDetector travelDetector, GhostDetectionService ghostSvc) {
        this.beneRepo = beneRepo;
        this.fpsRepo = fpsRepo;
        this.scoringSvc = scoringSvc;
        this.explanationSvc = explanationSvc;
        this.travelDetector = travelDetector;
        this.ghostSvc = ghostSvc;
    }

    @GetMapping("/high-risk-beneficiaries")
    public Map<String, Object> highRiskBeneficiaries(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "false") boolean withExplanation) {

        List<FraudRiskScore> scores = scoringSvc.getHighRisk().stream()
            .limit(limit)
            .toList();

        List<Map<String, Object>> results = new ArrayList<>();
        for (FraudRiskScore s : scores) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("beneficiary_id",  s.subjectId());
            item.put("name",            s.subjectName());
            item.put("state",           s.stateCode());
            item.put("risk_score",      round(s.riskScore()));
            item.put("risk_level",      s.riskLevel().name());
            item.put("risk_badge",      s.riskLevel().badge());
            item.put("top_factors",     s.topFactors());
            item.put("computed_at",     s.computedAt());

            if (withExplanation) {
                item.put("ai_explanation", explanationSvc.explain(s));
            }
            results.add(item);
        }

        return buildResponse("HIGH_RISK_BENEFICIARIES", results.size(),
            Map.of("results", results, "total_high_risk", results.size()));
    }

    // ── 2. Suspicious dealers ──────────────────────────────────────────────

    @GetMapping("/suspicious-dealers")
    public Map<String, Object> suspiciousDealers(
            @RequestParam(defaultValue = "20") int limit) {

        List<FpsDelivery> flagged = fpsRepo.findByFlaggedTrue();

        // Group by fpsShopId, compute per-shop stats
        Map<String, List<FpsDelivery>> byShop = flagged.stream()
            .filter(d -> d.getFpsShopId() != null)
            .collect(Collectors.groupingBy(FpsDelivery::getFpsShopId));

        // Total deliveries per shop (for rate calculation)
        Map<String, Long> totalPerShop = fpsRepo.findAll().stream()
            .filter(d -> d.getFpsShopId() != null)
            .collect(Collectors.groupingBy(FpsDelivery::getFpsShopId, Collectors.counting()));

        List<Map<String, Object>> dealers = new ArrayList<>();
        for (Map.Entry<String, List<FpsDelivery>> e : byShop.entrySet()) {
            String shopId   = e.getKey();
            var    shopFlags = e.getValue();
            long   total    = totalPerShop.getOrDefault(shopId, 1L);
            double flagRate = shopFlags.size() / (double) total;

            FpsDelivery sample = shopFlags.get(0);
            long riceLost = shopFlags.stream()
                .mapToLong(d -> {
                    int claimed   = d.getDealerRiceKg()    != null ? d.getDealerRiceKg()    : 0;
                    int confirmed = d.getConfirmedRiceKg() != null ? d.getConfirmedRiceKg() : 0;
                    return Math.max(0, claimed - confirmed);
                }).sum();

            Map<String, Object> dealer = new LinkedHashMap<>();
            dealer.put("fps_shop_id",         shopId);
            dealer.put("state",               sample.getStateCode());
            dealer.put("operator",            sample.getFpsOperatorName());
            dealer.put("flagged_deliveries",  shopFlags.size());
            dealer.put("total_deliveries",    total);
            dealer.put("diversion_rate_pct",  round(flagRate * 100));
            dealer.put("rice_lost_kg_est",    riceLost);
            dealer.put("estimated_loss_rs",   riceLost * 30L);
            dealer.put("risk_level",          flagRate > 0.5 ? "HIGH" : flagRate > 0.2 ? "MEDIUM" : "LOW");
            dealer.put("sample_reason",       sample.getFlagReason());
            dealers.add(dealer);
        }

        dealers.sort((a, b) -> Double.compare(
            (Double) b.get("diversion_rate_pct"),
            (Double) a.get("diversion_rate_pct")));

        List<Map<String, Object>> topDealers = dealers.stream().limit(limit).toList();
        return buildResponse("SUSPICIOUS_DEALERS", topDealers.size(),
            Map.of("results", topDealers));
    }

    // ── 3. Duplicate identities ────────────────────────────────────────────

    @GetMapping("/duplicate-identities")
    public Map<String, Object> duplicateIdentities(
            @RequestParam(defaultValue = "50") int limit) {

        List<Beneficiary> all = beneRepo.findAll();

        Map<String, List<Beneficiary>> byAadhaar = all.stream()
            .filter(b -> b.getAadhaarHash() != null)
            .collect(Collectors.groupingBy(Beneficiary::getAadhaarHash));

        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map.Entry<String, List<Beneficiary>> e : byAadhaar.entrySet()) {
            if (e.getValue().size() <= 1) continue;

            List<Map<String, Object>> members = e.getValue().stream().map(b -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",              b.getId());
                m.put("name",            b.getFullName());
                m.put("state",           b.getStateCode());
                m.put("category",        b.getCategory());
                m.put("status",          b.getStatus());
                m.put("registered_at",   b.getRegisteredAt());
                m.put("masked_aadhaar",  b.getMaskedAadhaar());
                return m;
            }).toList();

            Set<String> states = e.getValue().stream()
                .map(Beneficiary::getStateCode).collect(Collectors.toSet());
            long deliveryMonthlyLoss = e.getValue().stream()
                .filter(b -> "GHOST".equals(b.getStatus()))
                .mapToLong(b -> estimateLoss(b.getCategory()))
                .sum();

            Map<String, Object> group = new LinkedHashMap<>();
            group.put("aadhaar_hash_prefix",  e.getKey().substring(0, 8) + "…");
            group.put("duplicate_count",      e.getValue().size());
            group.put("states_found_in",      states);
            group.put("cross_state",          states.size() > 1);
            group.put("deliveryMonthly_loss_rs",      deliveryMonthlyLoss);
            group.put("members",              members);
            groups.add(group);
        }

        groups.sort((a, b) -> Integer.compare(
            (Integer) b.get("duplicate_count"),
            (Integer) a.get("duplicate_count")));

        List<Map<String, Object>> top = groups.stream().limit(limit).toList();
        long totalDuplicates = groups.stream()
            .mapToLong(g -> ((Integer) g.get("duplicate_count")) - 1L)
            .sum();

        return buildResponse("DUPLICATE_IDENTITIES", top.size(), Map.of(
            "results",             top,
            "total_duplicate_groups", groups.size(),
            "total_ghost_entries",  totalDuplicates
        ));
    }

    // ── 4. Impossible travel ───────────────────────────────────────────────

    @GetMapping("/impossible-travel")
    public Map<String, Object> impossibleTravel(
            @RequestParam(defaultValue = "50") int limit) {

        List<Beneficiary> all = beneRepo.findAll();
        List<Map<String, Object>> events = new ArrayList<>();

        for (Beneficiary b : all) {
            if (b.getClaimState() == null || b.getStateCode() == null
                    || b.getClaimState().equals(b.getStateCode())) continue;
            if (b.getRegisteredAt() == null || b.getLastClaimAt() == null) continue;

            double hours = Math.abs(ChronoUnit.HOURS.between(
                b.getRegisteredAt(), b.getLastClaimAt()));

            ImpossibleTravelDetector.TravelAnalysis ta =
                travelDetector.analyse(b.getStateCode(), b.getClaimState(), hours);

            if (!ta.isImpossible()) continue;

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("beneficiary_id",    b.getId());
            event.put("name",              b.getFullName());
            event.put("home_state",        b.getStateCode());
            event.put("claim_state",       b.getClaimState());
            event.put("distance_km",       round(ta.distanceKm()));
            event.put("min_travel_hours",  round(ta.minTravelHours()));
            event.put("actual_gap_hours",  round(ta.actualHours()));
            event.put("is_onorc_migrant",  b.getMigrant());
            event.put("explanation",       ta.explanation());
            event.put("fraud_layer",       "LAYER_3_VELOCITY");
            event.put("registered_at",     b.getRegisteredAt());
            event.put("last_claim_at",     b.getLastClaimAt());
            events.add(event);
        }

        events.sort(Comparator.comparingDouble(
            (Map<String, Object> e) -> (Double) e.get("min_travel_hours")));

        List<Map<String, Object>> top = events.stream().limit(limit).toList();
        return buildResponse("IMPOSSIBLE_TRAVEL", top.size(), Map.of(
            "results",                top,
            "total_impossible_events", events.size()
        ));
    }

    // ── 5. Score + AI explanation for a single beneficiary ────────────────

    @GetMapping("/score/{id}")
    public Map<String, Object> scoreById(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean explain) {

        Optional<FraudRiskScore> scoreOpt = scoringSvc.scoreById(id);
        if (scoreOpt.isEmpty()) {
            return Map.of("error", "Beneficiary #" + id + " not found.");
        }

        FraudRiskScore score = scoreOpt.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("beneficiary_id", score.subjectId());
        result.put("name",           score.subjectName());
        result.put("state",          score.stateCode());
        result.put("risk_score",     round(score.riskScore()));
        result.put("risk_level",     score.riskLevel().name());
        result.put("risk_badge",     score.riskLevel().badge());
        result.put("top_factors",    score.topFactors());
        result.put("computed_at",    score.computedAt());

        var f = score.features();
        if (f != null) {
            result.put("feature_vector", Map.of(
                "duplicate_aadhaar",     round(f.duplicateAadhaarSignal()),
                "impossible_travel",     round(f.impossibleTravelSignal()),
                "cross_state_fraud",     round(f.crossStateFraudSignal()),
                "ration_anomaly",        round(f.rationUsageAnomalyScore()),
                "category_mismatch",     round(f.categoryMismatchSignal()),
                "dealer_diversion_rate", round(f.dealerDiversionRate()),
                "claim_freq_anomaly",    round(f.claimFrequencyAnomaly())
            ));
        }

        if (explain) {
            result.put("ai_explanation", explanationSvc.explain(score));
        }
        return result;
    }

    // ── 6. Summary overview ────────────────────────────────────────────────

    @GetMapping("/summary")
    public Map<String, Object> fraudSummary() {
        List<FraudRiskScore> all = scoringSvc.scoreAll();

        long highCount   = all.stream().filter(s -> s.riskLevel() == FraudRiskScore.RiskLevel.HIGH).count();
        long medCount    = all.stream().filter(s -> s.riskLevel() == FraudRiskScore.RiskLevel.MEDIUM).count();
        long lowCount    = all.stream().filter(s -> s.riskLevel() == FraudRiskScore.RiskLevel.LOW).count();
        long ghostCount  = beneRepo.countByStatus("GHOST");
        long fpsFlagged  = fpsRepo.findByFlaggedTrue().size();

        long deliveryMonthlyLossGhost = beneRepo.findByStatus("GHOST").stream()
            .mapToLong(b -> estimateLoss(b.getCategory())).sum();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scored_at",          LocalDateTime.now());
        summary.put("total_scored",       all.size());
        summary.put("high_risk_count",    highCount);
        summary.put("medium_risk_count",  medCount);
        summary.put("low_risk_count",     lowCount);
        summary.put("ghost_beneficiaries", ghostCount);
        summary.put("flagged_fps_shops",  fpsFlagged);
        summary.put("deliveryMonthly_loss_rs",    deliveryMonthlyLossGhost);
        summary.put("annual_loss_rs",     deliveryMonthlyLossGhost * 12);
        summary.put("avg_risk_score",     round(all.stream()
            .mapToDouble(FraudRiskScore::riskScore).average().orElse(0)));
        summary.put("detection_layers",   List.of(
            "LAYER_1_DUPLICATE_AADHAAR",
            "LAYER_2_STATISTICAL_ANOMALY",
            "LAYER_3_VELOCITY_CROSS_STATE",
            "LAYER_4_AI_GROQ_REASONING",
            "LAYER_5_FRAUD_RISK_SCORE"
        ));
        return buildResponse("FRAUD_SUMMARY", 1, summary);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Map<String, Object> buildResponse(String type, int count, Map<String, Object> data) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("api",           "AnnaSuraksha Fraud Analytics");
        resp.put("query_type",    type);
        resp.put("result_count",  count);
        resp.put("generated_at",  LocalDateTime.now());
        resp.putAll(data);
        return resp;
    }

    private double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private long estimateLoss(String category) {
        if (category == null) return 96L;
        return switch (category) {
            case "AAY" -> 350L;
            case "BPL" -> 96L;
            case "PHH" -> 75L;
            case "APL" -> 60L;
            default    -> 96L;
        };
    }
}
