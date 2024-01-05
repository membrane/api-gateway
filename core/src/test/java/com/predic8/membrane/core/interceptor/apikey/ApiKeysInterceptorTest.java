package com.predic8.membrane.core.interceptor.apikey;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.apikey.extractors.ApiKeyHeaderExtractor;
import com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyFileStore;
import com.predic8.membrane.core.interceptor.apikey.stores.UnauthorizedKeyException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.interceptor.apikey.ApiKeysInterceptor.SCOPES;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.List.of;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

class ApiKeysInterceptorTest {

    static final String keyHeader = "X-api-key";
    static final String apiKey = "73D29";
    static ApiKeysInterceptor akiWithProp;
    static ApiKeysInterceptor akiWithoutProp;
    static ApiKeyFileStore store;
    static ApiKeyFileStore store2;
    static ApiKeyHeaderExtractor ahe;
    Exchange exc;

    @BeforeAll
    static void setup() {
        store = new ApiKeyFileStore();
        store2 = new ApiKeyFileStore();
        ahe = new ApiKeyHeaderExtractor();
        akiWithProp = new ApiKeysInterceptor();
        akiWithoutProp = new ApiKeysInterceptor();
        akiWithProp.setExtractors(of(ahe));
        akiWithProp.setRequire(true);
        ahe.setHeaderName(keyHeader);
        store.setLocation(requireNonNull(ApiKeysInterceptorTest.class.getClassLoader().getResource("apikeys/keys.txt")).getPath());
        store2.setLocation(requireNonNull(ApiKeysInterceptorTest.class.getClassLoader().getResource("apikeys/keys2.txt")).getPath());

        //noinspection DataFlowIssue
        store.onApplicationEvent(null);
        //noinspection DataFlowIssue
        store2.onApplicationEvent(null);

        akiWithoutProp.setExtractors(of(ahe));
        akiWithProp.setStores(of(store, store2));
        akiWithoutProp.setStores(of(store));
    }

    @BeforeEach
    void init() {
        exc = new Exchange(null);
    }

    @Test
    void handleRequestWithKeyRequiredWithApiKey() {
        exc = new Request.Builder().header(keyHeader, apiKey).buildExchange();
        assertEquals(CONTINUE, akiWithProp.handleRequest(exc));
        assertEquals(of("accounting", "management"), exc.getProperty(SCOPES));
    }

    @Test
    void handleRequestWithKeyRequiredWithInvalidApiKey() {
        exc = new Request.Builder().header(keyHeader, "foo").buildExchange();
        assertEquals(RETURN, akiWithProp.handleRequest(exc));
        assertNull(exc.getProperty(SCOPES));
        assertEquals(403, exc.getResponse().getStatusCode());
    }

    @Test
    void handleRequestWithKeyRequiredWithoutApiKey() {
        exc = new Request.Builder().buildExchange();
        assertEquals(RETURN, akiWithProp.handleRequest(exc));
        assertNull(exc.getProperty(SCOPES));
    }

    @Test
    void handleRequestWithoutKeyRequiredWithApiKey() {
        exc = new Request.Builder().header(keyHeader, apiKey).buildExchange();
        assertEquals(CONTINUE, akiWithoutProp.handleRequest(exc));
        assertEquals(of("accounting", "management"), exc.getProperty(SCOPES));
    }

    @Test
    void handleRequestWithoutKeyRequiredWithInvalidApiKey() {
        exc = new Request.Builder().header(keyHeader, "foo").buildExchange();
        assertEquals(CONTINUE, akiWithoutProp.handleRequest(exc));
        assertNull(exc.getProperty(SCOPES));
    }

    @Test
    void handleRequestWithoutKeyRequiredWithoutApiKey() {
        exc = new Request.Builder().buildExchange();
        assertEquals(CONTINUE, akiWithoutProp.handleRequest(exc));
        assertNull(exc.getProperty(SCOPES));
    }


    @Test
    void getScopes() throws UnauthorizedKeyException {
        assertEquals(asList("finance", "internal", "account"), akiWithProp.getScopes("5XF27"));
        assertEquals(emptyList(), akiWithProp.getScopes("L62NA"));
        assertThrows(UnauthorizedKeyException.class, () -> akiWithoutProp.getScopes("751B2"));
    }
}