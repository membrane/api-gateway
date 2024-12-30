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
import org.springframework.expression.spel.*;

import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static org.junit.jupiter.api.Assertions.*;

public class IfInterceptorSpELTest extends ConditionalEvaluationTestContext {

    @Test
    void simpleRequestTrue() throws Exception {
        assertTrue(eval("true", new Builder()));
    }

    @Test
    void simpleResponseTrue() throws Exception {
        assertTrue(eval("true", new ResponseBuilder()));
    }

    @Test
    void invalidSpEL() {
        assertThrows(SpelParseException.class, () -> eval("foobar][", new Builder()));
        assertThrows(SpelParseException.class, () -> eval("foobar][", new ResponseBuilder()));
    }

    @Test
    void hasHeader() throws Exception {
        assertTrue(eval("headers['X-Foo-Bar'] == 'Baz'", new Builder().header("X-Foo-Bar", "Baz")));
    }

    @Test
    void property() throws Exception {
        assertTrue(eval("properties['bar'] == '123'", new Builder()));
    }

    @Test
    void isResponse() throws Exception {
        assertTrue(eval("response != null", new ResponseBuilder()));
    }

    @Test
    void testBuiltInMethod() throws Exception {
        assertTrue(eval("hasScope('test')", new Builder()));
    }

    @Test
    void testBuiltInMethodOverload() throws Exception {
        assertTrue(eval("hasScope()", new Builder()));
    }

    @Test
    void testBuiltInMethodTypeHierarchy() throws Exception {
        //Inline List initializer in SpEL produces a RandomAccessList which should be assignable to List.
        assertTrue(eval("hasScope({'test'})", new Builder()));
    }

    private static boolean eval(String condition, Object builder) throws Exception {
        return performEval(condition, builder, SPEL);
    }
}

