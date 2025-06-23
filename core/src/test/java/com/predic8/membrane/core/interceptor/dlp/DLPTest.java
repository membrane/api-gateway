package com.predic8.membrane.core.interceptor.dlp;

import com.predic8.membrane.core.http.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DLPTest {

    private Message mockMessage;
    private DLP dlp;
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

        Map<String, String> riskDict = new HashMap<>();
        riskDict.put("user.email", "High");
        riskDict.put("user.profile", "High");
        riskDict.put("active", "Low");
        riskDict.put("nothing", "Low");

        dlp = new DLP(riskDict);
    }

    @Test
    @SuppressWarnings("unchecked")
    void analyze() {
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

    @Test
    void extractFieldNames() {
        Set<String> fieldNames = dlp.extractFieldNames(mockMessage);
        assertTrue(fieldNames.contains("user.email"));
        assertTrue(fieldNames.contains("user.profile"));
        assertTrue(fieldNames.contains("user.profile.firstname"));
        assertTrue(fieldNames.contains("user.profile.lastname"));
        assertTrue(fieldNames.contains("active"));
        assertEquals(6, fieldNames.size());
    }
}