package com.rationchain.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.rationchain.model.*;
import java.time.LocalDateTime;
import com.rationchain.service.*;


import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.*;


@Controller

public class RationController {
    private static final Logger log = LoggerFactory.getLogger(RationController.class);


    private final BeneficiaryService      beneSvc;
    private final AuditService            auditSvc;
    private final GhostDetectionService   ghostSvc;
    private final BlockchainService       chainSvc;
    private final ReferenceDataService    refData;
    private final BeneficiaryRepository   beneRepo;
    private final AuditRepository         auditRepo;
    private final FpsDeliveryRepository   fpsRepo;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM HH:mm");

    // ── Dashboard ────────────────────────────────────────────────────────

    public RationController(BeneficiaryService beneSvc, AuditService auditSvc, GhostDetectionService ghostSvc, BlockchainService chainSvc, ReferenceDataService refData, com.rationchain.model.BeneficiaryRepository beneRepo, com.rationchain.model.AuditRepository auditRepo, com.rationchain.model.FpsDeliveryRepository fpsRepo) {
        this.beneSvc = beneSvc;
        this.auditSvc = auditSvc;
        this.ghostSvc = ghostSvc;
        this.chainSvc = chainSvc;
        this.refData = refData;
        this.beneRepo = beneRepo;
        this.auditRepo = auditRepo;
        this.fpsRepo = fpsRepo;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        Map<String, Long> stats = beneSvc.getStats();
        List<Beneficiary> all   = beneSvc.getAll();
        List<ClaimRecord> claims = beneSvc.getRecentClaims();
        List<AuditReport> audits = auditRepo.findTop5ByOrderByGeneratedAtDesc();

        long deliveryMonthlyLoss = beneRepo.findByStatus("GHOST").stream()
            .mapToLong(b -> ghostSvc.estimatedeliveryMonthlyLoss(b.getCategory()))
            .sum();

        boolean chainValid = chainSvc.validateChain();

        model.addAttribute("stats",        stats);
        model.addAttribute("beneficiaries", all);
        model.addAttribute("claims",        claims);
        model.addAttribute("audits",        audits);
        model.addAttribute("deliveryMonthlyLoss",   deliveryMonthlyLoss);
        model.addAttribute("annualSavings", deliveryMonthlyLoss * 12);
        model.addAttribute("chainValid",    chainValid);
        model.addAttribute("states",        refData.getStates());
        model.addAttribute("supplyChain",   refData.getSupplyChain());
        model.addAttribute("categories",    refData.getCategories());
        model.addAttribute("catColors",     buildCatColorMap());
        model.addAttribute("fmt",           FMT);
        return "dashboard";
    }

    // ── Ledger ───────────────────────────────────────────────────────────

    @GetMapping("/ledger")
    public String ledger(@RequestParam(required = false) String state,
                         @RequestParam(required = false) String status,
                         Model model) {
        List<Beneficiary> list;
        if (state != null && !state.isBlank()) {
            list = beneSvc.getByState(state);
        } else {
            list = beneSvc.getAll();
        }
        if (status != null && !status.isBlank()) {
            list = list.stream().filter(b -> status.equals(b.getStatus())).toList();
        }

        model.addAttribute("beneficiaries", list);
        model.addAttribute("states",        refData.getStates());
        model.addAttribute("selectedState", state);
        model.addAttribute("selectedStatus",status);
        model.addAttribute("catColors",     buildCatColorMap());
        model.addAttribute("fmt",           FMT);
        return "ledger";
    }

    // ── Beneficiary detail ───────────────────────────────────────────────

    @GetMapping("/beneficiary/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Beneficiary b = beneSvc.findById(id)
            .orElseThrow(() -> new RuntimeException("Not found"));
        List<ClaimRecord> claims = beneSvc.getClaimsForBeneficiary(id);

        model.addAttribute("b",          b);
        model.addAttribute("claims",     claims);
        model.addAttribute("shortHash",  chainSvc.shortHash(b.getBlockHash()));
        model.addAttribute("catColors",  buildCatColorMap());
        model.addAttribute("fmt",        FMT);
        return "detail";
    }

    // ── Search ───────────────────────────────────────────────────────────

