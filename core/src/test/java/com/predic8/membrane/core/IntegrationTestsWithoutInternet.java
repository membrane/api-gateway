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

import com.predic8.membrane.core.config.SpringReferencesTest;
import com.predic8.membrane.core.interceptor.RegExReplaceInterceptorTest;
import com.predic8.membrane.core.interceptor.rest.REST2SOAPInterceptorIntegrationTest;
import com.predic8.membrane.core.interceptor.server.WSDLPublisherInterceptorTest;
import com.predic8.membrane.core.proxies.ProxySSLTest;
import com.predic8.membrane.core.proxies.SOAPProxyIntegrationTest;
import com.predic8.membrane.core.oauth2.OAuth2RedirectTest;
import com.predic8.membrane.core.resolver.ResolverTest;
import com.predic8.membrane.core.transport.http.BoundConnectionTest;
import com.predic8.membrane.core.transport.http.IllegalCharactersInURLTest;
import com.predic8.membrane.integration.Http10Test;
import com.predic8.membrane.integration.Http11Test;
import com.predic8.membrane.integration.SessionManager;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        Http10Test.class,
        Http11Test.class,
        RegExReplaceInterceptorTest.class,
        BoundConnectionTest.class,
        REST2SOAPInterceptorIntegrationTest.class,
        WSDLPublisherInterceptorTest.class,
        SpringReferencesTest.class,
        SOAPProxyIntegrationTest.class,
        ResolverTest.class,
        IllegalCharactersInURLTest.class,
        ProxySSLTest.class,
        SessionManager.class,
        OpenApiRewriteIntegrationTest.class,
        OAuth2RedirectTest.class
})
public class IntegrationTestsWithoutInternet {
}
