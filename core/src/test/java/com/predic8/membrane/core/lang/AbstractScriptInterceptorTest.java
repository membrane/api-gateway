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

package com.predic8.membrane.core.lang;

import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.router.Router;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractScriptInterceptorTest {

    @Test
    void getScriptSrcResolvesLocationViaResolver() {

        var interceptor = new TestScriptInterceptor();
        var router = new DefaultRouter();
        router.init();
        interceptor.setRouterForTest(router);
        interceptor.setLocation("classpath:/resolver/script.groovy");
        assertEquals("CONTINUE", interceptor.getScriptSrc());
    }

    private static final class TestScriptInterceptor extends AbstractScriptInterceptor {
        @Override
        protected void initInternal() {
        }

        @Override
        protected Logger getScriptLogger() {
            return LoggerFactory.getLogger(TestScriptInterceptor.class);
        }

        void setRouterForTest(Router router) {
            this.router = router;
        }
    }
}
