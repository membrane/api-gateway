/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core;

import com.predic8.membrane.core.exchangestore.LimitedMemoryExchangeStoreIntegrationTest;
import com.predic8.membrane.core.http.LargeBodyTest;
import com.predic8.membrane.core.http.MethodTest;
import com.predic8.membrane.core.interceptor.AdjustContentLengthIntegrationTest;
import com.predic8.membrane.core.interceptor.RegExReplaceInterceptorTest;
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
        AccessControlInterceptorIntegrationTest.class,
        LoadBalancingInterceptorTest.class,
        REST2SOAPInterceptorIntegrationTest.class,
        InterceptorInvocationTest.class,
        ViaProxyTest.class,
        ProxySSLConnectionMethodTest.class,
        AdjustContentLengthIntegrationTest.class,
        LimitedMemoryExchangeStoreIntegrationTest.class,
        UnavailableSoapProxyTest.class,
        LargeBodyTest.class,
        SoapAndInternalProxyTest.class
})
public class IntegrationTestsWithInternet {
}
