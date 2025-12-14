package com.predic8.membrane.core.interceptor.dlp;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.http.Message;

import java.io.InputStream;
import java.util.*;

public class DLPAnalyzer {

    private static final JsonFactory JSON_FACTORY = new JsonFactoryBuilder()
            .configure(JsonReadFeature.ALLOW_TRAILING_COMMA, true)
            .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS, false)
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(64)
                    .maxStringLength(16 * 1024)
                    .build())
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper(JSON_FACTORY);

    private final Map<String, String> riskDict;
    private final Map<String, String> categoryMap;

    public DLPAnalyzer(Map<String, String> rawRiskMap, Map<String, String> categoryMap) {
        this.riskDict = normalizeRiskLevels(rawRiskMap);
        this.categoryMap = categoryMap;
    }

    private Map<String, String> normalizeRiskLevels(Map<String, String> raw) {
        Map<String, String> result = new HashMap<>();
        raw.forEach((key, value) -> result.put(key, normalizeLevel(value)));
        return result;
    }

    private String normalizeLevel(String level) {
        switch (level.toLowerCase()) {
            case "high":
            case "medium":
            case "low":
                return level.toLowerCase();
            default:
                return "unknown";
        }
    }

    public RiskReport analyze(Message msg) {
        try (InputStream is = msg.getBodyAsStreamDecoded()) {
            JsonNode root = MAPPER.readTree(is);
            RiskReport report = new RiskReport();
            traverse(root, new ArrayDeque<>(), report);
            return report;
        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze message", e);
        }
    }

    private void traverse(JsonNode node, Deque<String> path, RiskReport report) {
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(fieldName -> {
                path.addLast(fieldName);
                traverse(node.get(fieldName), path, report);
                path.removeLast();
            });
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                traverse(child, path, report);
            }
        } else {
            String fullPath = String.join(".", path);
            String lastSegment = path.peekLast() != null ? path.peekLast() : "";

            String riskLevel = classify(fullPath, lastSegment);
            String category = categoryMap.getOrDefault(fullPath, categoryMap.getOrDefault(lastSegment, "Unknown"));

            report.recordField(fullPath, riskLevel, category);
        }
    }

    private String classify(String fullPath, String simpleName) {
        return Optional.ofNullable(riskDict.get(fullPath))
                .or(() -> Optional.ofNullable(riskDict.get(simpleName)))
                .orElse("unknown");
    }
}
