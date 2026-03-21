package com.rationchain.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ReferenceDataService {

    public record StateInfo(String code, String name, long beneficiaries, int coverage) {}
    public record Category(String code, String label, String rice, String wheat, String sugar, String color) {}
    public record SupplyNode(String id, String label, String icon, String detail) {}

    public List<StateInfo> getStates() {
        return List.of(
            new StateInfo("UP",  "Uttar Pradesh",    1_49_76_000L, 91),
            new StateInfo("MH",  "Maharashtra",      7_16_00_000L, 82),
            new StateInfo("WB",  "West Bengal",      6_31_00_000L, 87),
            new StateInfo("MP",  "Madhya Pradesh",   5_39_00_000L, 93),
            new StateInfo("RJ",  "Rajasthan",        4_44_00_000L, 88),
            new StateInfo("KA",  "Karnataka",        4_12_00_000L, 79),
            new StateInfo("TN",  "Tamil Nadu",       3_56_00_000L, 94),
            new StateInfo("GJ",  "Gujarat",          3_14_00_000L, 76),
            new StateInfo("AP",  "Andhra Pradesh",   2_98_00_000L, 85),
            new StateInfo("OD",  "Odisha",           2_78_00_000L, 91)
        );
    }

    public List<String> getStateCodes() {
        return getStates().stream().map(StateInfo::code).toList();
    }

    public List<Category> getCategories() {
        return List.of(
            new Category("AAY", "Antyodaya Anna Yojana", "14 kg", "21 kg", "1 kg", "#7c3aed"),
            new Category("BPL", "Below Poverty Line",    "5 kg",  "3 kg",  "0",    "#ef4444"),
            new Category("PHH", "Priority Household",    "5 kg",  "0",     "0",    "#f59e0b"),
            new Category("APL", "Above Poverty Line",    "3 kg",  "2 kg",  "0",    "#3b82f6")
        );
    }

    public List<SupplyNode> getSupplyChain() {
        return List.of(
            new SupplyNode("FCI",       "FCI Godown",        "🏛️", "Food Corporation of India — national buffer stock"),
            new SupplyNode("STATE_WH",  "State Warehouse",   "🏗️", "State-level storage, quality testing"),
            new SupplyNode("DIST_DEPOT","District Depot",    "🚚", "District allocation, truck dispatch"),
            new SupplyNode("FPS",       "Fair Price Shop",   "🏪", "Last-mile delivery, biometric claim"),
            new SupplyNode("BENE",      "Beneficiary",       "👨‍🌾", "Family receives entitlement")
        );
    }

    /** Entitlement calculator — kg/deliveryMonth for given category and family size */
    public Map<String, Integer> calculateEntitlement(String category, int familySize) {
        Map<String, Integer> e = new LinkedHashMap<>();
        switch (category) {
            case "AAY" -> { e.put("rice", 14); e.put("wheat", 21); e.put("sugar", 1); }
            case "BPL" -> { e.put("rice", 5 * familySize); e.put("wheat", 3 * familySize); e.put("sugar", 0); }
            case "PHH" -> { e.put("rice", 5 * familySize); e.put("wheat", 0); e.put("sugar", 0); }
            case "APL" -> { e.put("rice", 3 * familySize); e.put("wheat", 2 * familySize); e.put("sugar", 0); }
            default    -> { e.put("rice", 0); e.put("wheat", 0); e.put("sugar", 0); }
        }
        return e;
    }

    public String getCategoryColor(String category) {
        return getCategories().stream()
            .filter(c -> c.code().equals(category))
            .map(Category::color)
            .findFirst().orElse("#4ade80");
    }

    public String stateName(String code) {
        return getStates().stream()
            .filter(s -> s.code().equals(code))
            .map(StateInfo::name)
            .findFirst().orElse(code);
    }
}
