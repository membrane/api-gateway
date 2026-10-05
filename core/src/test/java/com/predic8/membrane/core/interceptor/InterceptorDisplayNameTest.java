/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.core.interceptor.acl.AccessControlInterceptor;
import com.predic8.membrane.core.interceptor.administration.AdminRESTInterceptor;
import com.predic8.membrane.core.interceptor.administration.DynamicAdminPageInterceptor;
import com.predic8.membrane.core.interceptor.jwt.JwtSignInterceptor;
import com.predic8.membrane.core.interceptor.kubernetes.KubernetesValidationInterceptor;
import com.predic8.membrane.core.interceptor.oauth2client.FlowInitiator;
import com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor;
import com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityInterceptor;
import com.predic8.membrane.core.interceptor.stomp.STOMPClient;
import com.predic8.membrane.core.interceptor.ws_addressing.WsaEndpointRewriterInterceptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * The display name ends up in the <code>see</code> URL of ProblemDetails responses, so it must not
 * fall back to the fully qualified class name.
 */
class InterceptorDisplayNameTest {

    static Stream<Arguments> interceptors() {
        return Stream.of(
                arguments((Supplier<Interceptor>) AccessControlInterceptor::new, "access control"),
                arguments((Supplier<Interceptor>) AdminRESTInterceptor::new, "admin rest"),
                arguments((Supplier<Interceptor>) DynamicAdminPageInterceptor::new, "dynamic admin page"),
                arguments((Supplier<Interceptor>) EchoInterceptor::new, "echo"),
                arguments((Supplier<Interceptor>) FlowInitiator::new, "flow initiator"),
                arguments((Supplier<Interceptor>) JwtSignInterceptor::new, "jwt sign"),
                arguments((Supplier<Interceptor>) KubernetesValidationInterceptor::new, "kubernetes validation"),
                arguments((Supplier<Interceptor>) OAuth2Resource2Interceptor::new, "oauth2 resource"),
                arguments((Supplier<Interceptor>) STOMPClient::new, "stomp client"),
                arguments((Supplier<Interceptor>) WsSecurityInterceptor::new, "ws security"),
                arguments((Supplier<Interceptor>) WsaEndpointRewriterInterceptor::new, "wsa endpoint rewriter")
        );
    }

    @ParameterizedTest
    @MethodSource("interceptors")
    void displayName(Supplier<Interceptor> interceptor, String expected) {
        assertEquals(expected, interceptor.get().getDisplayName());
    }
}
