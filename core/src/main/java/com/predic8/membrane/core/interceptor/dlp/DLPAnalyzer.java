package com.predic8.membrane.core.interceptor.dlp;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.predic8.membrane.core.http.Message;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DLPAnalyzer {

    private static final int MAX_DEPTH = 64;
    private static final int MAX_STRING_LENGTH = 16 * 1024; // 16 KiB

    private static final JsonFactory JSON_FACTORY = JsonFactory.builder()
            .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS, false)
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(MAX_DEPTH)
                    .maxStringLength(MAX_STRING_LENGTH)
                    .build())
            .build();

    private final Map<String, String> riskDict;

    public DLPAnalyzer(Map<String, String> riskDict) {
        this.riskDict = Map.copyOf(riskDict); // immutable defensive copy
    }

    public RiskReport analyze(Message msg) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(msg.getBodyAsStreamDecoded(), Optional.ofNullable(msg.getCharset())
                .map(Charset::forName)
                .orElse(StandardCharsets.UTF_8)));
             JsonParser parser = JSON_FACTORY.createParser(reader)) {

            Deque<String> ctx = new ArrayDeque<>();
            RiskReport report = new RiskReport();
            String currentField = null;

            while (parser.nextToken() != null) {
                switch (parser.currentToken()) {
                    case FIELD_NAME -> currentField = parser.currentName();
                    case START_OBJECT, START_ARRAY -> {
                        if (currentField != null) {
                            ctx.addLast(currentField);
                            currentField = null;
                        }
                    }
                    case END_OBJECT, END_ARRAY -> {
                        if (!ctx.isEmpty()) ctx.removeLast();
                    }
                    default -> {
                        if (currentField != null) {
                            String fullPath = buildFullPath(ctx, currentField);
                            String lvl = classify(fullPath, currentField);
                            report.recordField(fullPath, lvl);
                            currentField = null;
                        }
                    }
                }
            }
            return report;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String classify(String fullPath, String simpleName) {
        return Optional.ofNullable(riskDict.get(fullPath.toLowerCase()))
                .or(() -> Optional.ofNullable(riskDict.get(simpleName.toLowerCase())))
                .orElse("unclassified")
                .toLowerCase();
    }

    private static String buildFullPath(Deque<String> stack, String field) {
        if (stack.isEmpty()) return field;
        List<String> path = new ArrayList<>(stack);
        path.add(field);
        return String.join(".", path);
    }
}
