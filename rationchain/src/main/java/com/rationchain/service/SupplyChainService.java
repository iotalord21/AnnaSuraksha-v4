package com.rationchain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.rationchain.model.SupplyChainEntry;
import com.rationchain.model.SupplyChainRepository;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Grain Supply Chain Service — tracks grain from warehouse to beneficiary.
 *
 * Lifecycle stages (append-only):
 *   WAREHOUSE_LOADED → DISPATCHED → IN_TRANSIT → FPS_RECEIVED → DISTRIBUTED → CONFIRMED
 *
 * Each stage transition:
 *   1. Creates a new SupplyChainEntry row
 *   2. Computes a hash-linked ledger entry (SHA-256 chain)
 *   3. Flags discrepancies > 5% between consecutive stages
 *
 * Discrepancy detection:
 *   - At FPS_RECEIVED: compare receivedQty vs dispatchedQty from DISPATCHED entry
 *   - At CONFIRMED:    compare confirmedQty vs distributedQty from DISTRIBUTED entry
 *   - Missing grain = (dispatchedQty - receivedQty) at each stage
 */

@Service

public class SupplyChainService {
    private static final Logger log = LoggerFactory.getLogger(SupplyChainService.class);


    private final SupplyChainRepository supplyRepo;
    private final BlockchainService     blockchainSvc;

    private static final double DISCREPANCY_THRESHOLD = 0.05; // 5% tolerance

    public SupplyChainService(com.rationchain.model.SupplyChainRepository supplyRepo, BlockchainService blockchainSvc) {
        this.supplyRepo = supplyRepo;
        this.blockchainSvc = blockchainSvc;
    }

    // ── Stage 1: Warehouse loads shipment ─────────────────────────────────

    @Transactional
    public SupplyChainEntry warehouseLoad(
            String warehouseId, String fpsShopId, String stateCode, String district,
            String warehouseOfficerId,
            int riceKg, int wheatKg, int sugarKg) {

        String shipmentId = generateShipmentId(stateCode, fpsShopId);
        String prevHash   = getLatestHash();
        long   height     = getNextHeight();

        SupplyChainEntry entry = SupplyChainEntry.builder()
            .stage(SupplyChainEntry.Stage.WAREHOUSE_LOADED)
            .shipmentId(shipmentId)
            .warehouseId(warehouseId)
            .fpsShopId(fpsShopId)
            .stateCode(stateCode)
            .district(district)
            .warehouseOfficerId(warehouseOfficerId)
            .dispatchedRiceKg(riceKg)
            .dispatchedWheatKg(wheatKg)
            .dispatchedSugarKg(sugarKg)
            .dispatchedAt(LocalDateTime.now())
            .prevEntryHash(prevHash)
            .entryHeight(height)
            .build();

        entry.setEntryHash(computeEntryHash(entry));
        SupplyChainEntry saved = supplyRepo.save(entry);
        log.info("Warehouse loaded — shipment {} | rice={}kg wheat={}kg sugar={}kg",
            shipmentId, riceKg, wheatKg, sugarKg);
        return saved;
    }

    // ── Stage 2: Dispatch (warehouse → transport) ─────────────────────────

    @Transactional
    public SupplyChainEntry dispatch(String shipmentId, String transporterId) {
        SupplyChainEntry loaded = getLatestStageEntry(shipmentId, SupplyChainEntry.Stage.WAREHOUSE_LOADED);

        String prevHash = getLatestHash();
        long   height   = getNextHeight();

        SupplyChainEntry entry = SupplyChainEntry.builder()
            .stage(SupplyChainEntry.Stage.DISPATCHED)
            .shipmentId(shipmentId)
            .warehouseId(loaded.getWarehouseId())
            .fpsShopId(loaded.getFpsShopId())
            .stateCode(loaded.getStateCode())
            .district(loaded.getDistrict())
            .transporterId(transporterId)
            .dispatchedRiceKg(loaded.getDispatchedRiceKg())
            .dispatchedWheatKg(loaded.getDispatchedWheatKg())
            .dispatchedSugarKg(loaded.getDispatchedSugarKg())
            .dispatchedAt(LocalDateTime.now())
            .prevEntryHash(prevHash)
            .entryHeight(height)
            .build();

        entry.setEntryHash(computeEntryHash(entry));
        log.info("Dispatched — shipment {} by transporter {}", shipmentId, transporterId);
        return supplyRepo.save(entry);
    }

    // ── Stage 3: FPS receives grain ───────────────────────────────────────

