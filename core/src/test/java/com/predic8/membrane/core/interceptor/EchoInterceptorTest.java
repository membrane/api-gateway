/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.TEXT_PLAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EchoInterceptorTest {

    @Test
    void testEcho() throws Exception {
        Exchange exc = Request
                .post("/foo")
                .header("X-Foo","bar")
                .header("Content-Type", TEXT_PLAIN)
                .body("Content").buildExchange();

        EchoInterceptor ei = new EchoInterceptor();
        Outcome outcome = ei.handleRequest(exc);

        assertEquals(Outcome.RETURN, outcome);
        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals("bar", exc.getResponse().getHeader().getFirstValue("X-Foo"));
        assertEquals(TEXT_PLAIN, exc.getResponse().getHeader().getContentType());
        assertEquals("Content", exc.getResponse().getBodyAsStringDecoded());
    }

}