package com.predic8.membrane.core.interceptor.apikey.extractors;

import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiKeyHeaderExtractorTest {

    static final String keyHeader = "X-api-key";
    static final String keyHeaderRandomCase = "x-APi-kEY";
    static final String apiKey = "123456789";
    static ApiKeyHeaderExtractor ahe;

    @BeforeAll
    static void setup() {
        ahe = new ApiKeyHeaderExtractor();
        ahe.setHeaderName(keyHeader);
    }

    @Test
    void noHeaderToExtract() {
        assertEquals(Optional.empty(), ahe.extract(new Request.Builder().header("api-key", apiKey).buildExchange()));
    }

    @Test
    void extractHeader() {
        assertEquals(Optional.of(apiKey), ahe.extract(new Request.Builder().header(keyHeader, apiKey).buildExchange()));
    }

    @Test
    void extractHeaderRandomCase() {
        assertEquals(Optional.of(apiKey), ahe.extract(new Request.Builder().header(keyHeaderRandomCase, apiKey).buildExchange()));
    }
}