package com.predic8.membrane.core.interceptor.dlp;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.predic8.membrane.core.http.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DLP {

    private static final Logger log = LoggerFactory.getLogger(DLP.class);
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    private final Map<String, String> riskDict;

    public DLP(Map<String, String> riskDict) {
        this.riskDict = riskDict;
    }

    public RiskReport analyze(Message msg) {
        try (JsonParser parser = createParser(msg)) {
            Deque<String> contextStack = new ArrayDeque<>();
            RiskReport report = new RiskReport();

            String currentField = null;

            while (parser.nextToken() != null) {
                JsonToken token = parser.currentToken();

                switch (token) {
                    case FIELD_NAME -> currentField = parser.currentName();

                    case START_OBJECT, START_ARRAY -> {
                        if (currentField != null) {
                            contextStack.addLast(currentField);
                            currentField = null;
                        }
                    }

                    case END_OBJECT, END_ARRAY -> {
                        if (!contextStack.isEmpty()) contextStack.removeLast();
                    }

                    default -> {
                        if (currentField != null) {
                            String fullPath = buildFullPath(contextStack, currentField).toLowerCase();
                            report.recordField(fullPath, Optional.ofNullable(riskDict.get(fullPath))
                                    .orElse(riskDict.getOrDefault(currentField.toLowerCase(), "unclassified"))
                                    .toLowerCase());
                            currentField = null;
                        }
                    }
                }
            }
            return report;
        } catch (IOException e) {
            log.error("Parse Error: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private JsonParser createParser(Message msg) throws IOException {
        return JSON_FACTORY.createParser(new InputStreamReader(msg.getBodyAsStreamDecoded(), Optional.ofNullable(msg.getCharset()).orElse(StandardCharsets.UTF_8.name())));
    }

    private String buildFullPath(Deque<String> stack, String field) {
        List<String> path = new ArrayList<>(stack);
        path.add(field);
        return String.join(".", path);
    }
}
