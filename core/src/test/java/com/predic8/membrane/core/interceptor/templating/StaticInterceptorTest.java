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
package com.predic8.membrane.core.interceptor.templating;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Request.*;
import static org.junit.jupiter.api.Assertions.*;

class StaticInterceptorTest {

    StaticInterceptor i;
    Exchange exc;

    @BeforeEach
    void beforeAll() throws URISyntaxException {
        i = new StaticInterceptor();
        i.setLocation("src/test/resources/json/unformatted.json");
        exc = get("/foo").buildExchange();
    }

    @Test
    void readContentFromLocationPath() throws URISyntaxException {
        i.init(new Router());
        i.handleRequest(exc);
        assertEquals(27, exc.getRequest().getBodyAsStringDecoded().length());
    }

    @Test
    void pretty() throws URISyntaxException {
        i.setPretty("true");
        i.setContentType(APPLICATION_JSON);
        i.init(new Router());

        i.handleRequest(exc);

        // Formatted with spaces and newlines it should be at least greater than 30
        assertTrue(exc.getRequest().getBodyAsStringDecoded().length() > 30);
    }

    @Nested
    class Charset {

        @Test
        void germanUmlauts() {
            i.init(new Router());
            i.handleRequest(exc);
            assertEquals("äöüß", exc.getRequest().getBodyAsStringDecoded());
        }
    }
}