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
package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.http.Request.*;
import com.predic8.membrane.core.http.Response.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class IfInterceptorSpELTest extends ConditionalEvaluationTestContext {

    static Router router;

    @BeforeAll
    static void setup() {
        router = new Router();
    }

    @AfterAll
    static void teardown() {
        router.shutdown();
    }

    @Test
    void requestTrue() throws Exception {
        assertEquals(CONTINUE,eval("true", new Builder()));
    }

    @Test
    void simpleResponseTrue() throws Exception {
        assertEquals(CONTINUE,eval("true", new ResponseBuilder()));
    }

    @Test
    void invalidSpEL() {
        assertThrows(ConfigurationException.class, () -> eval("foobar][", new Builder()));
        assertThrows(ConfigurationException.class, () -> eval("foobar][", new ResponseBuilder()));
    }

    @Test
    void hasHeader() throws Exception {
        assertEquals(CONTINUE,eval("headers['X-Foo-Bar'] == 'Baz'", new Builder().header("X-Foo-Bar", "Baz")));
    }

    @Test
    void headerNotNull() throws Exception {
        assertEquals(CONTINUE,eval("headers['X-Foo-Bar'] != null", new Builder().header("X-Foo-Bar", "Baz")));
    }

    @Test
    void headerNullTrue() throws Exception {
        assertEquals(CONTINUE,performEval("headers['X-Does-Not-Exist'] == null", new Builder().header("X-Foo-Bar", "Baz"),SPEL, true));
    }

    @Test
    void headerNullFalse() throws Exception {
        assertEquals(CONTINUE,performEval("headers['X-Foo-Bar'] == null", new Builder().header("X-Foo-Bar", "Baz"),SPEL,false));
    }

    @Test
    void property() throws Exception {
        assertEquals(CONTINUE,eval("properties['bar'] == '123'", new Builder()));
    }

    @Test
    void isResponse() throws Exception {
        assertEquals(CONTINUE,eval("response != null", new ResponseBuilder()));
    }

    @Test
    void testBuiltInMethod() throws Exception {
        assertEquals(CONTINUE,eval("hasScope('test')", new Builder()));
    }

    @Test
    void testBuiltInMethodOverload() throws Exception {
        assertEquals(CONTINUE,eval("hasScope()", new Builder()));
    }

    @Test
    void testBuiltInMethodTypeHierarchy() throws Exception {
        //Inline List initializer in SpEL produces a RandomAccessList which should be assignable to List.
        assertEquals(CONTINUE,eval("hasScope({'test'})", new Builder()));
    }

    @Test
    void isXMLTrue() throws Exception {
        Exchange exc = Request.post("/foo").contentType(APPLICATION_XML).buildExchange();
        verify(createMock(exc), atLeastOnce()).handleRequest(exc);
    }

    @Test
    void isXMLFalse() throws Exception {
        Exchange exc = Request.post("/foo").contentType(TEXT_PLAIN).buildExchange();
        verify(createMock(exc), never()).handleRequest(exc);
    }

    private static @NotNull Interceptor createMock(Exchange exc) throws Exception {
        Interceptor mi = mock(Interceptor.class);
        when(mi.handlesRequests()).thenReturn(true);
        IfInterceptor i = new IfInterceptor();
        i.setTest("isXML()");
        i.getFlow().add(mi);
        i.init(router);
        i.handleRequest(exc);
        return mi;
    }

    private static Outcome eval(String condition, Object builder) throws Exception {
        return performEval(condition, builder, SPEL,true);
    }
}

