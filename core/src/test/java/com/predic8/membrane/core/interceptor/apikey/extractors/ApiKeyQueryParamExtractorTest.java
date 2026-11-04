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
package com.predic8.membrane.core.interceptor.apikey.extractors;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.security.ApiKeySecurityScheme.In.*;
import static com.predic8.membrane.test.TestAppender.captureLog;
import static java.util.Optional.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("OptionalGetWithoutIsPresent")
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
        LocationNameValue lnv = aqeDefault.extract(getExchange("foo/bar?api-Key=" + API_KEY)).get();
        assertEquals(API_KEY, lnv.key());
        assertEquals("api-key", lnv.name());
        assertEquals(QUERY, lnv.location());
    }

    @Test
    void extractKey() throws URISyntaxException {
        LocationNameValue lnv = aqe.extract(getExchange("foo/bar?ApiKey=" + API_KEY)).get();
        assertEquals(API_KEY, lnv.key());
        assertEquals("ApiKey", lnv.name());
        assertEquals(QUERY, lnv.location());
    }

    @Test
    void noKeyToExtract() throws URISyntaxException {
        assertEquals(empty(), aqe.extract(getExchange("foo/bar")));
    }

    @Test
    void extractKeyRandomCase() throws URISyntaxException {
        LocationNameValue lnv = aqe.extract(getExchange("foo/bar?aPiKey=123")).get();
        assertEquals("123", lnv.key());
        assertEquals("ApiKey", lnv.name());
        assertEquals(QUERY, lnv.location());
    }

    @Test
    void extractionErrorLogDoesNotContainKey() throws Exception {
        final var log = captureLog(() -> aqeDefault.extract(getExchange("/foo/bar?api-key=%s&api-key=%s".formatted(API_KEY, API_KEY))));
        assertTrue(log.contains("Error extracting query parameters. From /foo/bar?api-key=***&api-key=***"), log.getMessages().toString());
        assertFalse(log.contains(API_KEY), log.getMessages().toString());
    }

    @Test
    void duplicateKeyIsRejected() throws URISyntaxException {
        assertEquals(empty(), aqeDefault.extract(getExchange("/foo/bar?api-key=%s&api-key=%s".formatted(API_KEY, API_KEY))));
    }

    @Test
    void extractionErrorLogDoesNotContainUserInfo() throws Exception {
        // A forward proxy keeps the absolute URI as received
        final var exc = getExchange("/foo");
        exc.getRequest().setUri("http://user:password@example.com/foo?api-key=%s&api-key=%s".formatted(API_KEY, API_KEY));
        final var log = captureLog(() -> aqeDefault.extract(exc));
        assertTrue(log.contains("From http://***@example.com/foo?api-key=***&api-key=***"), log.getMessages().toString());
        assertFalse(log.contains("password"), log.getMessages().toString());
    }

    private static Exchange getExchange(String API_KEY) throws URISyntaxException {
        return new Request.Builder().get(API_KEY).buildExchange();
    }

}