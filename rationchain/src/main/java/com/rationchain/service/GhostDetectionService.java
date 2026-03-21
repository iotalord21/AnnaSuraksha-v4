package com.rationchain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.rationchain.model.Beneficiary;
import com.rationchain.model.BeneficiaryRepository;


import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Multi-layer ghost beneficiary detection engine.
 *
 * Layer 1 — Duplicate Aadhaar hash across states
 *           Same SHA-256 hash found in two or more state registrations.
 *           Earliest registration is legitimate; all later ones are ghosts.
 *
 * Layer 2 — Statistical pattern anomaly (rule-based heuristics)
 *           Flags entitlement mismatches and impossible category assignments.
 *           Production upgrade: replace with IsolationForest via DJL/ONNX Runtime.
 *
 * Layer 3 — Cross-state velocity check
 *           Flags physically impossible cross-state claims (ONORC migrants
 *           who appear in two states within an impossible travel window).
 *           Also flags non-migrants who have claimed cross-state (forbidden).
 *
 * Layer 4 — Groq AI reasoning (implemented in AuditService)
 *           Sends all flagged records to Groq for natural language fraud report.
 *           Production: feeds into a human review queue.
 */

@Service

public class GhostDetectionService {
    private static final Logger log = LoggerFactory.getLogger(GhostDetectionService.class);


    private final BeneficiaryRepository repo;

    public record GhostFlag(Long beneficiaryId, String layer, String reason, long estimateddeliveryMonthlyLossRs) {}

    /** Run all three detection layers and return the combined flag list. */
    public List<GhostFlag> runAllLayers() {
        List<Beneficiary> all = repo.findAll();
        List<GhostFlag> flags = new ArrayList<>();
        flags.addAll(layer1DuplicateAadhaar(all));
        flags.addAll(layer2AbnormalPattern(all));
        flags.addAll(layer3VelocityAndCrossState(all));
        return flags;
    }

    public GhostDetectionService(com.rationchain.model.BeneficiaryRepository repo) {
        this.repo = repo;
    }

    // ── Layer 1: Duplicate Aadhaar across states ──────────────────────────
    //
    // Groups all beneficiaries by their Aadhaar hash. Any group with more than
    // one member contains duplicates. The earliest registration (by registeredAt,
    // with id as a stable secondary sort) is treated as the legitimate record;
    // all later registrations are ghosts.
    private List<GhostFlag> layer1DuplicateAadhaar(List<Beneficiary> all) {
        Map<String, List<Beneficiary>> byAadhaar = new HashMap<>();
        for (Beneficiary b : all) {
            if (b.getAadhaarHash() == null) continue;
            byAadhaar.computeIfAbsent(b.getAadhaarHash(), k -> new ArrayList<>()).add(b);
        }

        List<GhostFlag> flags = new ArrayList<>();
        for (Map.Entry<String, List<Beneficiary>> e : byAadhaar.entrySet()) {
            if (e.getValue().size() <= 1) continue;

            // Sort ascending by registeredAt; use id as stable secondary sort for null safety.
            List<Beneficiary> dups = e.getValue().stream()
                .sorted(Comparator
                    .comparing(
                        (Beneficiary b) -> b.getRegisteredAt() != null ? b.getRegisteredAt() : LocalDateTime.MIN
                    )
                    .thenComparingLong(b -> b.getId() != null ? b.getId() : Long.MAX_VALUE)
                )
                .toList();

            // The first entry is the legitimate registration; skip it.
            for (int i = 1; i < dups.size(); i++) {
                Beneficiary ghost = dups.get(i);
                // Skip if already flagged — avoids duplicate entries in the returned list.
                if ("GHOST".equals(ghost.getStatus())) continue;
                long loss = estimatedeliveryMonthlyLoss(ghost.getCategory());
                flags.add(new GhostFlag(
                    ghost.getId(),
                    "LAYER_1_DUPLICATE",
                    "Duplicate Aadhaar registered in " + dups.get(0).getStateCode()
                        + " and " + ghost.getStateCode(),
                    loss
                ));
                log.warn("Layer 1: ghost detected — beneficiary {} ({})", ghost.getId(), ghost.getFullName());
            }
        }
        return flags;
    }

    // ── Layer 2: Heuristic statistical pattern anomaly ────────────────────
    //
    // Checks two rule-based anomalies:
    //   (a) Rice entitlement > physical maximum for family size
    //       Note: AAY is exempt because its 14 kg/deliveryMonth is a fixed statutory
    //       amount under NFSA 2013, not family-size-scaled.
    //   (b) AAY category assigned to a family of fewer than 3 people.
    //       AAY (Antyodaya Anna Yojana) is reserved for destitute families;
    //       single-person or couple households are statistically anomalous.
    private List<GhostFlag> layer2AbnormalPattern(List<Beneficiary> all) {
        List<GhostFlag> flags = new ArrayList<>();
        for (Beneficiary b : all) {
            // Skip any non-ACTIVE record — already handled or suspended.
            if (!"ACTIVE".equals(b.getStatus())) continue;

            // Flag (a): rice entitlement exceeds NFSA family-size maximum.
            // AAY is exempt — its 14 kg/deliveryMonth is fixed by law, not per-member.
            if (!"AAY".equals(b.getCategory())) {
                int maxPossibleRice = (b.getFamilySize() != null ? b.getFamilySize() : 1) * 7;
                if (b.getRiceKg() != null && b.getRiceKg() > maxPossibleRice) {
                    flags.add(new GhostFlag(b.getId(), "LAYER_2_PATTERN",
                        "Rice entitlement " + b.getRiceKg() + " kg exceeds NFSA maximum ("
                            + maxPossibleRice + " kg) for family of " + b.getFamilySize(),
                        estimatedeliveryMonthlyLoss(b.getCategory())
                    ));
                    log.warn("Layer 2: excess rice entitlement — beneficiary {} ({})", b.getId(), b.getFullName());
                }
            }

            // Flag (b): AAY category with impossibly small household.
            // AAY minimum household size is 3 in practice (destitute families).
            if ("AAY".equals(b.getCategory()) && b.getFamilySize() != null && b.getFamilySize() < 3) {
                flags.add(new GhostFlag(b.getId(), "LAYER_2_PATTERN",
                    "AAY category (reserved for destitute families) assigned to household of "
                        + b.getFamilySize() + " — statistically anomalous",
                    estimatedeliveryMonthlyLoss("AAY")
                ));
                log.warn("Layer 2: AAY single-person anomaly — beneficiary {} ({})", b.getId(), b.getFullName());
            }
        }
        return flags;
    }

