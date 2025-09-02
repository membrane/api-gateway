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
package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PolyglotSessionIdExtractorTest extends AbstractSessionIdExtractorTest {

    public static final String X_SESSION = "X-Session";
    private static PolyglotSessionIdExtractor extractor;

    @BeforeAll
    static void setup() {
        extractor = new PolyglotSessionIdExtractor();
        extractor.setLanguage(SPEL);
        extractor.setSessionSource("headers['%s']".formatted(X_SESSION));
        extractor.init(new Router());
    }

    @Test
    void requestExtraction() throws Exception {
        Request req = new Request();

        req.setHeader(getHeader("12345"));
        assertEquals("12345", extractor.getSessionId(getExchange(req), REQUEST));

        assertEquals(false, extractor.hasSessionId(getExchange(new Request()), REQUEST));
    }

    @Test
    void responseExtraction() throws Exception {
        Response res = new Response();

        res.setHeader(getHeader("12345"));
        assertEquals("12345", extractor.getSessionId(getExchange(res), RESPONSE));

        assertEquals(false, extractor.hasSessionId(getExchange(new Response()), RESPONSE));
    }

    private Header getHeader(String sessionId) {
        return new Header() {{
            setValue(X_SESSION, sessionId);
        }};
    }
}
