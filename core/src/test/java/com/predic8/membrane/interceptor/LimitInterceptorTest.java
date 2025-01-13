/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.interceptor;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.*;
import static org.junit.jupiter.api.Assertions.*;

class LimitInterceptorTest {

    public static final String SMALL = "small";
    public static final String LARGE = "large message body";
    static LimitInterceptor li;

    @BeforeAll
    public static void before() {
        li = new LimitInterceptor();
        li.setMaxBodyLength(10);
        li.setFlow(REQUEST);
    }

    @Test
    void small() {
        assertDoesNotThrow(() -> li.handleRequest(Request.post("/foo").body(SMALL).buildExchange()));
    }

    @Test
    void large() {
        assertThrows(Exception.class, () -> li.handleRequest(Request.post("/foo").body(LARGE).buildExchange()));
    }

    @Test
    void streamSmall() throws Exception {
        Exchange exc = getExchangeWithLargeStreamRequest(SMALL);
        li.handleRequest(exc);
        assertEquals(SMALL, exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void streamLarge() throws Exception {
        Exchange exc = getExchangeWithLargeStreamRequest(LARGE);
        li.handleRequest(exc);
        assertThrows(Exception.class, () -> exc.getRequest().readBody());
    }

    private static @NotNull Exchange getExchangeWithLargeStreamRequest(String body) throws Exception {
        Request request = Request.post("/foo").header("dummy", "foo").build();
        request.setBody(new Body(new ByteArrayInputStream(body.getBytes())));
        Exchange exc = new Exchange(null);
        exc.setRequest(request);
        return exc;
    }
}