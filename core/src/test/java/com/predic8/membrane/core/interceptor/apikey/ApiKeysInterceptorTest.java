package com.predic8.membrane.core.interceptor.apikey;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.apikey.extractors.ApiKeyHeaderExtractor;
import com.predic8.membrane.core.interceptor.apikey.extractors.ApiKeyQueryParamExtractor;
import com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyFileStore;
import com.predic8.membrane.core.interceptor.apikey.stores.UnauthorizedApiKeyException;
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


public class ApiKeysInterceptorTest {
    static final String keyHeader = "X-Api-Key";
    static final String apiKey = "73D29";
    static ApiKeyFileStore store;
    static ApiKeyFileStore mergeStore;
    static ApiKeyHeaderExtractor ahe;
    static ApiKeyQueryParamExtractor aqe;
    static ApiKeysInterceptor akiWithProp;
    static ApiKeysInterceptor akiWithoutProp;
    static ApiKeysInterceptor akiWithTwoStores;
    Exchange exc;

    @BeforeAll
    static void setup() {
        store = new ApiKeyFileStore();
        store.setLocation(requireNonNull(ApiKeysInterceptorTest.class.getClassLoader().getResource("apikeys/keys.txt")).getPath());
        //noinspection DataFlowIssue
        store.onApplicationEvent(null);

        mergeStore = new ApiKeyFileStore();
        mergeStore.setLocation(requireNonNull(ApiKeysInterceptorTest.class.getClassLoader().getResource("apikeys/merge-keys.txt")).getPath());
        //noinspection DataFlowIssue
        mergeStore.onApplicationEvent(null);

        ahe = new ApiKeyHeaderExtractor();
        aqe = new ApiKeyQueryParamExtractor();

        akiWithProp = new ApiKeysInterceptor();
        akiWithProp.setExtractors(of(ahe));
        akiWithProp.setRequire(true);
        akiWithProp.setStores(of(store));

        akiWithoutProp = new ApiKeysInterceptor();
        akiWithoutProp.setExtractors(of(ahe));
        akiWithoutProp.setStores(of(store));

        akiWithTwoStores = new ApiKeysInterceptor();
        akiWithTwoStores.setStores(of(store, mergeStore));
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
    void handleRequestWithTwoStores() throws UnauthorizedApiKeyException {
        assertEquals(asList("finance", "internal", "account"), akiWithTwoStores.getScopes("5XF27"));
        }

    @Test
    void handleRequestWithKeyWithoutScopes() throws UnauthorizedApiKeyException {
        assertEquals(emptyList(), akiWithProp.getScopes("L63NC"));
    }

    @Test
    void handleUnauthorizedKey() {
        assertThrows(UnauthorizedApiKeyException.class, () -> akiWithoutProp.getScopes("751B2"));

    }

    @Test
    void handleDuplicateApiKeys() {
        var dupeStore = new ApiKeyFileStore();
        dupeStore.setLocation(requireNonNull(ApiKeysInterceptorTest.class.getClassLoader().getResource("apikeys/duplicate-api-keys.txt")).getPath());
        //noinspection DataFlowIssue
        assertThrows(RuntimeException.class, () -> dupeStore.onApplicationEvent(null));
    }
}