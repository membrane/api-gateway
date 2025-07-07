package com.predic8.membrane.core.interceptor.dlp;

import java.util.*;

public class RiskReport {

    private static final List<String> LEVELS = List.of("high", "medium", "low", "unclassified");

    private final Map<String, String> matchedFields = new LinkedHashMap<>();
    private final Map<String, Integer> riskCounts   = new HashMap<>();
    private final Map<String, Map<String, Integer>> riskDetails = new HashMap<>();

    public void recordField(String field, String riskLevel) {
        matchedFields.put(field, riskLevel);
        riskCounts.merge(riskLevel, 1, Integer::sum);
        riskDetails.computeIfAbsent(riskLevel, r -> new LinkedHashMap<>())
                .merge(field, 1, Integer::sum);
    }

    public Category getCategory() {
        if (riskCounts.getOrDefault("high", 0)   > 0) return Category.HIGH;
        if (riskCounts.getOrDefault("medium", 0) > 0) return Category.MEDIUM;
        if (riskCounts.getOrDefault("low", 0)    > 0) return Category.LOW;
        return Category.UNCLASSIFIED;
    }

    public enum Category { HIGH, MEDIUM, LOW, UNCLASSIFIED }

    public Map<String, Object> getLogReport() {
        Map<String, Object> out = new LinkedHashMap<>();
        LEVELS.forEach(level -> {
            out.put(level + "_risk", riskCounts.getOrDefault(level, 0));
            if (riskDetails.containsKey(level))
                out.put(level + "_details", riskDetails.get(level));
        });
        out.put("category", getCategory().name());
        return out;
    }

    Map<String, String>  getMatchedFields() { return matchedFields; }
    Map<String, Integer> getRiskCounts()    { return riskCounts;    }
}
