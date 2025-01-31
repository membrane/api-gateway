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

import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.flow.invocation.FlowTestInterceptors.*;
import static java.util.Optional.empty;
import static org.junit.jupiter.api.Assertions.*;

class AbstractProxyTest {

    private static AbstractProxy proxy;

    @BeforeAll
    static void beforeAll() {
        proxy = new AbstractProxy() {{
           interceptors = List.of(ECHO, RETURN, ABORT);
        }};
    }

    @Test
    void getFirstInterceptorOfTypeTest() {
        assertEquals( Optional.of(RETURN), proxy.getFirstInterceptorOfType(RETURN.getClass()));
    }

    @Test
    void getFirstInterceptorOfTypeNotFound() {
        assertEquals( empty(), proxy.getFirstInterceptorOfType(EXCEPTION.getClass()));
    }
}