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

    private static Exchange getExchange(String API_KEY) throws URISyntaxException {
        return new Request.Builder().get(API_KEY).buildExchange();
    }

}