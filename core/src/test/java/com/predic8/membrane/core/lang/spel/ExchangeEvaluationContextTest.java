/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.lang.spel;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.*;

import static org.junit.jupiter.api.Assertions.*;

public class ExchangeEvaluationContextTest {

    Exchange exc;

    @BeforeEach
    void setup() {
        exc = new Request.Builder()
                .header("Authentication","foo")
                .buildExchange();
    }

    String keyExpression(String spel) {
        Expression expression = new SpelExpressionParser().parseExpression(spel);
        return expression.getValue(new ExchangeEvaluationContext(exc, exc.getRequest()), String.class);
    }

    @Test
    void getMethod() {
        assertEquals("foo", keyExpression("headers.authentication"));
    }

    @Test
    void getMethodIgnoreCase() {
        assertEquals("foo", keyExpression("headers.AUTHenticatioN"));
    }
}
