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

    private final Map<String, RiskReport.Category> riskDict;

    public DLPAnalyzer(Map<String, String> rawRiskMap) {
        this.riskDict = mapToEnumRiskLevels(rawRiskMap);
    }

    private Map<String, RiskReport.Category> mapToEnumRiskLevels(Map<String, String> raw) {
        Map<String, RiskReport.Category> result = new HashMap<>();
        raw.forEach((key, value) -> {
            RiskReport.Category level = RiskReport.Category.fromString(value);
            result.put(key.toLowerCase(Locale.ROOT), level);
        });
        return result;
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
            String fullPath = String.join(".", path).toLowerCase(Locale.ROOT);
            String lastSegment = path.peekLast() != null ? path.peekLast().toLowerCase(Locale.ROOT) : "";

            RiskReport.Category level = classify(fullPath, lastSegment);
            report.recordField(fullPath, level.name());
        }
    }

    private RiskReport.Category classify(String fullPath, String simpleName) {
        return Optional.ofNullable(riskDict.get(fullPath))
                .or(() -> Optional.ofNullable(riskDict.get(simpleName)))
                .orElse(RiskReport.Category.UNCLASSIFIED);
    }
}
