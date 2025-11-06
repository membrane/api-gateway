package com.predic8.membrane.core.interceptor.dlp;

import com.predic8.membrane.core.http.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DLPAnalyzerTest {

    private DLPAnalyzer dlpAnalyzer;
    private Message mockMessage;
    private Message mockArrayMessage;

    private static final String JSON = """
            {
              "user": {
                "email": "test@example.com",
                "profile": {
                  "firstName": "John",
                  "lastName": "Doe"
                }
              },
              "active": true
            }
            """;

    private static final String ARRAY_JSON = """
            {
              "array": [
                { "foo": 1, "bar": 2 }
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        mockMessage = mock(Message.class);
        when(mockMessage.getBodyAsStreamDecoded())
                .thenReturn(new ByteArrayInputStream(JSON.getBytes(StandardCharsets.UTF_8)));
        when(mockMessage.getCharset()).thenReturn(StandardCharsets.UTF_8.name());

        mockArrayMessage = mock(Message.class);
        when(mockArrayMessage.getBodyAsStreamDecoded())
                .thenReturn(new ByteArrayInputStream(ARRAY_JSON.getBytes(StandardCharsets.UTF_8)));
        when(mockArrayMessage.getCharset()).thenReturn(StandardCharsets.UTF_8.name());

        Map<String, String> riskDict = new HashMap<>();
        riskDict.put("user.email", "high");
        riskDict.put("active", "low");
        riskDict.put("array.foo", "high");
        riskDict.put("array.bar", "low");

        Map<String, String> catDict = new HashMap<>();
        catDict.put("user.email", "personal");
        catDict.put("active", "technical");

        dlpAnalyzer = new DLPAnalyzer(riskDict, catDict);
    }


    @Test
    void analyzeSimpleJson() {
        RiskReport report = dlpAnalyzer.analyze(mockMessage);

        assertEquals(1, report.getRiskCounts().getOrDefault("high", 0));
        assertEquals(1, report.getRiskCounts().getOrDefault("low", 0));
        assertEquals(0, report.getRiskCounts().getOrDefault("medium", 0));
        assertEquals(2, report.getRiskCounts().getOrDefault("unknown", 0));

        assertEquals("high", report.getMatchedFields().get("user.email"));
        assertEquals("low", report.getMatchedFields().get("active"));
        assertEquals("unknown", report.getMatchedFields().get("user.profile.firstName"));
        assertEquals("unknown", report.getMatchedFields().get("user.profile.lastName"));
    }

    @Test
    void analyzeArrayJson() {
        RiskReport report = dlpAnalyzer.analyze(mockArrayMessage);

        assertEquals(1, report.getRiskCounts().getOrDefault("high", 0));
        assertEquals(1, report.getRiskCounts().getOrDefault("low", 0));
        assertEquals(0, report.getRiskCounts().getOrDefault("medium", 0));
        assertEquals(0, report.getRiskCounts().getOrDefault("unknown", 0));

        assertEquals("high", report.getMatchedFields().get("array.foo"));
        assertEquals("low", report.getMatchedFields().get("array.bar"));
    }

    @Test
    void allExpectedFieldsPresent() {
        RiskReport report = dlpAnalyzer.analyze(mockMessage);

        assertTrue(report.getMatchedFields().containsKey("user.email"));
        assertTrue(report.getMatchedFields().containsKey("user.profile.firstName"));
        assertTrue(report.getMatchedFields().containsKey("user.profile.lastName"));
        assertTrue(report.getMatchedFields().containsKey("active"));
    }
}
