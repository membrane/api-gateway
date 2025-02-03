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
package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.*;
import org.springframework.context.support.*;
import org.springframework.core.io.*;

abstract class AbstractProxySpringConfigurationTest {

    static String config = """
            <spring:beans xmlns:spring="http://www.springframework.org/schema/beans"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xmlns="http://membrane-soa.org/proxies/1/" xmlns:lang="http://www.springframework.org/schema/lang"
                          xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.2.xsd
            					    http://membrane-soa.org/proxies/1/ http://membrane-soa.org/schemas/proxies-1.xsd http://www.springframework.org/schema/lang http://www.springframework.org/schema/lang/spring-lang.xsd">
                <router>
            
                    %s
            
                </router>
            </spring:beans>
            """;
    protected static Router startSpringContextAndReturnRouter(String api) {
        GenericXmlApplicationContext ctx = new GenericXmlApplicationContext();
        ctx.load(new ByteArrayResource(config.formatted(api).getBytes()));
        ctx.refresh();
        return ctx.getBean("router", Router.class);
    }

    protected static APIProxy getApiProxy(Router router) {
        return router.getRuleManager().getRuleByName("bool-api",APIProxy.class);
    }
}