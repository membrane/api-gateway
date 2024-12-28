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

package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.lang.xpath.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static org.junit.jupiter.api.Assertions.*;

public class XPathExpressionTest {

    static Exchange exc;

    @BeforeAll
    static void setup() throws URISyntaxException {
        exc = Request.post("/foo").body("""
                <person id="7"/>
                """).buildExchange();
    }

    @Test
    void simple() {
        assertTrue( new XPathExchangeExpression("true()").evaluate(exc, REQUEST));
    }

    @Test
    void attribute() {
        assertTrue( new XPathExchangeExpression("//person/@id = 7").evaluate(exc, REQUEST));
    }
}