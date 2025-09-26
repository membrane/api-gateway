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
import com.predic8.membrane.core.http.Request.*;
import com.predic8.membrane.core.http.Response.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;
import com.predic8.membrane.core.security.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.security.ApiKeySecurityScheme.In.*;
import static java.util.List.*;

class ConditionalEvaluationTestContext {

    static Outcome performEval(String condition, Object builder, Language lang, boolean shouldCallNested) throws Exception {
        var exc = new Exchange(null);
        var mockInt = new ConditionalEvaluationTestContext.MockInterceptor();
        var ifInt = new IfInterceptor();

        new ApiKeySecurityScheme(HEADER,"x-api-key").scopes("test", "main").add(exc);

        exc.setProperty("bar", "123");

        ifInt.setLanguage(lang);
        ifInt.setFlow(of(mockInt));
        ifInt.setTest(condition);
        ifInt.init(new HttpRouter());

        Outcome outcome;
        if (builder instanceof Builder b) {
            exc.setRequest(b.build());
            outcome = ifInt.handleRequest(exc);
        } else if (builder instanceof ResponseBuilder b) {
            exc.setResponse(b.build());
            outcome = ifInt.handleResponse(exc);
        } else {
            throw new IllegalStateException("Unexpected value: " + builder);
        }
        if (mockInt.isCalled() != shouldCallNested) {
            throw new RuntimeException("Mock");
        }
        return outcome;
    }

    private static class MockInterceptor extends AbstractInterceptor {

        boolean handleRequestCalled;

        @Override
        public Outcome handleRequest(Exchange exc) {
            handleRequestCalled = true;
            return CONTINUE;
        }

        @Override
        public Outcome handleResponse(Exchange exc) {
            handleRequestCalled = true;
            return CONTINUE;
        }

        public boolean isCalled() {
            return handleRequestCalled;
        }
    }
}
