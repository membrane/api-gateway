/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.idempotency;

import com.predic8.membrane.core.exchange.Exchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Request.put;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.JSONPATH;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IdempotencyInterceptorTest {

    private IdempotencyInterceptor i;

    @BeforeEach
    void setup() {
        i = new IdempotencyInterceptor();
        i.setLanguage(JSONPATH);
        i.setKey("$.id");
        i.init();
    }

    @Test
    void newUniqueIdTest() throws URISyntaxException {
        assertEquals(CONTINUE, i.handleRequest(put("").body("{\"id\": \"abc123\"}").contentType(APPLICATION_JSON).buildExchange()));
    }

    @Test
    void duplicateIdTest() throws URISyntaxException {
        Exchange firstExchange = put("").body("{\"id\": \"abc456\"}").contentType(APPLICATION_JSON).buildExchange();
        Exchange secondExchange = put("").body("{\"id\": \"abc456\"}").contentType(APPLICATION_JSON).buildExchange();
        assertEquals(CONTINUE, i.handleRequest(firstExchange));
        assertEquals(ABORT, i.handleRequest(secondExchange));
        assertEquals(400, secondExchange.getResponse().getStatusCode());
    }

    @Test
    void uniqueIdsTest() throws URISyntaxException {
        assertEquals(CONTINUE, i.handleRequest(put("").body("{\"id\": \"789\"}").contentType(APPLICATION_JSON).buildExchange()));
        assertEquals(CONTINUE, i.handleRequest(put("").body("{\"id\": \"987\"}").contentType(APPLICATION_JSON).buildExchange()));
    }

    @Test
    void whenIdIsMissingTest() throws URISyntaxException {
        assertEquals(CONTINUE, i.handleRequest(put("").body("{\"msg\": \"Hello World!\"}").contentType(APPLICATION_JSON).buildExchange()));
    }

    @Test
    void defaultLanguageTest() {
        assertEquals(SPEL, new IdempotencyInterceptor().getLanguage());
    }
}
