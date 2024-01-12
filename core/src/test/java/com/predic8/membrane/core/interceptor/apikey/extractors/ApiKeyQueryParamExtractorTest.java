package com.predic8.membrane.core.interceptor.apikey.extractors;

import com.predic8.membrane.core.exchange.*;
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
    static ApiKeyQueryParamExtractor aqeDefault;

    @BeforeAll
    static void setup() {
        aqe = new ApiKeyQueryParamExtractor();
        aqe.setParamName("ApiKey");
        aqeDefault = new ApiKeyQueryParamExtractor();
    }

    @Test
    void defaultParameterName() throws URISyntaxException {
        assertEquals(Optional.of(API_KEY), aqeDefault.extract(getExchange("foo/bar?api-Key=" + API_KEY)));
    }

    @Test
    void extractKey() throws URISyntaxException {
        assertEquals(Optional.of(API_KEY), aqe.extract(getExchange("foo/bar?ApiKey=" + API_KEY)));
    }

    @Test
    void noKeyToExtract() throws URISyntaxException {
        assertEquals(empty(), aqe.extract(getExchange("foo/bar")));
    }

    @Test
    void extractKeyRandomCase() throws URISyntaxException {
        assertEquals(Optional.of(API_KEY), aqe.extract(getExchange("foo/bar?apikey=" + API_KEY)));
    }

    private static Exchange getExchange(String API_KEY) throws URISyntaxException {
        return new Request.Builder().get(API_KEY).buildExchange();
    }

}