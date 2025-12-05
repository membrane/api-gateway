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
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static org.junit.jupiter.api.Assertions.*;

public class IfInterceptorGroovyTest extends ConditionalEvaluationTestContext {

    @Test
    void simpleRequestTrue() throws Exception {
        assertEquals(CONTINUE,eval("true", new Builder(),true));
    }

    @Test
    void simpleRequestFalse() throws Exception {
        assertEquals(CONTINUE,eval("false", new Builder(),false));
    }

    @Test
    void simpleResponseTrue() throws Exception {
        assertEquals(CONTINUE,eval("true", new ResponseBuilder(),true));
    }

    @Test
    void invalidGroovy() throws Exception {
        assertThrows(ConfigurationException.class, () -> eval("foobar;()", new Builder(),false));
    }

    @Test
    void hasHeader() throws Exception {
        assertEquals(CONTINUE,eval("""
            header["X-Foo-Bar"].equals("Baz")
            """, new Builder().header("X-Foo-Bar", "Baz"),true));
    }

    @Test
    void property() throws Exception {
        assertEquals(CONTINUE,eval("property['bar'] == '123'", new Builder(),true));
    }

    @Test
    void isFlowResponse() throws Exception {
        assertEquals(CONTINUE,eval("flow.isResponse()", new ResponseBuilder(),true));
    }

    @Test
    void exchangeIsAvailable() throws Exception {
        assertEquals(CONTINUE,eval("exc != null", new Builder(),true));
        assertEquals(CONTINUE,eval("exchange != null", new Builder(),true));
    }

    private static Outcome eval(String condition, Object builder, boolean shouldCallNested) throws Exception {
        return performEval(condition, builder, GROOVY,shouldCallNested);
    }
}
