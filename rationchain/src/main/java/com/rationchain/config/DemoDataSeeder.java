package com.rationchain.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.rationchain.service.BeneficiaryService;
import com.rationchain.service.SupplyChainService;
import com.rationchain.model.*;


import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Seeds realistic demo data so the hackathon demo is immediately useful
 * without requiring any manual setup. Data covers:
 *   - 8 genuine beneficiaries across different states and categories
 *   - 2 ONORC (One Nation One Ration Card) migrant workers
 *   - 3 ghost candidates covering one case per detection layer:
 *       Ghost A — Layer 1: duplicate Aadhaar in a second state
 *       Ghost B — Layer 2: AAY category with single-person household
 *       Ghost C — Layer 3: ONORC velocity fraud (cross-state in < 24 hours)
 */

@Component

public class DemoDataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);


    private final BeneficiaryService  svc;
    private final BeneficiaryRepository repo;
    private final SupplyChainService  supplyChainSvc;

    public DemoDataSeeder(BeneficiaryService svc, com.rationchain.model.BeneficiaryRepository repo, SupplyChainService supplyChainSvc) {
        this.svc = svc;
        this.repo = repo;
        this.supplyChainSvc = supplyChainSvc;
    }

    @Override
    public void run(String... args) {
        if (repo.count() > 0) return; // already seeded — never re-seed on restart
        log.info("Seeding demo beneficiary data…");

        // ── Genuine beneficiaries ──────────────────────────────────────────
        svc.register("987654321012", "Ramesh Kumar Singh",  "UP", "BPL", 5, "9876543210", false);
        svc.register("876543210923", "Meena Devi Yadav",    "UP", "AAY", 6, "9765432109", false);
        svc.register("765432109834", "Suresh Patil",        "MH", "PHH", 3, "9654321098", false);
        svc.register("654321098745", "Lakshmi Bai Sharma",  "MP", "BPL", 4, "9543210987", false);
        svc.register("543210987656", "Arjun Das",           "WB", "BPL", 2, "9432109876", false);
        svc.register("432109876567", "Fatima Begum",        "RJ", "AAY", 8, "9321098765", false);
        svc.register("321098765478", "Venkatesh Reddy",     "AP", "PHH", 3, "9210987654", false);
        svc.register("210987654389", "Priya Nair",          "TN", "APL", 4, "9109876543", false);

        // ── ONORC migrant workers ──────────────────────────────────────────
        svc.register("198765432210", "Rajiv Gupta",         "UP", "BPL", 3, "9876012345", true);
        svc.register("287654321121", "Anita Kumari",        "WB", "PHH", 2, "9765101234", true);

        // ── Ghost A — Layer 1: duplicate Aadhaar in a second state ────────
        // Same Aadhaar "987654321012" as Ramesh Kumar Singh (UP).
        // This new registration in MH is saved as GHOST immediately by the
        // Layer 1 pre-check in BeneficiaryService.register().
        svc.register("987654321012", "Ramesh K (Duplicate)", "MH", "BPL", 5, "", false);

        // ── Ghost B — Layer 2: AAY category, single-person household ──────
        // AAY (Antyodaya Anna Yojana) is for destitute FAMILIES — assigning it
        // to a one-person household is a statistical impossibility.
        svc.register("111222333444", "Unknown Single",       "GJ", "AAY", 1, "", false);

        // ── Ghost C — Layer 3: ONORC velocity fraud ───────────────────────
        // "Speed Claimant" is an ONORC migrant registered in Tamil Nadu.
        // They have a recorded cross-state claim in Maharashtra.
        // The timestamps show: registered in TN 5 hours ago, last claim in MH 2 hours ago.
        // TN → MH minimum travel time is ~20 hours by any transport.
        // 3-hour gap = physically impossible → LAYER_3_VELOCITY.
        svc.register("999888777666", "Speed Claimant",       "TN", "BPL", 4, "9000000001", true);
        repo.findAll().stream()
            .filter(b -> "Speed Claimant".equals(b.getFullName()))
            .findFirst()
            .ifPresent(b -> {
                b.setClaimState("MH");                                    // last claimed in Maharashtra
                b.setRegisteredAt(LocalDateTime.now().minusHours(5));     // registered in TN 5h ago
                b.setLastClaimAt(LocalDateTime.now().minusHours(2));      // claimed in MH 2h ago
                // hoursApart = |5h - 2h| = 3h < 24h → velocity fraud
                repo.save(b);
                log.info("Seeded velocity ghost: registeredAt=-5h, lastClaimAt=-2h, claimState=MH");
            });

        log.info("Seeded {} beneficiaries (10 genuine, 3 ghost candidates)", repo.count());

        // ── Seed a few genuine claim records ──────────────────────────────
        List<Beneficiary> activeBeneficiaries = repo.findByStatus("ACTIVE");
        activeBeneficiaries.stream()
            .filter(b -> !Boolean.TRUE.equals(b.getMigrant()))   // seed home-state claims for non-migrants
            .limit(6)
            .forEach(b -> {
                try { svc.recordClaim(b.getId(), b.getStateCode(), true); }
                catch (Exception e) { log.debug("Claim seed skipped: {}", e.getMessage()); }
            });

        log.info("Demo data seeded successfully.");

        // ── Seed supply chain entries ──────────────────────────────────────
        seedSupplyChain();
        log.info("Supply chain demo data seeded.");
    }

    /**
     * Seeds realistic grain supply chain entries.
     * Includes one clean shipment and one with a discrepancy to demonstrate detection.
     */
    private void seedSupplyChain() {
        try {
            // ── Clean shipment: UP warehouse → FPS-UP-0042 (no discrepancy) ──
            SupplyChainEntry loaded1 = supplyChainSvc.warehouseLoad(
                "WH-UP-LUCKNOW-01", "FPS-UP-0042", "UP", "UP-District",
                "OFFICER-UP-001", 500, 300, 50);
            supplyChainSvc.dispatch(loaded1.getShipmentId(), "TRANSPORTER-RAJ-01");
            supplyChainSvc.fpsReceive(loaded1.getShipmentId(), "FPS-OP-UP-42",
                500, 300, 50); // received exactly what was dispatched

            // ── Flagged shipment: MH warehouse → FPS-MH-1101 (rice shortage) ──
            SupplyChainEntry loaded2 = supplyChainSvc.warehouseLoad(
                "WH-MH-MUMBAI-03", "FPS-MH-1101", "MH", "MH-District",
                "OFFICER-MH-007", 800, 400, 60);
            supplyChainSvc.dispatch(loaded2.getShipmentId(), "TRANSPORTER-MH-99");
            supplyChainSvc.fpsReceive(loaded2.getShipmentId(), "FPS-OP-MH-1101",
                680, 390, 60); // 120 kg rice missing → flagged (15% discrepancy > 5% threshold)

            // ── Second clean shipment: WB ──────────────────────────────────
            SupplyChainEntry loaded3 = supplyChainSvc.warehouseLoad(
                "WH-WB-KOLKATA-01", "FPS-WB-0701", "WB", "WB-District",
                "OFFICER-WB-003", 350, 200, 30);
            supplyChainSvc.dispatch(loaded3.getShipmentId(), "TRANSPORTER-WB-11");
            supplyChainSvc.fpsReceive(loaded3.getShipmentId(), "FPS-OP-WB-701",
                345, 198, 30); // tiny difference within 5% tolerance — not flagged

        } catch (Exception e) {
            log.warn("Supply chain seeding partial failure (safe to ignore): {}", e.getMessage());
        }
    }
}
