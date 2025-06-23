package com.predic8.membrane.core;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.predic8.membrane.core.http.Message;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.logging.Logger;

public class DLP {

    private static final Logger logger = Logger.getLogger(DLP.class.getName());

    private final Map<String, String> riskDict;
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    public DLP(Map<String, String> riskDict) {
        this.riskDict = riskDict;
    }

    public Map<String, Object> analyze(Message msg) {
        return evaluateRisk(extractFieldNames(msg));
    }

    private Set<String> extractFieldNames(Message msg) {
        Set<String> fieldNames = new HashSet<>();
        try {
            JsonParser parser = JSON_FACTORY.createParser(new InputStreamReader(msg.getBodyAsStreamDecoded(), msg.getCharset()));
            Deque<String> contextStack = new ArrayDeque<>();
            String currentFieldName = null;

            while (parser.nextToken() != null) {
                switch (parser.getCurrentToken()) {
                    case START_OBJECT:
                    case START_ARRAY:
                        if (currentFieldName != null) {
                            contextStack.push(currentFieldName);
                            currentFieldName = null;
                        }
                        break;

                    case END_OBJECT:
                    case END_ARRAY:
                        if (!contextStack.isEmpty()) {
                            contextStack.pop();
                        }
                        break;

                    case FIELD_NAME:
                        currentFieldName = parser.currentName();
                        fieldNames.add(buildFullPath(contextStack, currentFieldName).toLowerCase());
                        break;

                    default:
                        break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error extracting JSON field names", e);
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
            String risk = riskDict.getOrDefault(field, "unclassified");
            matchedFields.put(field, risk);

            switch (risk.toLowerCase()) {
                case "high": high++; break;
                case "medium": medium++; break;
                case "low": low++; break;
                default: unclassified++; break;
            }
        }

        logger.info(String.format(
                "High: %d, Medium: %d, Low: %d, Unclassified: %d",
                high, medium, low, unclassified
        ));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("high_risk", high);
        result.put("medium_risk", medium);
        result.put("low_risk", low);
        result.put("unclassified", unclassified);
        result.put("matched_fields", matchedFields);

        return result;
    }
}
