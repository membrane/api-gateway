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
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static org.junit.jupiter.api.Assertions.*;

public class ConditionalInterceptorXPathTest extends ConditionalEvaluationTestContext {

    @Test
    void simpleRequestTrue() throws Exception {
        assertTrue(eval("true()", getRequest()));
    }

    @Test
    void simpleResponseTrue() throws Exception {
        assertTrue(eval("true()", getResponse()));
    }

    @Test
    void attribute() throws Exception {
        assertTrue(eval("/person/@id = 314", getRequest()));
    }

    @Test
    void invalid() {
        assertThrows(RuntimeException.class, () -> eval("foobar][", getRequest()));
        assertThrows(RuntimeException.class, () -> eval("foobar][", getResponse()));
    }

    private static Builder getRequest() {
        return new Builder().body("""
            <person id="314"/>""");
    }

    private static ResponseBuilder getResponse() {
        return new ResponseBuilder().body("<foo/>");
    }

    private static boolean eval(String condition, Object builder) throws Exception {
        return performEval(condition, builder, XPATH);
    }
}

