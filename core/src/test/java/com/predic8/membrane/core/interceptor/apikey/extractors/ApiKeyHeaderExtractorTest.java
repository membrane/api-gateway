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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static java.util.Optional.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ApiKeyHeaderExtractorTest {

    static final String API_KEY = "123456789";
    static ApiKeyHeaderExtractor ahe;

    @BeforeAll
    static void setup() {
        ahe = new ApiKeyHeaderExtractor();
        ahe.setHeaderName("X-api-key");
    }

    @Test
    void noHeaderToExtract() {
        assertEquals(empty(), ahe.extract(getExchange("api-key")));
    }

    @Test
    void extractHeader() {
        assertEquals(Optional.of(API_KEY), ahe.extract(getExchange("X-api-key")));
    }

    @Test
    void extractHeaderRandomCase() {
        assertEquals(Optional.of(API_KEY), ahe.extract(getExchange("X-aPi-KeY")));
    }

    private static Exchange getExchange(String headerName) {
        return new Request.Builder().header(headerName, API_KEY).buildExchange();
    }

}