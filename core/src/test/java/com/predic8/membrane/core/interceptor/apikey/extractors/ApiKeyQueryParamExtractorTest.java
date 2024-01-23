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