    @Transactional
    public SupplyChainEntry fpsReceive(
            String shipmentId, String fpsOperatorId,
            int receivedRiceKg, int receivedWheatKg, int receivedSugarKg) {

        SupplyChainEntry dispatched = getLatestStageEntry(shipmentId, SupplyChainEntry.Stage.DISPATCHED);

        // Discrepancy detection
        int riceDiff  = dispatched.getDispatchedRiceKg()  - receivedRiceKg;
        int wheatDiff = dispatched.getDispatchedWheatKg() - receivedWheatKg;
        int sugarDiff = dispatched.getDispatchedSugarKg() - receivedSugarKg;

        boolean flagged = false;
        String  reason  = null;

        if (isDiscrepancy(dispatched.getDispatchedRiceKg(),  receivedRiceKg)  ||
            isDiscrepancy(dispatched.getDispatchedWheatKg(), receivedWheatKg) ||
            isDiscrepancy(dispatched.getDispatchedSugarKg(), receivedSugarKg)) {
            flagged = true;
            reason  = buildDiscrepancyReason(riceDiff, wheatDiff, sugarDiff, dispatched);
            log.warn("SUPPLY CHAIN DISCREPANCY — shipment {} | {}", shipmentId, reason);
        }

        String prevHash = getLatestHash();
        long   height   = getNextHeight();

        SupplyChainEntry entry = SupplyChainEntry.builder()
            .stage(SupplyChainEntry.Stage.FPS_RECEIVED)
            .shipmentId(shipmentId)
            .warehouseId(dispatched.getWarehouseId())
            .fpsShopId(dispatched.getFpsShopId())
            .stateCode(dispatched.getStateCode())
            .district(dispatched.getDistrict())
            .fpsOperatorId(fpsOperatorId)
            .dispatchedRiceKg(dispatched.getDispatchedRiceKg())
            .dispatchedWheatKg(dispatched.getDispatchedWheatKg())
            .dispatchedSugarKg(dispatched.getDispatchedSugarKg())
            .receivedRiceKg(receivedRiceKg)
            .receivedWheatKg(receivedWheatKg)
            .receivedSugarKg(receivedSugarKg)
            .riceDiscrepancyKg(riceDiff)
            .wheatDiscrepancyKg(wheatDiff)
            .sugarDiscrepancyKg(sugarDiff)
            .discrepancyFlagged(flagged)
            .discrepancyReason(reason)
            .receivedAt(LocalDateTime.now())
            .prevEntryHash(prevHash)
            .entryHeight(height)
            .build();

        entry.setEntryHash(computeEntryHash(entry));
        return supplyRepo.save(entry);
    }

    // ── Queries ────────────────────────────────────────────────────────────

    public List<SupplyChainEntry> getShipmentHistory(String shipmentId) {
        return supplyRepo.findByShipmentIdOrderByEntryHeightAsc(shipmentId);
    }

    public List<SupplyChainEntry> getFlaggedDiscrepancies() {
        return supplyRepo.findByDiscrepancyFlaggedTrueOrderByCreatedAtDesc();
    }

    public List<SupplyChainEntry> getByFpsShop(String fpsShopId) {
        return supplyRepo.findByFpsShopIdOrderByCreatedAtDesc(fpsShopId);
    }

    public Map<String, Object> getDiscrepancySummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalFlagged",       supplyRepo.countByDiscrepancyFlaggedTrue());
        summary.put("totalRiceLostKg",    supplyRepo.sumRiceDiscrepancy());
        summary.put("totalWheatLostKg",   supplyRepo.sumWheatDiscrepancy());
        summary.put("estimatedLossRs",    estimateLossRs(
                         safeSum(supplyRepo.sumRiceDiscrepancy()),
                         safeSum(supplyRepo.sumWheatDiscrepancy())));
        return summary;
    }

    /** Get all supply chain entries for a given state. */
    public List<SupplyChainEntry> getByState(String stateCode) {
        return supplyRepo.findByStateCodeOrderByCreatedAtDesc(stateCode);
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private SupplyChainEntry getLatestStageEntry(String shipmentId, SupplyChainEntry.Stage stage) {
        return supplyRepo.findByShipmentIdOrderByEntryHeightAsc(shipmentId)
            .stream()
            .filter(e -> e.getStage() == stage)
            .reduce((first, second) -> second)  // last entry for that stage
            .orElseThrow(() -> new RuntimeException(
                "No " + stage + " entry found for shipment " + shipmentId));
    }

    private boolean isDiscrepancy(int dispatched, int received) {
        if (dispatched == 0) return false;
        return Math.abs(dispatched - received) / (double) dispatched > DISCREPANCY_THRESHOLD;
    }

    private String buildDiscrepancyReason(int riceDiff, int wheatDiff, int sugarDiff,
                                           SupplyChainEntry dispatched) {
        List<String> parts = new ArrayList<>();
        if (riceDiff  > 0) parts.add("Rice: " + riceDiff  + " kg missing");
        if (wheatDiff > 0) parts.add("Wheat: " + wheatDiff + " kg missing");
        if (sugarDiff > 0) parts.add("Sugar: " + sugarDiff + " kg missing");
        if (riceDiff  < 0) parts.add("Rice: " + Math.abs(riceDiff) + " kg excess");
        return String.join("; ", parts) + " vs dispatched from " + dispatched.getWarehouseId();
    }

    private String generateShipmentId(String stateCode, String fpsShopId) {
        return "SHIP-" + stateCode + "-" + LocalDateTime.now().toString().replaceAll("[^0-9]", "").substring(0, 12)
            + "-" + fpsShopId.replaceAll("[^A-Z0-9]", "").substring(0, Math.min(4, fpsShopId.length()));
    }

    private String getLatestHash() {
        return supplyRepo.findLatestEntry()
            .map(SupplyChainEntry::getEntryHash)
            .orElse("0000000000000000000000000000000000000000000000000000000000000000");
    }

    private long getNextHeight() {
        return supplyRepo.findLatestEntry()
            .map(e -> e.getEntryHeight() + 1)
            .orElse(1L);
    }

    private String computeEntryHash(SupplyChainEntry e) {
        String payload = e.getPrevEntryHash() + e.getShipmentId() + e.getStage()
            + e.getDispatchedRiceKg() + e.getDispatchedWheatKg()
            + e.getReceivedRiceKg() + System.currentTimeMillis();
        return blockchainSvc.sha256(payload);
    }

    private long safeSum(Long v) { return v == null ? 0L : v; }

    /** Rough loss estimate: rice ₹30/kg, wheat ₹25/kg (market price delta from subsidy). */
    private long estimateLossRs(long riceLostKg, long wheatLostKg) {
        return (riceLostKg * 30L) + (wheatLostKg * 25L);
    }
}
