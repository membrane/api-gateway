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
package com.predic8.membrane.core.interceptor.opentelemetry;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.RuleManager.RuleDefinitionSource.SPRING;

class OpenTelemetryInterceptorTest {

    @Test
    void initTest() throws Exception {
        Rule r = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 3141), null, 0);
        r.getInterceptors().add(new OpenTelemetryInterceptor());

        HttpRouter rtr = new HttpRouter();
        rtr.getRuleManager().addProxy(r, SPRING);

        rtr.init();
    }
}