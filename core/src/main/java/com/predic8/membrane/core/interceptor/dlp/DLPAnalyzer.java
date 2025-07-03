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

    public DLPAnalyzer(Map<String, String> riskDict) {
        this.riskDict = Map.copyOf(riskDict);
    }

    public RiskReport analyze(Message msg) {
        try (InputStream is = msg.getBodyAsStreamDecoded()) {
            RiskReport report = new RiskReport();
            traverse(MAPPER.readTree(is), new ArrayDeque<>(), report);
            return report;
        } catch (Exception e) {
            throw new RuntimeException("Failed to analyse message", e);
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
            String simpleName = path.isEmpty() ? "" : path.getLast().toLowerCase(Locale.ROOT);
            report.recordField(fullPath, classify(fullPath, simpleName));
        }
    }

    private String classify(String fullPath, String simpleName) {
        return Optional.ofNullable(riskDict.get(fullPath))
                .or(() -> Optional.ofNullable(riskDict.get(simpleName)))
                .orElse("unclassified")
                .toLowerCase(Locale.ROOT);
    }
}