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
package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.test.TestAppender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The WSDL embeds two schemas and the request element is declared in the SECOND one, so the first
 * schema's validator is tried (and fails) before the matching one succeeds. These tests pin the
 * logging contract: per-schema errors from the schema that did not match must NOT be logged for a
 * message that validates against another schema; a genuine failure (no schema matches) must be
 * logged once, with all collected errors.
 */
public class MultiSchemaValidationLoggingTest {

    private static final String EMBEDDED_WSDL = "src/test/resources/ws/import/second-schema-match.wsdl";

    private Logger root;
    private TestAppender appender;

    @BeforeEach
    void attachAppender() {
        root = (Logger) LogManager.getRootLogger();
        appender = new TestAppender("MultiSchemaValidationLoggingTest");
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        root.removeAppender(appender);
        appender.stop();
    }

    @Test
    void validMessageDoesNotLogErrorsFromNonMatchingSchemas() throws Exception {
        var exc = requestExchange("""
                <b:Req xmlns:b="http://example.com/b"><b:value>hello</b:value></b:Req>
                """);

        assertEquals(CONTINUE, validator().validateMessage(exc, REQUEST));
        assertFalse(appender.contains("cvc-elt"), "non-matching schemas must not log: " + appender.getMessages());
        assertFalse(appender.contains("did not validate"), appender.getMessages().toString());
    }

    @Test
    void invalidMessageLogsConsolidatedErrorOnce() throws Exception {
        var exc = requestExchange("""
                <b:Req xmlns:b="http://example.com/b"/>
                """);

        assertEquals(ABORT, validator().validateMessage(exc, REQUEST));
        assertTrue(appender.contains("did not validate against any schema"), appender.getMessages().toString());
    }

    private static WSDLValidator validator() {
        var v = new WSDLValidator(new ResolverMap(), EMBEDDED_WSDL, "Svc", null, false);
        v.init();
        return v;
    }

    private static Exchange requestExchange(String payload) {
        try {
            return Request.post("/x").body("""
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                        <s:Body>%s</s:Body>
                    </s:Envelope>
                    """.formatted(payload)).contentType(TEXT_XML).buildExchange();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
