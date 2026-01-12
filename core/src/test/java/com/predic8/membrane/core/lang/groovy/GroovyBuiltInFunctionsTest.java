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

package com.predic8.membrane.core.lang.groovy;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static org.junit.jupiter.api.Assertions.*;

class GroovyBuiltInFunctionsTest {

    GroovyBuiltInFunctions functions;

    @BeforeEach
    void setUp() throws URISyntaxException {
        functions = new GroovyBuiltInFunctions(post("/foo").xml("<person name='Fritz'/>").buildExchange(), REQUEST );
    }

    @Test
    void xpath() {
        assertEquals("Fritz", functions.xpath("/person/@name"));
    }
}