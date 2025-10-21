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
package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.flow.CallInterceptor.copyHeadersFromResponseToRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CallInterceptorTest {

    static Exchange exc;

    @BeforeAll
    static void beforeAll() throws URISyntaxException {
        exc = Request.get("/foo").buildExchange();
    }

    @Test
    void filterHeaders() {
        exc.setResponse(Response.ok()
                .header(TRANSFER_ENCODING, "foo")
                .header(CONTENT_ENCODING, "bar")
                .header(SERVER, "dummy")
                .header("X-FOO", "42").build());

        copyHeadersFromResponseToRequest(exc, exc);

        // preserve
        assertEquals("42",exc.getRequest().getHeader().getFirstValue("X-FOO"));
        assertEquals("foo",exc.getRequest().getHeader().getFirstValue(TRANSFER_ENCODING));
        assertEquals(  "bar",exc.getRequest().getHeader().getFirstValue(CONTENT_ENCODING));

        // take out
        assertNull(exc.getRequest().getHeader().getFirstValue(SERVER));
    }

}