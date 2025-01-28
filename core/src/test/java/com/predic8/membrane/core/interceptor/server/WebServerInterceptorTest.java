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
package com.predic8.membrane.core.interceptor.server;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class WebServerInterceptorTest {

    private static final ObjectMapper om = new ObjectMapper();

    WebServerInterceptor ws;
    Exchange exc;
    Router r;

    @BeforeEach
    void init() {
        r = new Router();

        ws = new WebServerInterceptor(r) {{
            setDocBase(Objects.requireNonNull(this.getClass().getResource("/html/")).toString());
        }};

        exc = new Exchange(null) {{
            setOriginalRequestUri("/");
            setDestinations(new ArrayList<>() {{
                add("/");
            }});
        }};
    }

    @Test
    void noIndex() {
        ws.setGenerateIndex(false);
        ws.handleRequest(exc);
        // No index file is set, and no index page is generated, so throw not found.
//        System.out.println("exc.getResponse().getBodyAsStringDecoded() = " + exc.getResponse().getBodyAsStringDecoded());
        assertEquals(500, exc.getResponse().getStatusCode());
    }

    @Test
    void generateIndex() throws Exception {
        ws.setGenerateIndex(true);
        ws.handleRequest(exc);
        // No index file is set, but index page is being generated. Body lists the page.html resource.
        String body = exc.getResponse().getBodyAsStringDecoded();
//        System.out.println("body = " + body);

        JsonNode json = om.readTree(body);

        assertEquals("Could not resolve file",json.get("title").asText());
        assertEquals("https://membrane-api.io/problems/internal",json.get("type").asText());
    }
}