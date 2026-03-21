package com.rationchain.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.rationchain.model.*;
import com.rationchain.service.*;


import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Supply Chain REST API — STEP 5.
 *
 * Exposes grain lifecycle tracking and ledger integrity verification.
 *
 * Endpoints:
 *   POST /api/supply-chain/warehouse-load         — Record grain loaded at warehouse
 *   POST /api/supply-chain/dispatch               — Record shipment dispatched
 *   POST /api/supply-chain/fps-receive            — Record FPS shop receiving grain
 *   GET  /api/supply-chain/shipment/{id}          — Full lifecycle of a shipment
 *   GET  /api/supply-chain/flagged                — All shipments with discrepancies
 *   GET  /api/supply-chain/summary                — Leakage summary
 *   GET  /api/ledger/verify                       — Chain integrity check (STEP 2)
 *   GET  /api/ledger/audit                        — Full chain audit with per-block hashes
 */

@RestController

public class SupplyChainController {
    private static final Logger log = LoggerFactory.getLogger(SupplyChainController.class);


    private final SupplyChainService   supplyChainSvc;
    private final AuditLogService      auditLogSvc;
    private final BlockchainService    chainSvc;
    private final BeneficiaryRepository beneRepo;

    // ── Supply Chain Endpoints ─────────────────────────────────────────────

    public SupplyChainController(SupplyChainService supplyChainSvc, AuditLogService auditLogSvc, BlockchainService chainSvc, com.rationchain.model.BeneficiaryRepository beneRepo) {
        this.supplyChainSvc = supplyChainSvc;
        this.auditLogSvc = auditLogSvc;
        this.chainSvc = chainSvc;
        this.beneRepo = beneRepo;
    }

