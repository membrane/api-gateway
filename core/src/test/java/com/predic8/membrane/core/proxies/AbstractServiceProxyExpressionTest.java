/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.proxies;

import com.predic8.membrane.AbstractTestWithRouter;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.DispatchingInterceptor;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.log.LogInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxyKey;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;

import static com.predic8.membrane.core.interceptor.flow.invocation.FlowTestInterceptors.A;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractServiceProxyExpressionTest extends AbstractTestWithRouter {

    @Test
    void targetWithExpression() throws IOException, URISyntaxException {
        HttpRouter r = new HttpRouter();
        Exchange rq = new Request.Builder().get("http://localhost:2000/").buildExchange();
        APIProxy api = new APIProxy() {{
            setTarget(new Target() {{
                setUrl("http://localhost:${2000 + 1000}");
            }});
        }};
        rq.setProxy(api);
        api.init(r);

        DispatchingInterceptor di = new DispatchingInterceptor() {{
            router = r;
        }};
        di.handleRequest(rq);

        assertEquals("http://localhost:3000/", rq.getDestinations().getFirst());
    }
}