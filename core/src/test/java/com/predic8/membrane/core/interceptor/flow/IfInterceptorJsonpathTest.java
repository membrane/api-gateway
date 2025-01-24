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
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static org.junit.jupiter.api.Assertions.*;

class IfInterceptorJsonpathTest extends ConditionalEvaluationTestContext {

    @Test
    void accessField() throws Exception {
        assertEquals(CONTINUE,eval("$.id", getRequest(),true));
        assertEquals( CONTINUE,eval("$.name", getRequest(),true));
    }

    @Test
    void accessFieldWithNullValue() throws Exception {
        assertEquals( CONTINUE, eval("$.email", getRequest(),false));
    }

    @Test
    void accessNotExistingField() throws Exception {
        assertEquals( CONTINUE,eval("$.unknown", getRequest(),false));
    }

    @Test
    void invalid() {
        assertThrows(ConfigurationException.class, () -> eval("$$33foobar][", getRequest(),false));
    }

    private static Builder getRequest() {
        return new Builder().body("""
            {
                "id": 314,
                "name": "Heinz",
                "email": null
            }
            """);
    }

    private static Outcome eval(String condition, Object builder, boolean shouldCallNested) throws Exception {
        return performEval(condition, builder, JSONPATH,shouldCallNested);
    }
}

