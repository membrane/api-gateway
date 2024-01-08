package com.predic8.membrane.core.interceptor.apikey.extractors;

import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.Optional;

import static java.util.Optional.empty;
import static org.junit.jupiter.api.Assertions.*;

class ApiKeyQueryParamExtractorTest {

    static final String API_KEY = "123456789";
    static ApiKeyQueryParamExtractor aqe;

    @BeforeAll
    static void setup() {
        aqe = new ApiKeyQueryParamExtractor();
        aqe.setParamName("ApiKey");
    }

    @Test
    void extractKey() throws URISyntaxException {
        assertEquals(Optional.of(API_KEY), aqe.extract(new Request.Builder().get("foo/bar?ApiKey="+ API_KEY).buildExchange()));
    }

    @Test
    void noKeyToExtract() throws URISyntaxException {
        assertEquals(empty(), aqe.extract(new Request.Builder().get("foo/bar").buildExchange()));
    }

    @Test
    void extractKeyRandomCase() throws URISyntaxException {
        assertEquals(Optional.of(API_KEY), aqe.extract(new Request.Builder().get("foo/bar?apikey="+ API_KEY).buildExchange()));
    }

}