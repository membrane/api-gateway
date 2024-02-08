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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.apikey.extractors.*;
import com.predic8.membrane.core.interceptor.apikey.stores.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.apikey.ApiKeysInterceptor.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.List.*;
import static java.util.Objects.*;
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

    @BeforeAll
    static void setup() {
        store = new ApiKeyFileStore();
        store.setLocation(getKeyfilePath("apikeys/keys.txt"));
        //noinspection DataFlowIssue
        store.onApplicationEvent(null); // Call init()

        mergeStore = new ApiKeyFileStore();
        mergeStore.setLocation(getKeyfilePath("apikeys/merge-keys.txt"));
        //noinspection DataFlowIssue
        mergeStore.onApplicationEvent(null); // Call init()

        ahe = new ApiKeyHeaderExtractor();
        aqe = new ApiKeyQueryParamExtractor();

        akiWithProp = new ApiKeysInterceptor();
        akiWithProp.setExtractors(of(ahe));
        akiWithProp.setStores(of(store));

        akiWithoutProp = new ApiKeysInterceptor();
        akiWithoutProp.setExtractors(of(ahe));
        akiWithoutProp.setRequired(false);
        akiWithoutProp.setStores(of(store));

        akiWithTwoStores = new ApiKeysInterceptor();
        akiWithTwoStores.setStores(of(store, mergeStore));
    }

    @Test
    void handleRequestWithKeyRequiredWithValidApiKey() {
        Exchange exc = new Request.Builder().header(keyHeader, apiKey).buildExchange();
        assertEquals(CONTINUE, akiWithProp.handleRequest(exc));
        assertEquals(of("accounting", "management"), exc.getProperty(SCOPES));
    }

    @Test
    void handleRequestWithKeyRequiredWithInvalidApiKey() {
        Exchange exc = new Request.Builder().header(keyHeader, "foo").buildExchange();
        assertEquals(RETURN, akiWithProp.handleRequest(exc));
        assertNull(exc.getProperty(SCOPES));
        assertEquals(403, exc.getResponse().getStatusCode());
        assertEquals(APPLICATION_PROBLEM_JSON, exc.getResponse().getHeader().getContentType());
    }

    @Test
    void handleRequestWithKeyRequiredWithoutApiKey() {
        Exchange exc = new Request.Builder().buildExchange();
        assertEquals(RETURN, akiWithProp.handleRequest(exc));
        assertNull(exc.getProperty(SCOPES));
        assertEquals(401, exc.getResponse().getStatusCode());
        assertEquals(APPLICATION_PROBLEM_JSON, exc.getResponse().getHeader().getContentType());
    }

    @Test
    void handleRequestWithoutKeyRequiredWithApiKey() {
        Exchange exc = new Request.Builder().header(keyHeader, apiKey).buildExchange();
        assertEquals(CONTINUE, akiWithoutProp.handleRequest(exc));
        assertEquals(of("accounting", "management"), exc.getProperty(SCOPES));
    }

    @Test
    void handleRequestWithoutKeyRequiredWithInvalidApiKey() {
        Exchange exc = new Request.Builder().header(keyHeader, "foo").buildExchange();
        assertEquals(CONTINUE, akiWithoutProp.handleRequest(exc));
        assertNull(exc.getProperty(SCOPES));
    }

    @Test
    void handleRequestWithoutKeyRequiredWithoutApiKey() {
        Exchange exc = new Request.Builder().buildExchange();
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
        dupeStore.setLocation(getKeyfilePath("apikeys/duplicate-api-keys.txt"));
        //noinspection DataFlowIssue
        assertThrows(RuntimeException.class, () -> dupeStore.onApplicationEvent(null));
    }

    private static String getKeyfilePath(String name) {
        return requireNonNull(ApiKeysInterceptorTest.class.getClassLoader().getResource(name)).getPath();
    }
}