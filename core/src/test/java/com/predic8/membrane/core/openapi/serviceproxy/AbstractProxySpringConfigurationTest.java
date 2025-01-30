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