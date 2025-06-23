package com.predic8.membrane.core;

import com.predic8.membrane.core.http.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DLPTest {

    private Message mockMessage;
    private Map<String, String> riskDict;
    String json = """
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

    @BeforeEach
    void setUp() {
        mockMessage = mock(Message.class);
        when(mockMessage.getBodyAsStreamDecoded())
                .thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        when(mockMessage.getCharset())
                .thenReturn(String.valueOf(StandardCharsets.UTF_8));


        riskDict = new HashMap<>();
        riskDict.put("user.email", "High");
        riskDict.put("user.profile", "High");
        riskDict.put("active", "Low");
        riskDict.put("nothing", "Low");
    }

    @Test
    @SuppressWarnings("unchecked")
    void analyze() {
        DLP dlp = new DLP(riskDict);
        Map<String, Object> result = dlp.analyze(mockMessage);

        assertEquals(2, result.get("high_risk"));
        assertEquals(0, result.get("medium_risk"));
        assertEquals(1, result.get("low_risk"));
        assertEquals(3, result.get("unclassified"));

        Map<String, String> matchedFields = (Map<String, String>) result.get("matched_fields");
        assertEquals("High", matchedFields.get("user.email"));
        assertEquals("High", matchedFields.get("user.profile"));
        assertEquals("Low", matchedFields.get("active"));
    }
}