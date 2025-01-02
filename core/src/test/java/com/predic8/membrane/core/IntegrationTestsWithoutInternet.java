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

import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.rest.*;
import com.predic8.membrane.core.interceptor.server.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.integration.*;
import com.predic8.membrane.integration.withoutinternet.*;
import org.junit.platform.suite.api.*;

@Suite
@SelectClasses({
        Http10Test.class,
        Http11Test.class,
        RegExReplaceInterceptorTest.class,
        BoundConnectionTest.class,
        ExceptionHandlingTest.class,
        REST2SOAPInterceptorIntegrationTest.class,
        WSDLPublisherTest.class,
        SpringReferencesTest.class,
        SOAPProxyIntegrationTest.class,
        ResolverTest.class,
        IllegalCharactersInURLTest.class,
        ProxySSLTest.class,
        SessionManager.class,
        OpenApiRewriteIntegrationTest.class
})
public class IntegrationTestsWithoutInternet {
}
