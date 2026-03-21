package com.rationchain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rationchain.model.Beneficiary;
import com.rationchain.model.BeneficiaryRepository;
import com.rationchain.model.FraudRiskScore;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * FraudExplanationService — generates natural-language explanations
 * for individual fraud cases using a Groq LLM.
 *
 * Design:
 *  - Takes a FraudRiskScore (with FraudFeatureVector) as input.
 *  - Packages all fraud signals into a structured prompt.
 *  - Returns a concise 3-sentence human-readable explanation suitable
 *    for a government auditor's report.
 *
 * Fallback: if the LLM is unavailable, generates a deterministic
 * rule-based explanation from the feature vector. This ensures the
 * system is always functional even without a Groq API key.
 *
 * Example output:
 *  "Beneficiary Ramesh Kumar (ID #42, Bihar) has been flagged HIGH risk
 *   (score: 0.87) due to two critical signals: (1) a duplicate Aadhaar
 *   hash found in both Bihar and Uttar Pradesh, indicating ghost registration;
 *   (2) a ration claim was recorded in Uttar Pradesh only 1.5 hours after
 *   a home-state activity in Bihar — physically impossible given the 750 km
 *   distance. Estimated deliveryMonthly fraud loss: ₹350."
 */

@Service

public class FraudExplanationService {
    private static final Logger log = LoggerFactory.getLogger(FraudExplanationService.class);


