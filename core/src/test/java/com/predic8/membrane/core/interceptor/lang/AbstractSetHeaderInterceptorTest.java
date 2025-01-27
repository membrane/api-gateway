/* Copyright 2022 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.*;

abstract class AbstractSetHeaderInterceptorTest {

    Exchange exchange;
    Message message;
    final AbstractSetterInterceptor interceptor = new SetHeaderInterceptor();
    static Router router;

    protected abstract Language getLanguage();

    @BeforeEach
    void setUp() throws Exception {
        router = new Router();
        exchange = new Exchange(null) {{
            setRequest(new Request.Builder().post("/boo")
                    .header("host", "localhost:8080")
                    .header("X-Api-Key", "31415")
                    .contentType(APPLICATION_JSON)
                    .body("""
                        {
                            "name": "Mango",
                            "a": 5,
                            "tags": ["PRIVATE","BUSINESS",7],
                            "map": {
                                "a": 3141592,
                                "b": "Manaus"
                            }
                        }
                        """)
                    .build());
            setProperty("prop", 88);
            setProperty("bar", "Panama");
        }};
        message = exchange.getRequest();
        interceptor.setLanguage(getLanguage());
        interceptor.setFieldName("X-Bar");
        interceptor.setValue("42");
    }

    protected void extracted(String expression, Object expected) {
        interceptor.setValue(expression);
        interceptor.init(router);
        interceptor.handleRequest(exchange);
        assertEquals(expected, getHeader("x-bar"));
    }

    protected String getHeader(String fieldname) {
        return exchange.getRequest().getHeader().getFirstValue(fieldname);
    }
}