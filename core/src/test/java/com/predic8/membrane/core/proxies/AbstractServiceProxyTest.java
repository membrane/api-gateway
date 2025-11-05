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

import com.predic8.membrane.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.lang.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.AbstractServiceProxy.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.interceptor.flow.invocation.FlowTestInterceptors.*;
import static io.restassured.RestAssured.*;

@SuppressWarnings("UastIncorrectHttpHeaderInspection")
class AbstractServiceProxyTest extends AbstractTestWithRouter {

    @Test
    void getToPost() throws IOException {
        router.add(getBackend());
        router.add(getAPI());
        router.start();

        given()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .header("X-Called-Method", "POST");
    }

    private static @NotNull AbstractServiceProxy getAPI() {
        AbstractServiceProxy proxy = new AbstractServiceProxy() {};
        proxy.setKey(new ServiceProxyKey(2000));
        proxy.getFlow().add(A);

        var target = new Target() {
        };
        target.setMethod("POST");
        target.setHost("localhost");
        target.setPort(2010);

        proxy.setTarget(target);
        return proxy;
    }

    private static @NotNull APIProxy getBackend() {
        APIProxy p = new APIProxy();
        p.key = new APIProxyKey(2010);
        SetHeaderInterceptor sh = new SetHeaderInterceptor();
        sh.setFieldName("X-Called-Method");
        sh.setValue("${method}");
        p.getFlow().add(sh);
        p.getFlow().add(new ReturnInterceptor());
        return p;
    }

}