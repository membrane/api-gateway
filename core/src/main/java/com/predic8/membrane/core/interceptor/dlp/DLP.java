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
        int high = 0;
        int medium = 0;
        int low = 0;
        int unclassified = 0;

        for (String field : fieldNames) {
            String risk = riskDict.getOrDefault(field.toLowerCase(), "unclassified");
            matchedFields.put(field, risk);
            switch (risk.toLowerCase()) {
                case "high":
                    high++;
                    break;
                case "medium":
                    medium++;
                    break;
                case "low":
                    low++;
                    break;
                default:
                    unclassified++;
                    break;
            }
        }

        log.info("High: {}, Medium: {}, Low: {}, Unclassified: {}", high, medium, low, unclassified);


        Map<String, Object> result = new LinkedHashMap<>();
        result.put("high_risk", high);
        result.put("medium_risk", medium);
        result.put("low_risk", low);
        result.put("unclassified", unclassified);
        result.put("matched_fields", matchedFields);

        return result;
    }
}
