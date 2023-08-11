/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.misc;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;
import org.springframework.expression.spel.support.*;

import java.net.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SetHeaderInterceptorTest {

    Exchange exc;
    SetHeaderInterceptor interceptor = new SetHeaderInterceptor();
    StandardEvaluationContext evalCtx;

    @BeforeEach
    void setUp() throws URISyntaxException {
        Router router = new Router();
        router.setBaseLocation("");
        exc = new Exchange(null);
        exc.setRequest(new Request.Builder().method("GET").url(new URIFactory(), "/boo").header("host", "localhost:8080").build());
        exc.setProperty("prop", 88);

        interceptor.setName("foo");
        evalCtx = new StandardEvaluationContext(new EvalContextValues());
    }

    @Test
    void handleRequest() throws Exception {
        interceptor.setValue("42");
        interceptor.handleRequest(exc);
        assertEquals("42", exc.getRequest().getHeader().getFirstValue("foo"));
    }

    @Test
    void withoutExpressionOnlyConstant() throws Exception {
        interceptor.setValue("42");
        interceptor.handleRequest(exc);
        assertEquals("42", exc.getRequest().getHeader().getFirstValue("foo"));
    }

    @Test
    void calculation() throws Exception {
        interceptor.setValue("-${5 * 5}-");
        interceptor.handleRequest(exc);
        assertEquals("-25-", exc.getRequest().getHeader().getFirstValue("foo"));
    }

    @Test
    void getRequestUri() throws Exception {
        interceptor.setValue("${exchange.request.uri}");
        interceptor.handleRequest(exc);
        assertEquals("/boo", exc.getRequest().getHeader().getFirstValue("foo"));
    }

    @Test
    void getHostHeader() throws Exception {
        interceptor.setValue("${headers.host}");
        interceptor.handleRequest(exc);
        assertEquals("localhost:8080", exc.getRequest().getHeader().getFirstValue("foo"));
    }

    @Test
    void getExcProperty() throws Exception {
        interceptor.setValue("${properties['prop']}");
        interceptor.handleRequest(exc);
        assertEquals("88", exc.getRequest().getHeader().getFirstValue("foo"));
    }

    @Test
    void computeValueNoExpression() {
        interceptor.setValue("foo");
        assertEquals("foo", interceptor.evaluateExpression(evalCtx));
    }

    @Test
    void computeSingeExpression() {
        assertEquals("24", interceptor.evaluateSingeExpression(evalCtx,"1*2*3*4"));
    }

    @Test
    void computeSingeExpressionWithCtx() {
        interceptor.setValue("foo ${bar} baz ${foo} coo ${2*3}/${'a'}");
        assertEquals("bar", interceptor.evaluateSingeExpression(evalCtx,"bar"));
    }

    @Test
    void computeValue() {
        interceptor.setValue("foo ${bar} baz ${foo} coo ${2*3}/${'a'}");
        assertEquals("foo bar baz foo coo 6/a", interceptor.evaluateExpression(evalCtx));
    }

    @Test
    void getJson() {
        interceptor.setValue("foo ${json[a]} baz");
        assertEquals("foo 5 baz", interceptor.evaluateExpression(evalCtx));
    }

    @Test
    void jsonWrongAttribute() {
        interceptor.setValue("foo ${json[b]} baz");
        assertEquals("foo null baz", interceptor.evaluateExpression(evalCtx));
    }

    @Test
    @DisplayName("Only set if the header is absent")
    void onlyIfAbsent() throws Exception {
        exc.getRequest().getHeader().add("X-FOO","0");

        interceptor.setName("X-FOO");
        interceptor.setValue("42");
        interceptor.setIfAbsent(true);

        interceptor.handleRequest(exc);

        assertEquals("0", exc.getRequest().getHeader().getFirstValue("X-FOO"));
    }

    @Test
    @DisplayName("Only set if the header is absent with different casing")
    void onlyIfAbsentCaseDiff() throws Exception {
        exc.getRequest().getHeader().add("X-FOO","0");

        interceptor.setName("x-fOo");
        interceptor.setValue("42");
        interceptor.setIfAbsent(true);

        interceptor.handleRequest(exc);

        assertEquals("0", exc.getRequest().getHeader().getFirstValue("x-FoO"));
    }


    @Test
    @DisplayName("Overwrite header when it is not absent")
    void notIfAbsent() throws Exception {
        exc.getRequest().getHeader().add("X-FOO", "0");

        interceptor.setName("X-FOO");
        interceptor.setValue("42");

        interceptor.handleRequest(exc);

        assertEquals("42", exc.getRequest().getHeader().getFirstValue("X-FOO"));
    }

    @Test
    @DisplayName("Overwrite header when it is not absent with different casing")
    void notIfAbsentCaseDiff() throws Exception {
        exc.getRequest().getHeader().add("X-FOO","0");

        interceptor.setName("x-fOo");
        interceptor.setValue("42");

        interceptor.handleRequest(exc);

        assertEquals("42", exc.getRequest().getHeader().getFirstValue("x-FoO"));
    }

    class EvalContextValues {
        public String getFoo() {
            return "foo";
        }

        public String getBar() {
            return "bar";
        }

        public Map<String, Object> getJson() {
            Map<String, Object> m = new HashMap<>();
            m.put("a",5);
            return m;
        }
    }
}