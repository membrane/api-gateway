package com.predic8.membrane.core;

import com.predic8.membrane.core.config.SpringReferencesTest;
import com.predic8.membrane.core.http.MethodTest;
import com.predic8.membrane.core.interceptor.LimitInterceptorTest;
import com.predic8.membrane.core.interceptor.RegExReplaceInterceptorTest;
import com.predic8.membrane.core.interceptor.server.WSDLPublisherTest;
import com.predic8.membrane.core.resolver.ResolverTest;
import com.predic8.membrane.core.rules.ProxySSLTest;
import com.predic8.membrane.core.transport.ExceptionHandlingTest;
import com.predic8.membrane.core.transport.http.BoundConnectionTest;
import com.predic8.membrane.core.transport.http.IllegalCharactersInURLTest;
import com.predic8.membrane.integration.SessionManager;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        RegExReplaceInterceptorTest.class,
        BoundConnectionTest.class,
        ExceptionHandlingTest.class,
        WSDLPublisherTest.class,
        LimitInterceptorTest.class,
        SpringReferencesTest.class,
        ResolverTest.class,
        IllegalCharactersInURLTest.class,
        ProxySSLTest.class,
        SessionManager.class,
})
public class IntegrationTestsWithoutInternet {
}
