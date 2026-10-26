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

import com.predic8.membrane.core.http.Request.*;
import com.predic8.membrane.core.http.Response.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static org.junit.jupiter.api.Assertions.*;

public class IfInterceptorXPathTest extends ConditionalEvaluationTestContext {

    @Test
    void simpleRequestTrue() throws Exception {
        assertEquals(CONTINUE,eval("true()", getRequest(),true));
    }

    @Test
    void simpleResponseTrue() throws Exception {
        assertEquals(CONTINUE,eval("true()", getResponse(),true));
    }

    @Test
    void attribute() throws Exception {
        assertEquals( CONTINUE,eval("/person/@id = 314", getRequest(),true));
    }

    @Test
    void invalid() throws Exception {
        assertEquals(ABORT, eval("foobar][", getRequest(),false));
        assertEquals(ABORT, eval("foobar][", getResponse(),false));
    }

    @Test
    void emptyBodyIsAnEmptyDocument() {
        assertEquals(CONTINUE, eval("/a", new Builder(), false));
        assertEquals(CONTINUE, eval("not(/a)", new Builder(), true));
    }

    @Test
    void jsonBodyIsAnEmptyDocument() {
        assertEquals(CONTINUE, eval("/a", new Builder().contentType(APPLICATION_JSON).body("{}"), false));
        assertEquals(CONTINUE, eval("not(/a)", new Builder().contentType(APPLICATION_JSON).body("{}"), true));
    }

    @Test
    void textPlainXmlIsEvaluated() {
        assertEquals(CONTINUE, eval("/a", new Builder().contentType(TEXT_PLAIN).body("<a/>"), true));
    }

    @Test
    void malformedRequestBodyIs400() {
        var exc = new Exchange(null);
        assertEquals(ABORT, performEval(exc, "/a", new Builder().contentType(APPLICATION_XML).body("<a><b></a>"), XPATH, false));
        assertEquals(400, exc.getResponse().getStatusCode());
    }

    @Test
    void malformedResponseBodyIs502() {
        var exc = new Exchange(null);
        assertEquals(ABORT, performEval(exc, "/a", new ResponseBuilder().contentType(APPLICATION_XML).body("<a><b></a>"), XPATH, false));
        assertEquals(502, exc.getResponse().getStatusCode());
    }

    private static Builder getRequest() {
        return new Builder().body("""
            <person id="314"/>""");
    }

    private static ResponseBuilder getResponse() {
        return new ResponseBuilder().body("<foo/>");
    }

    private static Outcome eval(String condition, Object builder,boolean shouldCallNested) {
        return performEval(condition, builder, XPATH,shouldCallNested);
    }
}

