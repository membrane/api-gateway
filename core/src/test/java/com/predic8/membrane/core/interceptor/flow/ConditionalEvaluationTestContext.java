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
import com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor.*;
import com.predic8.membrane.core.security.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.security.ApiKeySecurityScheme.In.*;
import static java.util.List.*;

class ConditionalEvaluationTestContext {

    static boolean performEval(String condition, Object builder, LanguageType lang) throws Exception {
        var exc = new Exchange(null);
        var mockInt = new ConditionalEvaluationTestContext.MockInterceptor();
        var condInt = new ConditionalInterceptor();

        new ApiKeySecurityScheme(HEADER,"x-api-key").scopes("test", "main").add(exc);

        condInt.setLanguage(lang);
        condInt.setInterceptors(of(mockInt));
        condInt.setTest(condition);
        condInt.init(new HttpRouter());

        if (builder instanceof Builder b) {
            exc.setRequest(b.build());
            condInt.handleRequest(exc);
        } else if (builder instanceof ResponseBuilder b) {
            exc.setResponse(b.build());
            condInt.handleResponse(exc);
            new FlowController().invokeResponseHandlers(exc, List.of(condInt));
        } else {
            throw new IllegalStateException("Unexpected value: " + builder);
        }

        return mockInt.isCalled();
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
