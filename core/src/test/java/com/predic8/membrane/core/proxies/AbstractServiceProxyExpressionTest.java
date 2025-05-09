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
import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.lang.SetHeaderInterceptor;
import com.predic8.membrane.core.interceptor.log.LogInterceptor;
import com.predic8.membrane.core.interceptor.templating.StaticInterceptor;
import com.predic8.membrane.core.interceptor.templating.TemplateInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxyKey;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.predic8.membrane.core.interceptor.flow.invocation.FlowTestInterceptors.A;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalToIgnoringCase;

class AbstractServiceProxyExpressionTest extends AbstractTestWithRouter {

    @Test
    void targetWithExpression() throws IOException {
        router.add(getBackend());
        router.add(getTargetExpressionAPI());
        router.start();

        given()
            .get("http://localhost:2000/123/xyz")
        .then()
            .statusCode(200);
    }

    private static @NotNull APIProxy getBackend() {
        APIProxy p = new APIProxy();
        p.key = new APIProxyKey(2010);
        p.getInterceptors().add(new ReturnInterceptor());
        return p;
    }

    private static @NotNull AbstractServiceProxy getTargetExpressionAPI() {
        AbstractServiceProxy proxy = new AbstractServiceProxy() {};
        proxy.setKey(new ServiceProxyKey(2000));
        proxy.getInterceptors().add(new LogInterceptor());
        AbstractServiceProxy.Target target = new AbstractServiceProxy.Target() {};
        target.setUrl("http://localhost:${2000 + 10}");
        proxy.setTarget(target);
        return proxy;
    }
}