    @GetMapping("/search")
    public String search(@RequestParam(required = false) String last4, Model model) {
        List<Beneficiary> results = List.of();
        if (last4 != null && last4.matches("\\d{4}")) {
            results = beneSvc.searchByAadhaarLast4(last4);
        }
        model.addAttribute("results",  results);
        model.addAttribute("last4",    last4);
        model.addAttribute("catColors",buildCatColorMap());
        return "search";
    }

    // ── Register ─────────────────────────────────────────────────────────

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("states",     refData.getStateCodes());
        model.addAttribute("categories", refData.getCategories());
        return "register";
    }

    @PostMapping("/register")
    public String registerSubmit(
            @RequestParam String aadhaar,
            @RequestParam String name,
            @RequestParam String state,
            @RequestParam String category,
            @RequestParam int familySize,
            @RequestParam(required = false) String phone,
            @RequestParam(defaultValue = "false") boolean migrant,
            RedirectAttributes flash) {
        try {
            Beneficiary b = beneSvc.register(aadhaar, name, state, category, familySize,
                                              phone != null ? phone : "", migrant);
            if ("GHOST".equals(b.getStatus())) {
                flash.addFlashAttribute("warn",
                    "⚠️ Duplicate Aadhaar detected — registered but flagged as GHOST. Block #" + b.getBlockHeight());
            } else {
                flash.addFlashAttribute("success",
                    "✅ Beneficiary registered. Block #" + b.getBlockHeight()
                    + "  Hash: " + chainSvc.shortHash(b.getBlockHash()));
            }
        } catch (Exception e) {
            flash.addFlashAttribute("error", "Registration failed: " + e.getMessage());
        }
        return "redirect:/ledger";
    }

    // ── Record claim ─────────────────────────────────────────────────────

    @PostMapping("/claim/{id}")
    public String recordClaim(@PathVariable Long id,
                               @RequestParam(required = false) String claimState,
                               @RequestParam(defaultValue = "true") boolean biometric,
                               RedirectAttributes flash) {
        try {
            ClaimRecord cr = beneSvc.recordClaim(id, claimState, biometric);
            flash.addFlashAttribute("success",
                "✅ Claim recorded on-chain. TX: " + cr.getTxHash());
        } catch (Exception e) {
            flash.addFlashAttribute("error", "Claim failed: " + e.getMessage());
        }
        return "redirect:/beneficiary/" + id;
    }

    // ── Ghost detection ──────────────────────────────────────────────────

    @PostMapping("/detect-ghosts")
    public String detectGhosts(RedirectAttributes flash) {
        List<GhostDetectionService.GhostFlag> flags = ghostSvc.runAllLayers();
        int count = ghostSvc.applyFlags(flags);
        flash.addFlashAttribute("success",
            "🔍 Detection complete — " + count + " ghost(s) newly flagged across 3 layers."
            + " Total flags generated: " + flags.size() + ".");
        return "redirect:/ledger?status=GHOST";
    }

    // ── AI Audit ─────────────────────────────────────────────────────────

    @PostMapping("/audit")
    public String runAudit(RedirectAttributes flash) {
        try {
            AuditReport r = auditSvc.runAudit();
            flash.addFlashAttribute("auditId", r.getId());
            return "redirect:/audit/" + r.getId();
        } catch (Exception e) {
            flash.addFlashAttribute("error", "Audit failed: " + e.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/audit/{id}")
    public String auditReport(@PathVariable Long id, Model model) {
        AuditReport r = auditRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Report not found"));
        model.addAttribute("report", r);
        model.addAttribute("fmt", DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
        return "audit";
    }

    // ── Entitlement calculator ───────────────────────────────────────────

    @GetMapping("/calculator")
    public String calculator(@RequestParam(required = false) String category,
                              @RequestParam(defaultValue = "4") int familySize,
                              Model model) {
        Map<String, Integer> ent = null;
        if (category != null && !category.isBlank()) {
            ent = refData.calculateEntitlement(category, familySize);
        }
        model.addAttribute("categories",  refData.getCategories());
        model.addAttribute("category",    category);
        model.addAttribute("familySize",  familySize);
        model.addAttribute("entitlement", ent);
        model.addAttribute("catColors",   buildCatColorMap());
        return "calculator";
    }

    // ── Contract viewer ──────────────────────────────────────────────────

    @GetMapping("/contract")
    public String contractPage(Model model) {
        model.addAttribute("solidityCode",  SOLIDITY_CODE);
        model.addAttribute("pythonGhost",   PYTHON_GHOST);
        return "contract";
    }

    // ── FPS Dealer Dashboard ─────────────────────────────────────────────

    /**
     * Main FPS dashboard — shows recent distributions, flagged deliveries,
     * and pending confirmations.
     *
     * @param shopId optional filter by FPS shop ID
     */
    @GetMapping("/fps")
    public String fpsDashboard(@RequestParam(required = false) String shopId, Model model) {
        List<FpsDelivery> deliveries;
        if (shopId != null && !shopId.isBlank()) {
            deliveries = fpsRepo.findByFpsShopIdOrderByDealerConfirmedAtDesc(shopId);
        } else {
            deliveries = fpsRepo.findTop20ByOrderByDealerConfirmedAtDesc();
        }
        List<FpsDelivery> flagged = fpsRepo.findByFlaggedTrue();
        List<FpsDelivery> pending = fpsRepo.findByBeneficiaryStatus("PENDING");
        long totalCount = fpsRepo.count();
        long cleanCount = fpsRepo.countByBeneficiaryStatusAndFlagged("CONFIRMED", false);

        model.addAttribute("deliveries",    deliveries);
        model.addAttribute("flaggedCount",  flagged.size());
        model.addAttribute("pendingCount",  pending.size());
        model.addAttribute("totalCount",    totalCount);
        model.addAttribute("cleanCount",    cleanCount);
        model.addAttribute("flagged",       flagged);
        model.addAttribute("shopId",        shopId);
        model.addAttribute("fmt",           FMT);
        return "fps";
    }

    /**
     * FPS beneficiary loader — accepts a beneficiaryId via GET, loads the
     * beneficiary entity, and re-renders the FPS dashboard with the beneficiary
     * pre-populated in the distribution form.
     *
     * Previously missing: fps.html had <form action="/fps/select" method="get">
     * but no controller handler existed, causing a 404 error.
     */
    @GetMapping("/fps/select")
    public String fpsSelectBeneficiary(
            @RequestParam(required = false) Long beneficiaryId, Model model) {

        List<FpsDelivery> deliveries  = fpsRepo.findTop20ByOrderByDealerConfirmedAtDesc();
        List<FpsDelivery> flagged     = fpsRepo.findByFlaggedTrue();
        List<FpsDelivery> pending     = fpsRepo.findByBeneficiaryStatus("PENDING");
        long totalCount = fpsRepo.count();
        long cleanCount = fpsRepo.countByBeneficiaryStatusAndFlagged("CONFIRMED", false);

        model.addAttribute("deliveries",    deliveries);
        model.addAttribute("flaggedCount",  flagged.size());
        model.addAttribute("pendingCount",  pending.size());
        model.addAttribute("totalCount",    totalCount);
        model.addAttribute("cleanCount",    cleanCount);
        model.addAttribute("flagged",       flagged);
        model.addAttribute("fmt",           FMT);
        model.addAttribute("selectedBeneficiaryId", beneficiaryId);
        model.addAttribute("shopId",        null);  // no shop filter active on select endpoint

        if (beneficiaryId != null) {
            Optional<Beneficiary> bene = beneSvc.findById(beneficiaryId);
            if (bene.isPresent()) {
                Beneficiary b = bene.get();
                if ("GHOST".equals(b.getStatus())) {
                    model.addAttribute("fpsError",
                        "Beneficiary #" + beneficiaryId + " is flagged as GHOST — distributions cannot be recorded.");
                } else {
                    model.addAttribute("selectedBeneficiary", b);
                }
            } else {
                // selectedBeneficiary stays null → fps.html shows "not found" alert
            }
        }

        return "fps";
    }

    // ── FPS Distribution record ──────────────────────────────────────────

    @PostMapping("/fps/distribute/{beneficiaryId}")
    public String recordDistribution(
            @PathVariable Long beneficiaryId,
            @RequestParam String fpsOperatorName,
            @RequestParam String deliveryMonth,
            @RequestParam(defaultValue = "0") int dealerRiceKg,
            @RequestParam(defaultValue = "0") int dealerWheatKg,
            @RequestParam(defaultValue = "0") int dealerSugarKg,
            @RequestParam(defaultValue = "DISTRIBUTED") String dealerStatus,
            RedirectAttributes flash) {

        Beneficiary b = beneSvc.findById(beneficiaryId)
            .orElseThrow(() -> new RuntimeException("Beneficiary not found"));

        if ("GHOST".equals(b.getStatus())) {
            flash.addFlashAttribute("error",
                "Cannot record distribution — beneficiary #" + beneficiaryId + " is flagged as GHOST.");
            return "redirect:/fps";
        }

        String txHash = "0x" + Integer.toHexString(Math.abs(b.hashCode()))
                        + Long.toHexString(System.currentTimeMillis());

        // ── 3-Layer fraud detection at distribution time ────────────────────
        List<String> fraudReasons = new ArrayList<>();

        // Layer 1 — Ghost beneficiary (duplicate Aadhaar / already flagged)
        if ("GHOST".equals(b.getStatus())) {
            fraudReasons.add("[L1] Beneficiary is flagged as GHOST: " + b.getGhostReason());
        }

        // Layer 2 — Duplicate claim: same beneficiary, same month
        long claimCount = fpsRepo.countByBeneficiaryIdAndDeliveryMonth(b.getId(), deliveryMonth);
        if (claimCount > 0) {
            fraudReasons.add("[L2] DUPLICATE CLAIM: " + claimCount
                + " prior distribution(s) already recorded for month " + deliveryMonth
                + " — possible double-dipping");
        }

        // Layer 3 — Cross-state velocity: non-ONORC beneficiary claiming in wrong state
        if (!Boolean.TRUE.equals(b.getMigrant())
                && b.getClaimState() != null
                && !b.getClaimState().isBlank()
                && !b.getClaimState().equals(b.getStateCode())) {
            fraudReasons.add("[L3] CROSS-STATE FRAUD: Non-ONORC beneficiary registered in "
                + b.getStateCode() + " has prior cross-state claim in " + b.getClaimState());
        }

        boolean isFlagged = !fraudReasons.isEmpty();
        String flagReason = isFlagged ? String.join(" | ", fraudReasons) : null;

        if (isFlagged) {
            flash.addFlashAttribute("warning",
                "⚠️ FRAUD ALERT: Distribution flagged — " + String.join("; ", fraudReasons));
        }

        FpsDelivery delivery = FpsDelivery.builder()
            .beneficiaryId(b.getId())
            .beneficiaryName(b.getFullName())
            .fpsShopId(b.getFpsShopId())
            .fpsOperatorName(fpsOperatorName)
            .stateCode(b.getStateCode())
            .deliveryMonth(deliveryMonth)
            .dealerRiceKg(dealerRiceKg)
            .dealerWheatKg(dealerWheatKg)
            .dealerSugarKg(dealerSugarKg)
            .dealerStatus(dealerStatus)
            .beneficiaryStatus("CONFIRMED")
            .txHash(txHash)
            .blockHeight(b.getBlockHeight())
            .flagged(isFlagged)
            .flagReason(flagReason)
            .dealerConfirmedAt(LocalDateTime.now())
            .build();

        fpsRepo.save(delivery);
        flash.addFlashAttribute("success",
            "✅ Distribution recorded on-chain. Beneficiary confirmation pending. TX: "
            + txHash.substring(0, Math.min(14, txHash.length())) + "…");
        return "redirect:/fps";
    }

    // ── Beneficiary confirmation removed — to be added later ──────────────

    // ── Stats JSON API (consumed by GovStack Portal) ─────────────────────

    @GetMapping("/api/stats")
    @ResponseBody
    public Map<String, Object> apiStats() {
        Map<String, Long> stats = beneSvc.getStats();
        long deliveryMonthlyLoss = beneRepo.findByStatus("GHOST").stream()
            .mapToLong(b -> ghostSvc.estimatedeliveryMonthlyLoss(b.getCategory()))
            .sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("app",              "AnnaSuraksha PDS Ledger");
        result.put("total",            stats.getOrDefault("total", 0L));
        result.put("active",           stats.getOrDefault("active", 0L));
        result.put("ghosts",           stats.getOrDefault("ghosts", 0L));
        result.put("migrants",         stats.getOrDefault("migrants", 0L));
        result.put("deliveryMonthly_loss_rs",  deliveryMonthlyLoss);
        result.put("chain_valid",      chainSvc.validateChain());
        result.put("fps_flagged",      fpsRepo.findByFlaggedTrue().size());
        result.put("fps_pending",      fpsRepo.findByBeneficiaryStatus("PENDING").size());
        result.put("status",           "online");
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Map<String, String> buildCatColorMap() {
        Map<String, String> m = new HashMap<>();
        refData.getCategories().forEach(c -> m.put(c.code(), c.color()));
        return m;
    }

    // ── Embedded code snippets for /contract page ─────────────────────────

    private static final String SOLIDITY_CODE = """
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";
import "@openzeppelin/contracts/utils/cryptography/EIP712.sol";

/**
 * @title  RationLedger
 * @notice NFSA beneficiary registry on Polygon (India Stack)
 * @dev    Aadhaar stored as keccak256 hash — raw number never on-chain
 */
contract RationLedger is AccessControl, EIP712 {
    using ECDSA for bytes32;

    bytes32 public constant REGISTRAR_ROLE = keccak256("REGISTRAR_ROLE");
    bytes32 public constant FPS_ROLE       = keccak256("FPS_ROLE");
    bytes32 public constant AUDITOR_ROLE   = keccak256("AUDITOR_ROLE");

    enum Category { AAY, BPL, PHH, APL }
    enum Status   { ACTIVE, GHOST, SUSPENDED }

    struct Beneficiary {
        bytes32 aadhaarHash;    // keccak256(aadhaarNumber)
        bytes32 voterIdHash;    // secondary dedup
        string  name;
        string  stateCode;
        Category category;
        uint8   familySize;
        uint16  riceKg;
        uint16  wheatKg;
        uint8   sugarKg;
        Status  status;
        bool    migrant;        // ONORC eligible
        uint256 registeredAt;
        uint256 lastClaimAt;
        uint32  claimCount;
    }

    mapping(uint256 => Beneficiary) public beneficiaries;
    mapping(bytes32 => uint256)     public aadhaarToId;
    mapping(bytes32 => bool)        public usedAadhaar;

    uint256 public nextId = 1;
    uint256 public ghostCount;

    bytes32 public stateRoot;

    event BeneficiaryRegistered(uint256 indexed id, bytes32 aadhaarHash,
                                string stateCode, Category category);
    event ClaimRecorded(uint256 indexed id, address fps,
                        uint16 rice, uint16 wheat, uint256 timestamp);
    event GhostFlagged(uint256 indexed id, string reason, address flaggedBy);
    event StateRootUpdated(bytes32 newRoot, uint256 blockNumber);

    constructor() EIP712("RationLedger", "1") {
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(REGISTRAR_ROLE,     msg.sender);
        _grantRole(AUDITOR_ROLE,       msg.sender);
    }

    function register(
        bytes32 aadhaarHash,
        bytes32 voterIdHash,
        string  calldata name,
        string  calldata stateCode,
        Category category,
        uint8   familySize
    ) external onlyRole(REGISTRAR_ROLE) returns (uint256) {
        require(!usedAadhaar[aadhaarHash], "Duplicate Aadhaar");

        (uint16 rice, uint16 wheat, uint8 sugar) = _entitlement(category, familySize);

        uint256 id = nextId++;
        beneficiaries[id] = Beneficiary({
            aadhaarHash   : aadhaarHash,
            voterIdHash   : voterIdHash,
            name          : name,
            stateCode     : stateCode,
            category      : category,
            familySize    : familySize,
            riceKg        : rice,
            wheatKg       : wheat,
            sugarKg       : sugar,
            status        : Status.ACTIVE,
            migrant       : false,
            registeredAt  : block.timestamp,
            lastClaimAt   : 0,
            claimCount    : 0
        });
        aadhaarToId[aadhaarHash] = id;
        usedAadhaar[aadhaarHash] = true;

        emit BeneficiaryRegistered(id, aadhaarHash, stateCode, category);
        return id;
    }

    function recordClaim(uint256 id) external onlyRole(FPS_ROLE) {
        Beneficiary storage b = beneficiaries[id];
        require(b.status == Status.ACTIVE, "Beneficiary not active");
        b.lastClaimAt = block.timestamp;
        b.claimCount++;
        emit ClaimRecorded(id, msg.sender,
                           b.riceKg, b.wheatKg, block.timestamp);
    }

    function flagGhost(uint256 id, string calldata reason)
        external onlyRole(AUDITOR_ROLE)
    {
        beneficiaries[id].status = Status.GHOST;
        ghostCount++;
        emit GhostFlagged(id, reason, msg.sender);
    }

    function _entitlement(Category cat, uint8 fam)
        internal pure returns (uint16 rice, uint16 wheat, uint8 sugar)
    {
        if (cat == Category.AAY)  return (14,       21,       1);
        if (cat == Category.BPL)  return (5 * fam,  3 * fam,  0);
        if (cat == Category.PHH)  return (5 * fam,  0,        0);
        return (3 * fam, 2 * fam, 0); // APL
    }
}""";

    private static final String PYTHON_GHOST = """
# ghost_detector.py  —  3-layer ghost detection (Python microservice)
# Runs alongside Spring Boot, called via REST or message queue
# pip install groq scikit-learn pandas river

import os, hashlib, json
from groq import Groq
from sklearn.ensemble import IsolationForest
from river import anomaly
import numpy as np

client = Groq(api_key=os.environ.get("GROQ_API_KEY"))

# ── Layer 1: Duplicate Aadhaar ─────────────────────────────────────────────
def layer1_duplicate(records: list[dict]) -> list[dict]:
    seen = {}
    ghosts = []
    for r in records:
        h = hashlib.sha256(r["aadhaar"].encode()).hexdigest()
        if h in seen:
            ghosts.append({**r, "ghost_reason": f"Duplicate in {seen[h]['state']}",
                           "layer": "LAYER_1_DUPLICATE"})
        else:
            seen[h] = r
    return ghosts

# ── Layer 2: IsolationForest anomaly detection ─────────────────────────────
def layer2_ml(records: list[dict]) -> list[dict]:
    features = np.array([
        [r["family_size"], r["rice_kg"],
         r["claim_count"], r["days_since_last_claim"]]
        for r in records
    ])
    clf = IsolationForest(contamination=0.05, random_state=42)
    preds = clf.fit_predict(features)
    return [
        {**r, "ghost_reason": "Anomalous claim pattern (IsolationForest)",
         "layer": "LAYER_2_PATTERN"}
        for r, p in zip(records, preds) if p == -1
    ]

# ── Layer 3: Velocity check ────────────────────────────────────────────────
def layer3_velocity(records: list[dict]) -> list[dict]:
    ghosts = []
    for r in records:
        if r.get("claim_state") and r["claim_state"] != r["state"]:
            hours = r.get("hours_since_home_claim", 999)
            if hours < 24:
                ghosts.append({**r,
                    "ghost_reason": f"Cross-state claim in {r['claim_state']} within {hours}h",
                    "layer": "LAYER_3_VELOCITY"})
    return ghosts

# ── Layer 4: Groq AI reasoning ──────────────────────────────────────────────
def layer4_ai(flagged: list[dict]) -> str:
    resp = client.chat.completions.create(
        model="llama-3.3-70b-versatile", max_tokens=1000,
        messages=[
            {"role": "system", "content":
                "You are India's PDS fraud auditor. For each flagged record: "
                "confirm ghost or dismiss, explain why, estimate deliveryMonthly loss ₹. "
                "End with total estimated annual savings if all ghosts removed."},
            {"role": "user", "content": json.dumps(flagged, indent=2)}
        ]
    )
    return resp.choices[0].message.content

if __name__ == "__main__":
    sample = [
        {"id":1,"name":"Ramesh","aadhaar":"999988887777","state":"UP",
         "category":"BPL","family_size":5,"rice_kg":25,
         "claim_count":12,"days_since_last_claim":2,"claim_state":"MH",
         "hours_since_home_claim":3},
        {"id":2,"name":"Ramesh (Ghost)","aadhaar":"999988887777","state":"MH",
         "category":"BPL","family_size":5,"rice_kg":25,
         "claim_count":12,"days_since_last_claim":2,"claim_state":"MH",
         "hours_since_home_claim":3},
    ]
    l1 = layer1_duplicate(sample)
    l3 = layer3_velocity(sample)
    all_ghosts = l1 + l3
    print(f"Total ghosts: {len(all_ghosts)}")
    ai_report = layer4_ai(all_ghosts)
    print(ai_report)""";
}
