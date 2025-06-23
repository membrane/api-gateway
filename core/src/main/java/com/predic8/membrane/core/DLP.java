package com.predic8.membrane.core;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.predic8.membrane.core.http.Message;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

public class DLP {

    private static final Logger logger = Logger.getLogger(DLP.class.getName());

    private final Message msg;
    private final Map<String, String> riskDict;

    public DLP(Message msg, Map<String, String> riskDict) {
        this.msg = msg;
        this.riskDict = riskDict;
    }

    public Map<String, Object> analyze() {
        Map<String, String> matchedFields = new HashMap<>();
        int high = 0;
        int medium = 0;
        int low = 0;
        int unclassified = 0;

        try {
            JsonFactory factory = new JsonFactory();
            JsonParser parser = factory.createParser(new InputStreamReader(msg.getBodyAsStreamDecoded(), msg.getCharset()));
            String fieldName;

            while (parser.nextToken() != null) {
                if (parser.getCurrentToken() == JsonToken.FIELD_NAME) {
                    fieldName = parser.currentName().toLowerCase();

                    String risk = riskDict.getOrDefault(fieldName, "unclassified");
                    matchedFields.put(fieldName, risk);

                    switch (risk) {
                        case "High":
                            high++; break;
                        case "Medium":
                            medium++; break;
                        case "Low":
                            low++; break;
                        default:
                            unclassified++; break;
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error analyzing JSON", e);
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