    // ── Layer 3: Cross-state velocity check + non-ONORC cross-state fraud ─
    //
    // Two sub-checks:
    //
    // (3a) Non-ONORC cross-state: A beneficiary who is NOT registered under ONORC
    //      has NO legal right to claim in any state other than their registration state.
    //      If claimState != stateCode, this is outright fraud — flag immediately.
    //
    // (3b) ONORC velocity: An ONORC migrant may legally claim cross-state, but
    //      physics still apply. If they claimed in their registration state recently
    //      AND also have a cross-state claimState on record, the time between their
    //      registration timestamp and their last cross-state claim must be at least
    //      the minimum inter-state travel time (~24 hours for distant states).
    //      Less than 24 hours apart = physically impossible = ghost/fraud.
    private List<GhostFlag> layer3VelocityAndCrossState(List<Beneficiary> all) {
        List<GhostFlag> flags = new ArrayList<>();
        for (Beneficiary b : all) {
            if (!"ACTIVE".equals(b.getStatus())) continue;

            boolean hasCrossStateClaim = b.getClaimState() != null
                && !b.getClaimState().isBlank()
                && !b.getClaimState().equals(b.getStateCode());

            if (!hasCrossStateClaim) continue;

            // ── (3a) Non-ONORC beneficiary claiming cross-state ──────────
            if (!Boolean.TRUE.equals(b.getMigrant())) {
                flags.add(new GhostFlag(b.getId(), "LAYER_3_VELOCITY",
                    "Non-ONORC beneficiary registered in " + b.getStateCode()
                        + " has cross-state claim recorded in " + b.getClaimState()
                        + " — cross-state claiming is only permitted under ONORC",
                    estimatedeliveryMonthlyLoss(b.getCategory())
                ));
                log.warn("Layer 3 (3a): non-ONORC cross-state claim — beneficiary {} ({})", b.getId(), b.getFullName());
                continue;
            }

            // ── (3b) ONORC migrant: velocity check ───────────────────────
            // Compare registeredAt (home-state timestamp) with lastClaimAt
            // (most recent cross-state claim). If the gap < 24h, they physically
            // could not have traveled between states.
            if (b.getLastClaimAt() != null && b.getRegisteredAt() != null) {
                long hoursApart = Math.abs(
                    ChronoUnit.HOURS.between(b.getRegisteredAt(), b.getLastClaimAt())
                );
                if (hoursApart < 24) {
                    flags.add(new GhostFlag(b.getId(), "LAYER_3_VELOCITY",
                        "ONORC migrant claimed in " + b.getClaimState()
                            + " within " + hoursApart + " hour(s) of home-state activity in "
                            + b.getStateCode() + " — physically impossible inter-state travel",
                        estimatedeliveryMonthlyLoss(b.getCategory())
                    ));
                    log.warn("Layer 3 (3b): velocity fraud — beneficiary {} ({}) — {}h gap",
                        b.getId(), b.getFullName(), hoursApart);
                }
            }
        }
        return flags;
    }

    /**
     * Write ghost flags to the database — sets status = GHOST for each flagged record.
     * Skips records that are already GHOST (idempotent; safe to call repeatedly).
     *
     * @return count of records newly marked GHOST in this pass.
     */
    public int applyFlags(List<GhostFlag> flags) {
        int count = 0;
        for (GhostFlag flag : flags) {
            Optional<Beneficiary> opt = repo.findById(flag.beneficiaryId());
            if (opt.isPresent() && !"GHOST".equals(opt.get().getStatus())) {
                Beneficiary b = opt.get();
                b.setStatus("GHOST");
                b.setGhostReason(flag.reason());
                b.setGhostLayer(flag.layer());
                b.setFlaggedAt(LocalDateTime.now());
                repo.save(b);
                count++;
            }
        }
        return count;
    }

    /**
     * Estimate deliveryMonthly ration value by category (at NFSA subsidised procurement price).
     * BPL: ₹2/kg rice + ₹2/kg wheat — these are the NFSA ceiling retail prices.
     * Actual government subsidy per kg is higher; these values represent the minimum
     * deliveryMonthly loss estimate in rupees.
     */
    public long estimatedeliveryMonthlyLoss(String category) {
        if (category == null) return 96L;
        return switch (category) {
            case "AAY" -> 350L;   // 14 kg rice + 21 kg wheat × ₹2/kg + ₹2/kg
            case "BPL" -> 96L;    // 5 kg rice/member × avg 4 members × ₹2 + wheat
            case "PHH" -> 75L;    // 5 kg rice/member × avg 3 members × ₹2
            case "APL" -> 60L;    // 3 kg rice + 2 kg wheat per member
            default    -> 96L;
        };
    }
}
