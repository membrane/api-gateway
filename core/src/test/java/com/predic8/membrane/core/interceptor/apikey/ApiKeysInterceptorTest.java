/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.apikey;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.apikey.extractors.ApiKeyExpressionExtractor;
import com.predic8.membrane.core.interceptor.apikey.extractors.ApiKeyHeaderExtractor;
import com.predic8.membrane.core.interceptor.apikey.extractors.ApiKeyQueryParamExtractor;
import com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyFileStore;
import com.predic8.membrane.core.interceptor.apikey.stores.UnauthorizedApiKeyException;
import com.predic8.membrane.core.security.SecurityScheme;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.predic8.membrane.core.exchange.Exchange.SECURITY_SCHEMES;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_PROBLEM_JSON;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.interceptor.apikey.ApiKeysInterceptor.SCOPES;
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
    static ApiKeyExpressionExtractor aee;
    static ApiKeysInterceptor akiWithProp;
    static ApiKeysInterceptor akiWithoutProp;
    static ApiKeysInterceptor akiWithTwoStores;
    static ApiKeysInterceptor akiWithExpressionExtractor;

    @BeforeAll
    static void setup() {
        store = new ApiKeyFileStore();
        store.setLocation(getKeyfilePath("apikeys/keys.txt"));
        store.init(new Router());

        mergeStore = new ApiKeyFileStore();
        mergeStore.setLocation(getKeyfilePath("apikeys/merge-keys.txt"));
        mergeStore.init(new Router());

        ahe = new ApiKeyHeaderExtractor();
        aqe = new ApiKeyQueryParamExtractor();
        aee = new ApiKeyExpressionExtractor();
        aee.setExpression("json['api-key']");
        aee.init(new Router());

        akiWithProp = new ApiKeysInterceptor();
        akiWithProp.setExtractors(of(ahe));
        akiWithProp.setStores(of(store));

        akiWithoutProp = new ApiKeysInterceptor();
        akiWithoutProp.setExtractors(of(ahe));
        akiWithoutProp.setRequired(false);
        akiWithoutProp.setStores(of(store));

        akiWithTwoStores = new ApiKeysInterceptor();
        akiWithTwoStores.setStores(of(store, mergeStore));

        akiWithExpressionExtractor = new ApiKeysInterceptor();
        akiWithExpressionExtractor.setExtractors(of(aee));
        akiWithExpressionExtractor.setStores(of(store));
    }

    @Test
    void handleRequestWithKeyInsideBody() throws URISyntaxException {
        Exchange exc = post("/").body("""
            {
                "foo": "bar",
                "api-key": "%s"
            }
        """.formatted(apiKey)).buildExchange();
        assertEquals(CONTINUE, akiWithExpressionExtractor.handleRequest(exc));
        assertEquals(Set.of("accounting", "management"), getScopes(exc));
    }

    @Test
    void handleRequestWithKeyRequiredWithValidApiKey() throws URISyntaxException {
        Exchange exc = get("/").header(keyHeader, apiKey).buildExchange();
        assertEquals(CONTINUE, akiWithProp.handleRequest(exc));
        assertEquals(Set.of("accounting", "management"), getScopes(exc));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> getScopes(Exchange exc) {
        return ((List<SecurityScheme>) exc.getProperty(SECURITY_SCHEMES)).getFirst().getScopes();
    }

    @Test
    void handleRequestWithKeyRequiredWithInvalidApiKey() throws URISyntaxException {
        Exchange exc = get("/").header(keyHeader, "foo").buildExchange();
        assertEquals(RETURN, akiWithProp.handleRequest(exc));
        assertNull(exc.getProperty(SCOPES));
        assertEquals(403, exc.getResponse().getStatusCode());
        assertEquals(APPLICATION_PROBLEM_JSON, exc.getResponse().getHeader().getContentType());
    }

    @Test
    void handleRequestWithKeyRequiredWithoutApiKey() throws URISyntaxException {
        Exchange exc = get("/").buildExchange();
        assertEquals(RETURN, akiWithProp.handleRequest(exc));
        assertNull(exc.getProperty(SCOPES));
        assertEquals(401, exc.getResponse().getStatusCode());
        assertEquals(APPLICATION_PROBLEM_JSON, exc.getResponse().getHeader().getContentType());
    }

    @Test
    void handleRequestWithoutKeyRequiredWithApiKey() throws URISyntaxException {
        Exchange exc = get("/").header(keyHeader, apiKey).buildExchange();
        assertEquals(CONTINUE, akiWithoutProp.handleRequest(exc));
        assertEquals(Set.of("accounting", "management"), getScopes(exc));
    }

    @Test
    void handleRequestWithoutKeyRequiredWithInvalidApiKey() throws URISyntaxException {
        Exchange exc = get("/").header(keyHeader, "foo").buildExchange();
        assertEquals(CONTINUE, akiWithoutProp.handleRequest(exc));
        assertNull(exc.getProperty(SCOPES));
    }

    @Test
    void handleRequestWithoutKeyRequiredWithoutApiKey() throws URISyntaxException {
        Exchange exc = get("/").buildExchange();
        assertEquals(CONTINUE, akiWithoutProp.handleRequest(exc));
        assertNull(exc.getProperty(SCOPES));
    }


    @Test
    void handleRequestWithTwoStores() throws UnauthorizedApiKeyException {
        assertEquals(Set.of("finance", "internal", "account"), akiWithTwoStores.getScopes("5XF27"));
    }

    @Test
    void handleRequestWithKeyWithoutScopes() throws UnauthorizedApiKeyException {
        assertEquals( new HashSet<>(), akiWithProp.getScopes("L63NC"));
    }

    @Test
    void handleUnauthorizedKey() {
        assertThrows(UnauthorizedApiKeyException.class, () -> akiWithoutProp.getScopes("751B2"));
    }

    @Test
    void handleDuplicateApiKeys() {
        var dupeStore = new ApiKeyFileStore();
        dupeStore.setLocation(getKeyfilePath("apikeys/duplicate-api-keys.txt"));
        assertThrows(RuntimeException.class, () -> dupeStore.init(new Router()));
    }

    private static String getKeyfilePath(String name) {
        return requireNonNull(ApiKeysInterceptorTest.class.getClassLoader().getResource(name)).getPath();
    }
}
