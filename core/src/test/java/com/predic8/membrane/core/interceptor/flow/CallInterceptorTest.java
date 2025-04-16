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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.Arrays;
import java.util.List;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.flow.CallInterceptor.copyHeadersFromResponseToRequest;
import static com.predic8.membrane.core.interceptor.flow.CallInterceptor.getFilteredRequestHeader;
import static org.bson.assertions.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.*;

class CallInterceptorTest {

    static Exchange exc;

    @BeforeAll
    static void beforeAll() throws URISyntaxException {
        exc = Request.get("/foo").buildExchange();
    }

    @Test
    void filterHeaders() {
        exc.setResponse(Response.ok()
                .header(TRANSFER_ENCODING, "dummy")
                .header(CONTENT_ENCODING, "dummy")
                .header(SERVER, "dummy")
                .header("X-FOO", "42").build());

        copyHeadersFromResponseToRequest(exc, exc);

        assertEquals("42",exc.getRequest().getHeader().getFirstValue("X-FOO"));
        assertNull(exc.getRequest().getHeader().getFirstValue(TRANSFER_ENCODING));
        assertNull(exc.getRequest().getHeader().getFirstValue(CONTENT_ENCODING));
        assertNull(exc.getRequest().getHeader().getFirstValue(SERVER));
    }

}