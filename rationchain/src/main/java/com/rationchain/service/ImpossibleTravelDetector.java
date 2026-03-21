package com.rationchain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import org.springframework.stereotype.Service;

import java.util.Map;


@Service
public class ImpossibleTravelDetector {
    private static final Logger log = LoggerFactory.getLogger(ImpossibleTravelDetector.class);


    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double RAIL_SPEED_KMH  = 120.0;
    private static final double AIR_SPEED_KMH   = 600.0;
    private static final double AIR_OVERHEAD_H  = 3.0;
    private static final double AIR_MIN_DIST_KM = 400.0;

    private static final Map<String, double[]> STATE_COORDS = Map.ofEntries(
        Map.entry("AP",  new double[]{15.9129, 79.7400}),
        Map.entry("AR",  new double[]{27.0844, 93.6053}),
        Map.entry("AS",  new double[]{26.1433, 91.7362}),
        Map.entry("BR",  new double[]{25.5941, 85.1376}),
        Map.entry("CG",  new double[]{21.2514, 81.6296}),
        Map.entry("DL",  new double[]{28.6139, 77.2090}),
        Map.entry("GA",  new double[]{15.4909, 73.8278}),
        Map.entry("GJ",  new double[]{23.2156, 72.6369}),
        Map.entry("HR",  new double[]{30.7333, 76.7794}),
        Map.entry("HP",  new double[]{31.1048, 77.1734}),
        Map.entry("JH",  new double[]{23.3441, 85.3096}),
        Map.entry("KA",  new double[]{12.9716, 77.5946}),
        Map.entry("KL",  new double[]{8.5241,  76.9366}),
        Map.entry("MP",  new double[]{23.2599, 77.4126}),
        Map.entry("MH",  new double[]{19.0760, 72.8777}),
        Map.entry("MN",  new double[]{24.8170, 93.9368}),
        Map.entry("ME",  new double[]{25.5788, 91.8933}),
        Map.entry("MZ",  new double[]{23.7307, 92.7173}),
        Map.entry("NL",  new double[]{25.6747, 94.1086}),
        Map.entry("OD",  new double[]{20.2961, 85.8245}),
        Map.entry("PB",  new double[]{30.7333, 76.7794}),
        Map.entry("RJ",  new double[]{26.9124, 75.7873}),
        Map.entry("SK",  new double[]{27.3314, 88.6138}),
        Map.entry("TN",  new double[]{13.0827, 80.2707}),
        Map.entry("TG",  new double[]{17.3850, 78.4867}),
        Map.entry("TR",  new double[]{23.8315, 91.2868}),
        Map.entry("UP",  new double[]{26.8467, 80.9462}),
        Map.entry("UK",  new double[]{30.0668, 79.0193}),
        Map.entry("WB",  new double[]{22.5726, 88.3639}),
        Map.entry("AN",  new double[]{11.7401, 92.6586}),
        Map.entry("CH",  new double[]{30.7333, 76.7794}),
        Map.entry("DN",  new double[]{20.1809, 73.0169}),
        Map.entry("DD",  new double[]{20.3974, 72.8328}),
        Map.entry("LD",  new double[]{10.5667, 72.6417}),
        Map.entry("PY",  new double[]{11.9416, 79.8083}),
        Map.entry("JK",  new double[]{34.0837, 74.7973}),
        Map.entry("LA",  new double[]{34.1526, 77.5771})
    );

    public record TravelAnalysis(
        String stateA,
        String stateB,
        double distanceKm,
        double minTravelHours,
        double actualHours,
        boolean isImpossible,
        String explanation
    ) {}

    public TravelAnalysis analyse(String stateA, String stateB, double actualHours) {
        if (stateA == null || stateB == null || stateA.equalsIgnoreCase(stateB)) {
            return new TravelAnalysis(stateA, stateB, 0, 0, actualHours, false,
                "Same state — no travel required.");
        }

        double[] coordsA = STATE_COORDS.get(stateA.toUpperCase());
        double[] coordsB = STATE_COORDS.get(stateB.toUpperCase());

        if (coordsA == null || coordsB == null) {
            log.warn("Unknown state code(s): {} / {} — skipping travel analysis", stateA, stateB);
            return new TravelAnalysis(stateA, stateB, -1, -1, actualHours, false,
                "Unknown state code — cannot determine travel feasibility.");
        }

        double distanceKm     = haversineKm(coordsA[0], coordsA[1], coordsB[0], coordsB[1]);
        double minTravelHours = minimumTravelHours(distanceKm);
        boolean impossible    = actualHours < minTravelHours;

        String explanation;
        if (impossible) {
            explanation = String.format(
                "Distance %s -> %s is %.0f km. Minimum realistic travel time: %.1f h. " +
                "Actual gap: %.1f h. IMPOSSIBLE TRAVEL DETECTED.",
                stateA, stateB, distanceKm, minTravelHours, actualHours);
        } else {
            explanation = String.format(
                "Distance %s -> %s is %.0f km (min %.1f h). Actual gap %.1f h — feasible.",
                stateA, stateB, distanceKm, minTravelHours, actualHours);
        }

        log.debug("Travel analysis: {} -> {} | {} km | min {}h | actual {}h | impossible={}",
            stateA, stateB,
            String.format("%.1f", distanceKm),
            String.format("%.1f", minTravelHours),
            String.format("%.1f", actualHours),
            impossible);

        return new TravelAnalysis(stateA, stateB, distanceKm, minTravelHours,
                                  actualHours, impossible, explanation);
    }

    public TravelAnalysis analyse(String stateA, String stateB,
                                   long timestampAMillis, long timestampBMillis) {
        double hours = Math.abs(timestampAMillis - timestampBMillis) / 3_600_000.0;
        return analyse(stateA, stateB, hours);
    }

    public double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a    = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                    + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                    * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    double minimumTravelHours(double distanceKm) {
        double railHours = distanceKm / RAIL_SPEED_KMH;
        if (distanceKm > AIR_MIN_DIST_KM) {
            double airHours = (distanceKm / AIR_SPEED_KMH) + AIR_OVERHEAD_H;
            return Math.min(railHours, airHours);
        }
        return railHours;
    }

    public boolean isKnownState(String stateCode) {
        return stateCode != null && STATE_COORDS.containsKey(stateCode.toUpperCase());
    }

    public double distanceBetweenStates(String stateA, String stateB) {
        double[] a = STATE_COORDS.get(stateA.toUpperCase());
        double[] b = STATE_COORDS.get(stateB.toUpperCase());
        if (a == null || b == null) return -1;
        return haversineKm(a[0], a[1], b[0], b[1]);
    }
}