    private final BeneficiaryRepository beneRepo;
    private final ObjectMapper          mapper = new ObjectMapper();

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.model}")
    private String model;

    private static final String SYSTEM_PROMPT = """
        You are an AI fraud analyst for India's Public Distribution System (PDS).
        
        Given a structured fraud risk record for a beneficiary, generate a concise
        3-sentence natural-language explanation suitable for a government audit report.
        
        Requirements:
        - Sentence 1: Who is flagged, their risk level and score.
        - Sentence 2: The 1-3 specific fraud signals detected (reference actual values).
        - Sentence 3: Policy implication + estimated deliveryMonthly fraud value in ₹.
        
        Use formal English. Reference Indian PDS law/NFSA where relevant.
        Do NOT output markdown — plain prose only.
        Be specific about numbers: distances, hours, quantities, rupee values.
        """;

    /**
     * Generate a natural-language fraud explanation for a single beneficiary.
     *
     * @param score  The fraud risk score (must include features and beneficiaryId)
     * @return       Plain-text explanation (3 sentences)
     */
    public String explain(FraudRiskScore score) {
        Optional<Beneficiary> bOpt = beneRepo.findById(score.subjectId());
        Beneficiary b = bOpt.orElse(null);

        String context = buildContext(score, b);
        try {
            return callGroq(context);
        } catch (Exception e) {
            log.warn("Groq unavailable for explanation of beneficiary {} — using fallback: {}",
                score.subjectId(), e.getMessage());
            return buildFallbackExplanation(score, b);
        }
    }

    /**
     * Batch: explain all HIGH-risk beneficiaries up to a limit.
     */
    public Map<Long, String> explainBatch(List<FraudRiskScore> scores, int limit) {
        Map<Long, String> results = new LinkedHashMap<>();
        scores.stream()
            .filter(s -> s.riskLevel() == FraudRiskScore.RiskLevel.HIGH)
            .limit(limit)
            .forEach(s -> results.put(s.subjectId(), explain(s)));
        return results;
    }

    public FraudExplanationService(com.rationchain.model.BeneficiaryRepository beneRepo) {
        this.beneRepo = beneRepo;
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private String buildContext(FraudRiskScore score, Beneficiary b) {
        ObjectNode ctx = mapper.createObjectNode();
        ctx.put("beneficiary_id",   score.subjectId());
        ctx.put("name",             score.subjectName());
        ctx.put("state",            score.stateCode());
        ctx.put("risk_score",       String.format("%.3f", score.riskScore()));
        ctx.put("risk_level",       score.riskLevel().name());

        if (b != null) {
            ctx.put("category",     b.getCategory() != null ? b.getCategory() : "unknown");
            ctx.put("family_size",  b.getFamilySize() != null ? b.getFamilySize() : 0);
            ctx.put("rice_kg_ent",  b.getRiceKg()     != null ? b.getRiceKg()     : 0);
            ctx.put("claim_count",  b.getClaimCount() != null ? b.getClaimCount() : 0);
            ctx.put("is_migrant",   Boolean.TRUE.equals(b.getMigrant()));
            ctx.put("claim_state",  b.getClaimState() != null ? b.getClaimState() : "N/A");
            ctx.put("status",       b.getStatus());
            if (b.getGhostReason() != null) ctx.put("ghost_reason", b.getGhostReason());
        }

        var f = score.features();
        if (f != null) {
            ObjectNode signals = ctx.putObject("fraud_signals");
            signals.put("duplicate_aadhaar",     f.duplicateAadhaarSignal());
            signals.put("impossible_travel",     f.impossibleTravelSignal());
            signals.put("cross_state_fraud",     f.crossStateFraudSignal());
            signals.put("ration_usage_anomaly",  f.rationUsageAnomalyScore());
            signals.put("category_mismatch",     f.categoryMismatchSignal());
            signals.put("dealer_diversion_rate", f.dealerDiversionRate());
            signals.put("claim_freq_anomaly",    f.claimFrequencyAnomaly());
        }

        ArrayNode factors = ctx.putArray("top_factors");
        score.topFactors().forEach(factors::add);

        return ctx.toString();
    }

    private String callGroq(String contextJson) {
        WebClient client = WebClient.builder()
            .baseUrl("https://api.groq.com/openai/v1")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

        ObjectNode body = mapper.createObjectNode();
        body.put("model",      model);
        body.put("max_tokens", 300);

        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
        messages.addObject().put("role", "user")
            .put("content", "Explain this fraud case:\n\n" + contextJson);

        String response = client.post()
            .uri("/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class)
            .block();

        try {
            JsonNode root = mapper.readTree(response);
            String text = root.path("choices").path(0).path("message").path("content").asText();
            return text.isBlank() ? "Explanation unavailable." : text.trim();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Groq response: " + e.getMessage());
        }
    }

    /**
     * Deterministic rule-based explanation — used when Groq is unavailable.
     * Produces the same 3-sentence structure but without LLM creativity.
     */
    String buildFallbackExplanation(FraudRiskScore score, Beneficiary b) {
        var f = score.features();
        String name     = score.subjectName() != null ? score.subjectName() : "Beneficiary #" + score.subjectId();
        String state    = score.stateCode()   != null ? score.stateCode()   : "unknown state";
        String category = b != null && b.getCategory() != null ? b.getCategory() : "unknown";

        // Sentence 1: identity
        String s1 = String.format("%s (ID #%d, %s, %s category) has been flagged as %s risk " +
            "by the AnnaSuraksha fraud detection engine with a composite score of %.3f/1.00.",
            name, score.subjectId(), state, category,
            score.riskLevel().name(), score.riskScore());

        // Sentence 2: top signals
        List<String> signalDescs = new ArrayList<>();
        if (f != null) {
            if (f.duplicateAadhaarSignal()  > 0) signalDescs.add("duplicate Aadhaar identity detected across multiple states");
            if (f.impossibleTravelSignal()  > 0) signalDescs.add("physically impossible inter-state travel detected (two ration claims in geographically distant states within an infeasible time window)");
            if (f.crossStateFraudSignal()   > 0) signalDescs.add("illegal cross-state ration claim by a non-ONORC beneficiary");
            if (f.rationUsageAnomalyScore() > 0.3) signalDescs.add(String.format("rice entitlement %.0f%% above NFSA family-size maximum", f.rationUsageAnomalyScore() * 100));
            if (f.categoryMismatchSignal()  > 0) signalDescs.add("AAY (Antyodaya Anna Yojana) category assigned to an undersized household");
            if (f.dealerDiversionRate()     > 0.2) signalDescs.add(String.format("%.0f%% of FPS deliveries flagged for diversion", f.dealerDiversionRate() * 100));
        }
        String s2 = signalDescs.isEmpty()
            ? "Anomalous claim patterns detected by statistical analysis."
            : "Signals detected: " + String.join("; ", signalDescs) + ".";

        // Sentence 3: implication
        long deliveryMonthlyLoss = estimatedeliveryMonthlyLoss(category);
        String s3 = String.format("Under NFSA 2013 and ONORC guidelines, this record requires " +
            "immediate human review; if confirmed fraudulent, estimated deliveryMonthly subsidy loss is ₹%,d.", deliveryMonthlyLoss);

        return s1 + " " + s2 + " " + s3;
    }

    private long estimatedeliveryMonthlyLoss(String category) {
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
