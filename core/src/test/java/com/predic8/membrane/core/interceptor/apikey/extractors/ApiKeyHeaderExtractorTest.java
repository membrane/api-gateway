package com.predic8.membrane.core.interceptor.apikey.extractors;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http.HttpResendHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyHeaderExtractorTest {

    static final String keyHeader = "X-api-key";
    static final String apiKey = "123456789";

    static ApiKeyHeaderExtractor ahe;
    static HttpResendHandler hrh;
    static Exchange exc;

    @Test
    void noHeaderToExtract() {
        exc = new Request.Builder().header("api-key", apiKey).buildExchange();

        assertNull(ahe.extract(exc));
    }

    @Test
    void extractHeader() {
        exc = new Request.Builder().header(keyHeader, apiKey).buildExchange();

        assertEquals(apiKey, ahe.extract(exc));
    }

    @BeforeEach
    void init() {
        exc = new Exchange(null);
    }

    @BeforeAll
    static void setup() throws Exception {
        ahe = new ApiKeyHeaderExtractor();
        ahe.setHeaderName(keyHeader);
    }
}