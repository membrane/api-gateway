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

    @BeforeEach
    void setUp() {
        mockMessage = mock(Message.class);

        // up / recursive
        String json = """
                    {
                        "email": "test@example.com",
                        "password": "secret",
                        "username": "user1",
                        "test": "test"
                    }
                """;



        when(mockMessage.getBodyAsStreamDecoded())
                .thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        when(mockMessage.getCharset())
                .thenReturn(String.valueOf(StandardCharsets.UTF_8));


        riskDict = new HashMap<>();
        riskDict.put("email", "High");
        riskDict.put("password", "High");
        riskDict.put("username", "Low");
    }

    @Test
    @SuppressWarnings("unchecked")
    void analyze() {
        DLP dlp = new DLP(mockMessage, riskDict); // init with riskDict dlp.check(msg)
        Map<String, Object> result = dlp.analyze();

        assertEquals(2, result.get("high_risk"));
        assertEquals(0, result.get("medium_risk"));
        assertEquals(1, result.get("low_risk"));
        assertEquals(1, result.get("unclassified"));

        Map<String, String> matchedFields = (Map<String, String>) result.get("matched_fields");
        assertEquals("High", matchedFields.get("email"));
        assertEquals("High", matchedFields.get("password"));
        assertEquals("Low", matchedFields.get("username"));
    }
}