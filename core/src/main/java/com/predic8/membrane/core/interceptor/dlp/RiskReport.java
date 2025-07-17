package com.predic8.membrane.core.interceptor.dlp;

import java.util.*;

public class RiskReport {

    private static final List<String> LEVELS = List.of("high", "medium", "low", "unclassified");

    private final Map<String, String> matchedFields = new LinkedHashMap<>();
    private final EnumMap<Category, Integer> riskCounts = new EnumMap<>(Category.class);
    private final EnumMap<Category, Map<String, Integer>> riskDetails = new EnumMap<>(Category.class);

    public void recordField(String field, String riskLevel) {
        matchedFields.put(field, riskLevel);
        Category category = Category.fromString(riskLevel);
        riskCounts.merge(category, 1, Integer::sum);
        riskDetails.computeIfAbsent(category, r -> new LinkedHashMap<>()).merge(field, 1, Integer::sum);
    }

    public Category getCategory() {
        if (riskCounts.getOrDefault("high", 0) > 0) return Category.HIGH;
        if (riskCounts.getOrDefault("medium", 0) > 0) return Category.MEDIUM;
        if (riskCounts.getOrDefault("low", 0) > 0) return Category.LOW;
        return Category.UNCLASSIFIED;
    }

    public enum Category {
        HIGH(3), MEDIUM(2), LOW(1), UNCLASSIFIED(0);

        private final int severity;

        Category(int severity) {
            this.severity = severity;
        }

        public int getSeverity() {
            return severity;
        }

        public static Category fromString(String level) {
            return switch (level.toLowerCase()) {
                case "high" -> HIGH;
                case "medium" -> MEDIUM;
                case "low" -> LOW;
                default -> UNCLASSIFIED;
            };
        }
    }

    public Map<String, Object> getStructuredReport() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Category level : Category.values()) {
            int count = riskCounts.getOrDefault(level, 0);
            out.put(level.name().toLowerCase() + "_risk", count);

            if (riskDetails.containsKey(level)) {
                out.put(level.name().toLowerCase() + "_details", Map.copyOf(riskDetails.get(level)));
            }
        }
        out.put("category", getCategory().name());
        return out;
    }

    public Map<String, String> getMatchedFields() {
        return Collections.unmodifiableMap(matchedFields);
    }

    public Map<Category, Integer> getRiskCounts() {
        return Collections.unmodifiableMap(riskCounts);
    }
}
