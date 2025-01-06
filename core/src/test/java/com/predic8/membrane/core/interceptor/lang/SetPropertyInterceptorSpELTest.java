/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.lang;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class SetPropertyInterceptorSpELTest extends AbstractSetPropertyInterceptorTest {

    @Test
    @DisplayName("Overwrite header when it is not absent")
    void simple() throws Exception {
        interceptor.setName("exists");
        interceptor.setValue("true");
        interceptor.init(router);
        interceptor.handleRequest(exc);
        assertEquals("true", exc.getProperty("exists"));
    }

    @Test
    @DisplayName("Only set if the header is absent")
    void onlyIfAbsent() throws Exception {
        interceptor.setName("exists");
        interceptor.setValue("true");
        interceptor.setIfAbsent(true);
        interceptor.init(router);
        interceptor.handleRequest(exc);
        assertEquals("false", exc.getProperty("exists"));
        interceptor.setName("doesNotExist");
        interceptor.handleRequest(exc);
        assertEquals("true", exc.getProperty("doesNotExist"));
    }
}