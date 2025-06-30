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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.lang.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;

abstract class AbstractSetPropertyInterceptorTest {

    Router router;
    Exchange exc;
    AbstractSetterInterceptor interceptor;

    protected abstract ExchangeExpression.Language getLanguage();

    @BeforeEach
    void setUp() throws URISyntaxException {
        interceptor = new SetPropertyInterceptor();
        interceptor.setLanguage(getLanguage());
        router = new Router();
        exc = Request.get("/dummy")
                .contentType(APPLICATION_JSON)
                .body("""
                        {
                            "animal": "fish",
                            "color": "blue",
                            "countries": {
                                "de": "Germany",
                                "fr": "France"
                            }
                        }
                        """)
                .buildExchange();
        exc.setProperty("exists", "false");
        exc.setProperty("map", Map.of("pi", "3151", "city", "Berlin", "plant", "Jupiter"));
    }
}