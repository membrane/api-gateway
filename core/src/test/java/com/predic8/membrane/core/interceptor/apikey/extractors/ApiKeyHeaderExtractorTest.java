package com.predic8.membrane.core.interceptor.apikey.extractors;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


/**
 * TODO: Test also different casing
 */
class ApiKeyHeaderExtractorTest {

    static final String keyHeader = "X-api-key";
    static final String apiKey = "123456789";
    static ApiKeyHeaderExtractor ahe;

    @BeforeAll
    static void setup() throws Exception {
        ahe = new ApiKeyHeaderExtractor();
        ahe.setHeaderName(keyHeader);
    }

    @Test
    void noHeaderToExtract() throws Exception {
        assertNull(ahe.extract(new Request.Builder().header("api-key", apiKey).buildExchange()));
    }

    @Test
    void extractHeader() throws Exception {
        assertEquals(apiKey, ahe.extract(new Request.Builder().header(keyHeader, apiKey).buildExchange()));
    }

}