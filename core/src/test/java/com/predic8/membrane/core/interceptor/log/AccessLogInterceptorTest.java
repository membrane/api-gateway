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
package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.util.*;

public class AccessLogInterceptorTest {

    AccessLogInterceptor interceptor;

    @BeforeEach
    void setUp() throws Exception {
        interceptor = new AccessLogInterceptor();

        var variables = new ArrayList<AdditionalVariable>();
        AdditionalVariable av1 = new AdditionalVariable();
        av1.setName("foo");
        av1.setExpression("headers.foo");
        AdditionalVariable av2 = new AdditionalVariable();
        av2.setExpression("headers['X-Forwarded-For']");
        av2.setName("Forwarded");
        variables.add(av1);
        variables.add(av2);

        interceptor.setAdditionalPatternList(variables);
        interceptor.init();
    }

    @Test
    void simple() throws Exception {
        interceptor.handleResponse(Request.get("/foo").header("foo","bar").header("X-Forwarded-For","bazf").buildExchange());
    }

}