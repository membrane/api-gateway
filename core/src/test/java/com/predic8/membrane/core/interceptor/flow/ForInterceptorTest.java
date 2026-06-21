/* Copyright 2026 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.router.DummyTestRouter;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;
import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ForInterceptorTest {

    /** Nested interceptor that counts how often it is invoked in each flow. */
    private static class CountingInterceptor extends AbstractInterceptor {
        int requestCalls;
        int responseCalls;

        @Override
        public Outcome handleRequest(Exchange exc) {
            requestCalls++;
            return CONTINUE;
        }

        @Override
        public Outcome handleResponse(Exchange exc) {
            responseCalls++;
            return CONTINUE;
        }
    }

    /** A &lt;for&gt; over the three-element SpEL list literal {@code {'a','b','c'}} with one nested interceptor. */
    private static ForInterceptor forOverThreeItems(CountingInterceptor nested) {
        var fi = new ForInterceptor();
        fi.setLanguage(SPEL);
        fi.setIn("{'a','b','c'}");
        fi.setFlow(of(nested));
        fi.init(new DummyTestRouter());
        return fi;
    }

    @Test
    void runsNestedForEachItemInRequestFlow() {
        var nested = new CountingInterceptor();
        var exc = new Exchange(null);
        exc.setRequest(new Request.Builder().build());

        assertEquals(CONTINUE, forOverThreeItems(nested).handleRequest(exc));
        assertEquals(3, nested.requestCalls);
        assertEquals(0, nested.responseCalls);
    }

    @Test
    void runsNestedForEachItemInResponseFlow() {
        var nested = new CountingInterceptor();
        var exc = new Exchange(null);
        exc.setResponse(Response.ok().build());

        assertEquals(CONTINUE, forOverThreeItems(nested).handleResponse(exc));
        assertEquals(3, nested.responseCalls);
        assertEquals(0, nested.requestCalls);
    }
}
