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

package com.predic8.membrane.core.interceptor.misc;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SetPropertyInterceptorTest {

    Exchange exc;
    AbstractSetterInterceptor interceptor = new SetPropertyInterceptor();

    @BeforeEach
    void setUp() {
        exc = new Exchange(null);
        exc.setRequest(new Request());
        exc.setProperty("exists", "false");
        interceptor.setName("exists");
    }

    @Test
    @DisplayName("Overwrite header when it is not absent")
    void simple() throws Exception {
        interceptor.setValue("true");
        interceptor.handleRequest(exc);
        assertEquals("true", exc.getProperty("exists"));
    }

    @Test
    @DisplayName("Only set if the header is absent")
    void onlyIfAbsent() throws Exception {
        interceptor.setValue("true");
        interceptor.setIfAbsent(true);
        interceptor.handleRequest(exc);
        assertEquals("false", exc.getProperty("exists"));
        interceptor.setName("doesNotExist");
        interceptor.handleRequest(exc);
        assertEquals("true", exc.getProperty("doesNotExist"));
    }
}