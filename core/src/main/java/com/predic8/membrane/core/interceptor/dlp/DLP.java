package com.predic8.membrane.core.interceptor.dlp;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.predic8.membrane.core.http.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class DLP {

    private static final Logger log = LoggerFactory.getLogger(DLP.class);

    private final Map<String, String> riskDict;

    // JsonFactory is thread-safe and should be reused
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    public DLP(Map<String, String> riskDict) {
        this.riskDict = new HashMap<>();
        riskDict.forEach((key, value) -> this.riskDict.put(key.toLowerCase(), value));
    }

    public Map<String, Object> analyze(Message msg) {
        if (msg == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        return evaluateRisk(extractFieldNames(msg));
    }

    Set<String> extractFieldNames(Message msg) {
        Set<String> fieldNames = new HashSet<>();
        try (JsonParser parser = JSON_FACTORY.createParser(new InputStreamReader(msg.getBodyAsStreamDecoded(), msg.getCharset()))) {
            Deque<String> contextStack = new ArrayDeque<>();
            String currentFieldName = null;

            while (parser.nextToken() != null) {
                switch (parser.getCurrentToken()) {
                    case FIELD_NAME:
                        currentFieldName = parser.currentName();
                        break;

                    case START_OBJECT:
                    case START_ARRAY:
                        if (currentFieldName != null) {
                            fieldNames.add(buildFullPath(contextStack, currentFieldName).toLowerCase());

                            contextStack.addLast(currentFieldName);
                            currentFieldName = null;
                        }
                        break;

                    case END_OBJECT:
                    case END_ARRAY:
                        if (!contextStack.isEmpty()) {
                            contextStack.removeLast();
                        }
                        break;

                    default:
                        if (currentFieldName != null) {
                            fieldNames.add(buildFullPath(contextStack, currentFieldName).toLowerCase());
                            currentFieldName = null;
                        }
                        break;
                }
            }
        } catch (IOException e) {
            log.error("Failed to parse JSON content: {}", e.getMessage());
            return new HashSet<>();
        }
        return fieldNames;
    }

    private String buildFullPath(Deque<String> contextStack, String currentFieldName) {
        List<String> path = new ArrayList<>(contextStack);
        path.add(currentFieldName);
        return String.join(".", path);
    }


    private Map<String, Object> evaluateRisk(Set<String> fieldNames) {
        Map<String, String> matchedFields = new HashMap<>();
        Map<String, Integer> riskCounts = new HashMap<>();
        Map<String, Map<String, Integer>> riskDetails = new HashMap<>();

        for (String field : fieldNames) {
            String risk = riskDict.getOrDefault(field.toLowerCase(), "unclassified").toLowerCase();
            matchedFields.put(field, risk);

            riskCounts.merge(risk, 1, Integer::sum);
            riskDetails.computeIfAbsent(risk, k -> new HashMap<>()).merge(field, 1, Integer::sum);
        }

        log.info("Risk Summary: {}", riskCounts);

        for (Map.Entry<String, Map<String, Integer>> entry : riskDetails.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                log.info("{} Risk Fields: {}", capitalize(entry.getKey()), entry.getValue().keySet());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matched_fields", matchedFields);

        for (String riskLevel : List.of("high", "medium", "low", "unclassified")) {
            result.put(riskLevel + "_risk", riskCounts.getOrDefault(riskLevel, 0));
            Map<String, Integer> details = riskDetails.get(riskLevel);
            if (details != null && !details.isEmpty()) {
                result.put(riskLevel + "_details", details);
            }
        }
        return result;
    }

    private String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