    @PostMapping("/api/supply-chain/warehouse-load")
    public Map<String, Object> warehouseLoad(@RequestBody Map<String, Object> body) {
        try {
            String warehouseId = str(body, "warehouse_id");
            String fpsShopId   = str(body, "fps_shop_id");
            String stateCode   = str(body, "state_code");
            String district    = str(body, "district");
            String officerId   = str(body, "warehouse_officer_id");
            int rice  = num(body, "rice_kg");
            int wheat = num(body, "wheat_kg");
            int sugar = num(body, "sugar_kg");

            SupplyChainEntry entry = supplyChainSvc.warehouseLoad(
                warehouseId, fpsShopId, stateCode, district, officerId, rice, wheat, sugar);

            auditLogSvc.logSupplyChainUpdate(entry.getShipmentId(), "WAREHOUSE_LOADED", officerId);

            return success(Map.of(
                "shipment_id",   entry.getShipmentId(),
                "stage",         entry.getStage(),
                "entry_hash",    entry.getEntryHash(),
                "entry_height",  entry.getEntryHeight(),
                "rice_kg",       rice,
                "wheat_kg",      wheat,
                "sugar_kg",      sugar,
                "created_at",    entry.getCreatedAt()
            ));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @PostMapping("/api/supply-chain/dispatch")
    public Map<String, Object> dispatch(@RequestBody Map<String, Object> body) {
        try {
            String shipmentId   = str(body, "shipment_id");
            String transporterId = str(body, "transporter_id");

            SupplyChainEntry entry = supplyChainSvc.dispatch(shipmentId, transporterId);
            auditLogSvc.logSupplyChainUpdate(shipmentId, "DISPATCHED", transporterId);

            return success(Map.of(
                "shipment_id",  entry.getShipmentId(),
                "stage",        entry.getStage(),
                "entry_hash",   entry.getEntryHash(),
                "dispatched_at", entry.getDispatchedAt()
            ));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @PostMapping("/api/supply-chain/fps-receive")
    public Map<String, Object> fpsReceive(@RequestBody Map<String, Object> body) {
        try {
            String shipmentId   = str(body, "shipment_id");
            String operatorId   = str(body, "fps_operator_id");
            int rice  = num(body, "received_rice_kg");
            int wheat = num(body, "received_wheat_kg");
            int sugar = num(body, "received_sugar_kg");

            SupplyChainEntry entry = supplyChainSvc.fpsReceive(
                shipmentId, operatorId, rice, wheat, sugar);

            auditLogSvc.logSupplyChainUpdate(shipmentId, "FPS_RECEIVED", operatorId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("shipment_id",          entry.getShipmentId());
            result.put("stage",                entry.getStage());
            result.put("entry_hash",           entry.getEntryHash());
            result.put("received_at",          entry.getReceivedAt());
            result.put("discrepancy_flagged",  entry.getDiscrepancyFlagged());
            if (Boolean.TRUE.equals(entry.getDiscrepancyFlagged())) {
                result.put("discrepancy_reason",  entry.getDiscrepancyReason());
                result.put("rice_discrepancy_kg", entry.getRiceDiscrepancyKg());
                result.put("wheat_discrepancy_kg",entry.getWheatDiscrepancyKg());
                result.put("alert",
                    "⚠️ GRAIN SHORTAGE DETECTED — this shipment has been flagged for investigation.");
            }
            return success(result);
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @GetMapping("/api/supply-chain/shipment/{shipmentId}")
    public Map<String, Object> shipmentHistory(@PathVariable String shipmentId) {
        List<SupplyChainEntry> history = supplyChainSvc.getShipmentHistory(shipmentId);
        if (history.isEmpty()) {
            return error("Shipment " + shipmentId + " not found.");
        }

        List<Map<String, Object>> stages = history.stream().map(e -> {
            Map<String, Object> stage = new LinkedHashMap<>();
            stage.put("stage",              e.getStage());
            stage.put("entry_height",       e.getEntryHeight());
            stage.put("entry_hash",         e.getEntryHash());
            stage.put("prev_hash",          e.getPrevEntryHash());
            stage.put("dispatched_rice_kg", e.getDispatchedRiceKg());
            stage.put("received_rice_kg",   e.getReceivedRiceKg());
            stage.put("flagged",            e.getDiscrepancyFlagged());
            stage.put("timestamp",          e.getCreatedAt());
            return stage;
        }).toList();

        return Map.of(
            "shipment_id", shipmentId,
            "fps_shop",    history.get(0).getFpsShopId(),
            "state",       history.get(0).getStateCode(),
            "stage_count", stages.size(),
            "stages",      stages
        );
    }

    @GetMapping("/api/supply-chain/flagged")
    public Map<String, Object> flaggedShipments() {
        List<SupplyChainEntry> flagged = supplyChainSvc.getFlaggedDiscrepancies();
        List<Map<String, Object>> results = flagged.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("shipment_id",        e.getShipmentId());
            m.put("fps_shop",           e.getFpsShopId());
            m.put("state",              e.getStateCode());
            m.put("discrepancy_reason", e.getDiscrepancyReason());
            m.put("rice_lost_kg",       e.getRiceDiscrepancyKg());
            m.put("wheat_lost_kg",      e.getWheatDiscrepancyKg());
            m.put("estimated_loss_rs",
                (e.getRiceDiscrepancyKg() != null ? e.getRiceDiscrepancyKg() * 30L : 0L)
              + (e.getWheatDiscrepancyKg() != null ? e.getWheatDiscrepancyKg() * 25L : 0L));
            m.put("flagged_at",         e.getCreatedAt());
            return m;
        }).toList();

        return Map.of(
            "api",                      "AnnaSuraksha Supply Chain",
            "flagged_shipment_count",   results.size(),
            "results",                  results,
            "total_summary",            supplyChainSvc.getDiscrepancySummary()
        );
    }

    @GetMapping("/api/supply-chain/summary")
    public Map<String, Object> supplyChainSummary() {
        Map<String, Object> summary = supplyChainSvc.getDiscrepancySummary();
        summary.put("api",          "AnnaSuraksha Supply Chain Summary");
        summary.put("generated_at", LocalDateTime.now());
        return summary;
    }

    // ── Ledger Integrity Endpoints (STEP 2) ───────────────────────────────

    /**
     * Chain verification — walks the entire beneficiary chain and validates
     * that every prevBlockHash matches the actual hash of the preceding block.
     *
     * Returns HTTP 200 in both cases; check chain_valid field.
     */
    @GetMapping("/api/ledger/verify")
    public Map<String, Object> verifyLedger() {
        boolean valid = chainSvc.validateChain();
        long    total = beneRepo.count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("api",          "AnnaSuraksha Ledger Integrity");
        response.put("generated_at", LocalDateTime.now());
        response.put("chain_valid",  valid);
        response.put("chain_status", valid ? "✅ INTACT — no tampering detected" : "❌ TAMPERED — chain broken");
        response.put("blocks_verified", total);
        response.put("algorithm",    "SHA-256 hash chain (keccak256-compatible)");
        response.put("integrity_check", valid
            ? "All " + total + " blocks verified. prevBlockHash matches actual block hash at every height."
            : "Chain integrity FAILED. A record has been modified after registration. Immediate audit required.");
        return response;
    }

    /**
     * Full ledger audit — returns every block with its hash and linkage.
     * Paginated to avoid memory pressure.
     */
    @GetMapping("/api/ledger/audit")
    public Map<String, Object> auditLedger(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        List<Beneficiary> all = beneRepo.findAllByOrderByBlockHeightAsc();
        int total = all.size();
        int from  = Math.min(page * size, total);
        int to    = Math.min(from + size, total);
        List<Beneficiary> pageData = all.subList(from, to);

        List<Map<String, Object>> blocks = pageData.stream().map(b -> {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("block_height",   b.getBlockHeight());
            block.put("block_hash",     b.getBlockHash());
            block.put("prev_hash",      b.getPrevBlockHash());
            block.put("short_hash",     chainSvc.shortHash(b.getBlockHash()));
            block.put("beneficiary_id", b.getId());
            block.put("name",           b.getFullName());
            block.put("state",          b.getStateCode());
            block.put("status",         b.getStatus());
            block.put("registered_at",  b.getRegisteredAt());
            return block;
        }).toList();

        boolean chainValid = chainSvc.validateChain();

        return Map.of(
            "api",           "AnnaSuraksha Ledger Audit",
            "generated_at",  LocalDateTime.now(),
            "chain_valid",   chainValid,
            "total_blocks",  total,
            "page",          page,
            "page_size",     size,
            "blocks",        blocks
        );
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Map<String, Object> success(Map<String, Object> data) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "ok");
        r.putAll(data);
        return r;
    }

    private Map<String, Object> error(String msg) {
        return Map.of("status", "error", "message", msg);
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : "";
    }

    private int num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); }
        catch (Exception e) { return 0; }
    }
}
