package com.predic8.membrane.core;

import com.predic8.membrane.core.exchangestore.LimitedMemoryExchangeStoreIntegrationTest;
import com.predic8.membrane.core.http.LargeBodyTest;
import com.predic8.membrane.core.http.MethodTest;
import com.predic8.membrane.core.interceptor.AdjustContentLengthIntegrationTest;
import com.predic8.membrane.core.interceptor.RegExReplaceInterceptorTest;
import com.predic8.membrane.core.interceptor.authentication.BasicAuthenticationInterceptorIntegrationTest;
import com.predic8.membrane.core.interceptor.rest.REST2SOAPInterceptorIntegrationTest;
import com.predic8.membrane.core.rules.SOAPProxyIntegrationTest;
import com.predic8.membrane.core.rules.UnavailableSoapProxyTest;
import com.predic8.membrane.core.transport.http.InterceptorInvocationTest;
import com.predic8.membrane.integration.*;
import com.predic8.membrane.interceptor.LoadBalancingInterceptorTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        MethodTest.class,
        RegExReplaceInterceptorTest.class,
        Http10Test.class,
        Http11Test.class,
        AccessControlInterceptorIntegrationTest.class,
        LoadBalancingInterceptorTest.class,
        REST2SOAPInterceptorIntegrationTest.class,
        InterceptorInvocationTest.class,
        BasicAuthenticationInterceptorIntegrationTest.class,
        ViaProxyTest.class,
        ProxySSLConnectionMethodTest.class,
        AdjustContentLengthIntegrationTest.class,
        LimitedMemoryExchangeStoreIntegrationTest.class,
        SOAPProxyIntegrationTest.class,
        UnavailableSoapProxyTest.class,
        LargeBodyTest.class,
        SoapAndInternalProxyTest.class
})
public class IntegrationTestsWithInternet {
}
