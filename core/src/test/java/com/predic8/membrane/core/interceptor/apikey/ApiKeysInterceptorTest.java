package com.predic8.membrane.core.interceptor.apikey;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.apikey.apikeystore.ApiKeyFileStore;
import com.predic8.membrane.core.interceptor.apikey.extractors.ApiKeyHeaderExtractor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.interceptor.apikey.ApiKeysInterceptor.SCOPES;
import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiKeysInterceptorTest {

    static final String keyHeader = "X-api-key";
    static final String apiKey = "73D29";
    static ApiKeysInterceptor akiWithProp;
    static ApiKeysInterceptor akiWithoutProp;
    static ApiKeyFileStore store;
    static ApiKeyHeaderExtractor ahe;
    Exchange exc;

    @BeforeAll
    static void setup() {
        store = new ApiKeyFileStore();
        ahe = new ApiKeyHeaderExtractor();
        akiWithProp = new ApiKeysInterceptor();
        akiWithoutProp = new ApiKeysInterceptor();
        ahe.setHeaderName(keyHeader);
        store.setLocation(Objects.requireNonNull(ApiKeysInterceptorTest.class.getClassLoader().getResource("apikeys/keys.txt")).getPath());
        akiWithProp.setExtractors(ahe);
        akiWithProp.setRequire(true);
        akiWithProp.setStores(of(store));
        akiWithoutProp.setExtractors(ahe);
        akiWithoutProp.setStores(of(store));
        akiWithProp.init();
        akiWithoutProp.init();
    }

    @BeforeEach
    void init() {
        exc = new Exchange(null);
    }

    @Test
    void handleRequestWithKeyRequiredWithApiKey() throws Exception {
        exc = new Request.Builder().header(keyHeader, apiKey).buildExchange();
        assertEquals(CONTINUE, akiWithProp.handleRequest(exc));
        assertEquals(of("accounting", "management"), exc.getProperty(SCOPES));
    }

    @Test
    void handleRequestWithKeyRequiredWithoutApiKey() throws Exception {
        exc = new Request.Builder().buildExchange();
        assertEquals(RETURN, akiWithProp.handleRequest(exc));
        assertNull(exc.getProperty(SCOPES));
    }

    @Test
    void handleRequestWithoutKeyRequiredWithApiKey() throws Exception {
        exc = new Request.Builder().header(keyHeader, apiKey).buildExchange();
        assertEquals(CONTINUE, akiWithoutProp.handleRequest(exc));
        assertEquals(of("accounting", "management"), exc.getProperty(SCOPES));
    }

    @Test
    void handleRequestWithoutKeyRequiredWithoutApiKey() throws Exception {
        exc = new Request.Builder().buildExchange();
        assertEquals(CONTINUE, akiWithoutProp.handleRequest(exc));
        assertNull(exc.getProperty(SCOPES));
    }
}