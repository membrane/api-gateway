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

package com.predic8.membrane.core.lang.xpath;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.lang.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetPropertyInterceptorXPathTest {

    private ObjectMapper om = new ObjectMapper();
    private Exchange exc;

    @Nested
    class WithoutNamespaces {

        @BeforeEach
        void setup() throws Exception {
            exc = post("/person").xml("""
                    <person>
                        <firstname>Trevor</firstname>
                    </person>
                    """).buildExchange();
        }

        @Test
        void normal() {
            var interceptor = getInterceptor(null,"${//firstname}");
            assertEquals(CONTINUE, interceptor.handleRequest(exc));
            assertEquals("Trevor", exc.getProperty("firstname").toString());
        }
    }

    @Nested
    class WithNamespaces {

        @BeforeEach
        void setup() throws Exception {
            exc = post("/person").xml("""
                    <p8:person xmlns:p8="https://predic8.de">
                        <p8:firstname>Trevor</p8:firstname>
                        <other:foo xmlns:other="https://predic8.de/other">baz</other:foo>
                    </p8:person>
                    """).buildExchange();
        }

        @Test
        void noNamespacesDeclaredQueryWithPrefix() {
            var interceptor = getInterceptor(null,"${//p8:firstname}");
            assertEquals(CONTINUE, interceptor.handleRequest(exc));
            assertEquals("", exc.getProperty("firstname").toString());
        }

        @Test
        void noNamespacesDeclaredQueryWithLocalName() {
            var interceptor = getInterceptor(null,"${//*[local-name() = 'firstname']}");
            assertEquals(CONTINUE, interceptor.handleRequest(exc));
            assertEquals("Trevor", exc.getProperty("firstname").toString());
        }

        @Test
        void nsUri() {
            var interceptor = getInterceptor(null,"${//*[namespace-uri() = 'https://predic8.de/other']}");
            assertEquals(CONTINUE, interceptor.handleRequest(exc));
            assertEquals("baz", exc.getProperty("firstname").toString());
        }

        @Test
        void nsPrefixed() {
            var interceptor = getInterceptor(getNamespaces(),"${//p8:firstname}");
            assertEquals(CONTINUE, interceptor.handleRequest(exc));
            assertEquals("Trevor", exc.getProperty("firstname").toString());
        }

        @Test
        void unknownPrefix() throws Exception {
            var interceptor = getInterceptor(getNamespaces(),"${//unknown:firstname}");
            assertEquals(ABORT, interceptor.handleRequest(exc));
            assertEquals(500, exc.getResponse().getStatusCode());
            String body = exc.getResponse().getBodyAsStringDecoded();
            assertTrue(body.contains("${//unknown:firstname}"));
            assertTrue(body.contains("unknown"));
        }
    }

    private static @NotNull SetPropertyInterceptor getInterceptor(Namespaces namespaces, String value) {
        var i = new SetPropertyInterceptor();
        i.setNamespaces(namespaces);
        i.setLanguage(XPATH);
        i.setFieldName("firstname");
        i.setValue(value);
        i.init(new Router());
        return i;
    }

    private static @NotNull Namespaces getNamespaces() {
        var p8 = new Namespaces.Namespace();
        p8.prefix = "p8";
        p8.uri = "https://predic8.de";
        var ns = new Namespaces();
        ns.setNamespace(List.of(p8));
        return ns;
    }
}
