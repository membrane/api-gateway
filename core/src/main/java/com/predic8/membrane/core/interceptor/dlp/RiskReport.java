package com.predic8.membrane.core.interceptor.dlp;

import java.util.*;

public class RiskReport {

    private final Map<String, String> matchedFields = new LinkedHashMap<>();
    private final Map<String, String> fieldCategories = new LinkedHashMap<>();
    private final Map<String, Integer> riskCounts = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> riskDetails = new LinkedHashMap<>();

    private static final List<String> RISK_LEVELS = List.of("high", "medium", "low", "unclassified");

    public void recordField(String field, String riskLevel, String category) {
        String level = normalizeRiskLevel(riskLevel);
        matchedFields.put(field, level);
        fieldCategories.put(field, category);

        riskCounts.merge(level, 1, Integer::sum);
        riskDetails
                .computeIfAbsent(level, r -> new LinkedHashMap<>())
                .merge(field, 1, Integer::sum);
    }

    public String getCategoryOf(String field) {
        return fieldCategories.getOrDefault(field, "Unknown");
    }

    public String getCategory() {
        if (riskCounts.getOrDefault("high", 0) > 0) return "high";
        if (riskCounts.getOrDefault("medium", 0) > 0) return "medium";
        if (riskCounts.getOrDefault("low", 0) > 0) return "low";
        return "unclassified";
    }

    public String getFormattedSummaryLog() {
        StringBuilder sb = new StringBuilder();
        sb.append("[Summary]: Risk=").append(getCategory());

        for (String level : RISK_LEVELS) {
            sb.append(" | ").append(level).append("=")
                    .append(riskCounts.getOrDefault(level, 0));
        }

        List<String> fieldsOutput = new ArrayList<>();
        for (String level : RISK_LEVELS) {
            Map<String, Integer> details = riskDetails.getOrDefault(level, Collections.emptyMap());
            if (!details.isEmpty()) {
                String fieldList = String.join(", ", details.keySet());
                fieldsOutput.add(level + "=[" + fieldList + "]");
            }
        }

        if (!fieldsOutput.isEmpty()) {
            sb.append(" | Fields: ").append(String.join(", ", fieldsOutput));
        }

        return sb.toString();
    }

    public Map<String, String> getMatchedFields() {
        return Collections.unmodifiableMap(matchedFields);
    }

    public Map<String, Integer> getRiskCounts() {
        return Collections.unmodifiableMap(riskCounts);
    }

    private String normalizeRiskLevel(String level) {
        return switch (level.toLowerCase()) {
            case "high", "medium", "low" -> level.toLowerCase();
            default -> "unclassified";
        };
    }
}
