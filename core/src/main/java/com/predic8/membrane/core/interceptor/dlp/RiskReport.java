package com.predic8.membrane.core.interceptor.dlp;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RiskReport {

    private static final List<String> LEVELS = List.of("high", "medium", "low", "unclassified");

    private final Map<String, String> matchedFields = new LinkedHashMap<>();
    private final Map<String, Integer> riskCounts = new HashMap<>();
    private final Map<String, Map<String, Integer>> riskDetails = new HashMap<>();

    public void recordField(String field, String riskLevel) {
        matchedFields.put(field, riskLevel);
        riskCounts.merge(riskLevel, 1, Integer::sum);
        riskDetails.computeIfAbsent(riskLevel, r -> new LinkedHashMap<>()).merge(field, 1, Integer::sum);
    }

    public Map<String, Object> getLogReport() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        LEVELS.forEach(level -> {
            out.put(level + "_risk", riskCounts.getOrDefault(level, 0));
            riskDetails.computeIfPresent(level, (k, v) -> {
                out.put(level + "_details", v);
                return v;
            });
        });
        return out;
    }

    Map<String, String> getMatchedFields() {
        return matchedFields;
    }

    Map<String, Integer> getRiskCounts() {
        return riskCounts;
    }
}