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

package com.predic8.membrane.core.interceptor.lang;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.router.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON_UTF8;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A simple test is enough to test the logic of the interceptor.
 * Complex expressions are tested in the ExchangeExpressionTest, and ...
 */
class SetBodyInterceptorTest {

    private SetBodyInterceptor sbi;
    private Exchange exc;

    @BeforeEach
    void setup() throws URISyntaxException {
        sbi = new SetBodyInterceptor();

        exc = get("/foo").buildExchange();
        exc.setResponse(notImplemented().body("bar").build());
    }

    @Test
    void nullResult() {
        sbi.setValue("null");
        sbi.init(new DefaultRouter());
        sbi.handleRequest(exc);
        assertEquals("null", exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void evalOfSimpleExpression() {
        sbi.setValue("${path}");
        sbi.init(new DefaultRouter());
        sbi.handleRequest(exc);
        assertEquals("/foo", exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void escapeNull() {
        exc.getRequest().setBodyContent("""
                {"a":null}
                """.getBytes(UTF_8));
        sbi.setLanguage(JSONPATH);
        sbi.setValue("${$.a}");
        sbi.init(new DefaultRouter());
        sbi.handleRequest(exc);
        // When inserting a value from JSONPath into a JSON document like:
        // { "a": ${.a} }
        // Inserting an empty string will break the JSON
        // Different from Groovy: See below
        // See: https://github.com/membrane/api-gateway/discussions/2812
        assertEquals("", exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void escapeNulGroovy() {
        exc.getRequest().setBodyContent("""
                {"a":null}
                """.getBytes(UTF_8));
        sbi.setLanguage(GROOVY);
        sbi.setContentType(APPLICATION_JSON_UTF8);
        sbi.setValue("${fn.jsonPath('$.a')}");
        sbi.init(new DefaultRouter());
        sbi.handleRequest(exc);
        // See also test above
        // Here JSON is not broken. But is it right?
        assertEquals("\"\"", exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void response() {
        sbi.setValue("SC: ${statusCode}");
        sbi.init(new DefaultRouter());
        sbi.handleResponse(exc);
        assertEquals("SC: 501", exc.getResponse().getBodyAsStringDecoded());
    }
}