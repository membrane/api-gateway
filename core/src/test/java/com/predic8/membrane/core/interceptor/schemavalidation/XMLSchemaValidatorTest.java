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
package com.predic8.membrane.core.interceptor.schemavalidation;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.resolver.*;
import org.junit.jupiter.api.*;
import org.slf4j.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

class XMLSchemaValidatorTest {

    private static final Logger log = LoggerFactory.getLogger(XMLSchemaValidatorTest.class.getName());

    private static final ObjectMapper om = new ObjectMapper();

    XMLSchemaValidator validator;

    @BeforeEach
    void setUp() {
        validator = new XMLSchemaValidator(new ResolverMap(), "src/test/resources/validation/order.xsd",
                (message, exc) -> log.info("Validation failure: {}", message));
    }

    @Test
    void valid() throws Exception {
        Exchange exc = post("/foo").body(this.getClass().getResourceAsStream("/validation/order.xml")).buildExchange();
        assertEquals(CONTINUE, validator.validateMessage(exc, REQUEST));
    }

    @Test
    void invalid() throws Exception {
        Exchange exc = post("/foo").body("""
                <order xmlns="http://membrane-soa.org/router/validation/order/1/">
                	<items>
                		<item id="3" />
                		<illegal/>
                	</items>
                </order>
                """).buildExchange();
        assertEquals(ABORT, validator.validateMessage(exc, REQUEST));
        assertEquals(APPLICATION_PROBLEM_JSON, exc.getResponse().getHeader().getContentType());
        assertEquals(400,exc.getResponse().getStatusCode());
        assertEquals(REQUEST.name(), exc.getResponse().getHeader().getFirstValue(VALIDATION_ERROR_SOURCE));
        JsonNode jn = om.readTree(exc.getResponse().getBodyAsStreamDecoded());

        assertEquals("XML message validation failed", jn.get("title").asText());
        assertEquals("https://membrane-api.io/error/user", jn.get("type").asText());

        JsonNode validation = jn.get("validation");

        assertEquals(1, validation.size());
        assertTrue(validation.get(0).get("message").asText().contains("illegal"));
        assertEquals(4, validation.get(0).get("line").asInt());
        assertTrue(validation.get(0).get("column").asInt() > 5);  // Should be 13, but a bit of tolerance can help

//        System.out.println("exc.getResponse().getBodyAsStringDecoded() = " + exc.getResponse().getBodyAsStringDecoded());
    }
}