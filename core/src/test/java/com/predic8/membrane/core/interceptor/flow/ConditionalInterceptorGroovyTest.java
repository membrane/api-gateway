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
import org.codehaus.groovy.control.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor.LanguageType.GROOVY;
import static org.junit.jupiter.api.Assertions.*;

public class ConditionalInterceptorGroovyTest extends ConditionalEvaluationTestContext {

    @Test
    void simpleRequestTrue() throws Exception {
        assertTrue(eval("true", new Builder()));
    }

    @Test
    void simpleRequestFalse() throws Exception {
        assertFalse(eval("false", new Builder()));
    }

    @Test
    void simpleResponseTrue() throws Exception {
        assertTrue(eval("true", new ResponseBuilder()));
    }

    @Test
    void invalidGroovy() {
        assertThrows(MultipleCompilationErrorsException.class, () -> eval("foobar;()", new Builder()));
        assertThrows(MultipleCompilationErrorsException.class, () -> eval("foobar;()", new ResponseBuilder()));
    }

    @Test
    void hasHeader() throws Exception {
        assertTrue(eval("header.getFirstValue(\"X-Foo-Bar\").equals(\"Baz\")", new Builder().header("X-Foo-Bar", "Baz")));
    }

    @Test
    void isFlowResponse() throws Exception {
        assertTrue(eval("flow.isResponse()", new ResponseBuilder()));
    }

    private static boolean eval(String condition, Object builder) throws Exception {
        return performEval(condition, builder, GROOVY);
    }
}
