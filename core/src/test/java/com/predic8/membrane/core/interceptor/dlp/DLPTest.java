package com.predic8.membrane.core.interceptor.dlp;

import com.predic8.membrane.core.http.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DLPTest {

    private DLP dlp;
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
        riskDict.put("user.email", "High");  // Pfad-Match
        riskDict.put("active",      "Low");  // Leaf-Match
        riskDict.put("foo",         "High"); // Leaf-Match für Array-Element
        riskDict.put("bar",         "Low");

        dlp = new DLP(riskDict);
    }

    @Test
    void analyzeSimpleJson() {
        RiskReport report = dlp.analyze(mockMessage);

        assertEquals(1, report.getRiskCounts().getOrDefault("high", 0));
        assertEquals(1, report.getRiskCounts().getOrDefault("low", 0));
        assertEquals(0, report.getRiskCounts().getOrDefault("medium", 0));
        assertEquals(2, report.getRiskCounts().getOrDefault("unclassified", 0));

        assertEquals("high",        report.getMatchedFields().get("user.email"));
        assertEquals("low",         report.getMatchedFields().get("active"));
        assertEquals("unclassified",report.getMatchedFields().get("user.profile.firstname"));
        assertEquals("unclassified",report.getMatchedFields().get("user.profile.lastname"));
    }

    @Test
    void analyzeArrayJson() {
        RiskReport report = dlp.analyze(mockArrayMessage);

        assertEquals(1, report.getRiskCounts().getOrDefault("high", 0));
        assertEquals(1, report.getRiskCounts().getOrDefault("low", 0));
        assertEquals(0, report.getRiskCounts().getOrDefault("medium", 0));
        assertEquals(0, report.getRiskCounts().getOrDefault("unclassified", 0));

        assertEquals("high", report.getMatchedFields().get("array.foo"));
        assertEquals("low",  report.getMatchedFields().get("array.bar"));
    }

    @Test
    void allExpectedFieldsPresent() {
        RiskReport report = dlp.analyze(mockMessage);

        assertTrue(report.getMatchedFields().containsKey("user.email"));
        assertTrue(report.getMatchedFields().containsKey("user.profile.firstname"));
        assertTrue(report.getMatchedFields().containsKey("user.profile.lastname"));
        assertTrue(report.getMatchedFields().containsKey("active"));
    }
}
