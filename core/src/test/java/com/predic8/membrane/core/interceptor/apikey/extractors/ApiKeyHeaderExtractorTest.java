package com.predic8.membrane.core.interceptor.apikey.extractors;

import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static java.util.Optional.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ApiKeyHeaderExtractorTest {

    static final String API_KEY = "123456789";
    static ApiKeyHeaderExtractor ahe;

    @BeforeAll
    static void setup() {
        ahe = new ApiKeyHeaderExtractor();
        ahe.setHeaderName("X-api-key");
    }

    @Test
    void noHeaderToExtract() {
        assertEquals(empty(), ahe.extract(new Request.Builder().header("api-key", API_KEY).buildExchange()));
    }

    @Test
    void extractHeader() {
        assertEquals(Optional.of(API_KEY), ahe.extract(new Request.Builder().header("X-api-key", API_KEY).buildExchange()));
    }

    @Test
    void extractHeaderRandomCase() {
        assertEquals(Optional.of(API_KEY), ahe.extract(new Request.Builder().header("x-APi-kEY", API_KEY).buildExchange()));
    }
}