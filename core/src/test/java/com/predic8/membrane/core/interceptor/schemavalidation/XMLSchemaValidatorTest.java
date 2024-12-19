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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.resolver.*;
import org.junit.jupiter.api.*;
import org.slf4j.*;

class XMLSchemaValidatorTest {

    private static final Logger log = LoggerFactory.getLogger(XMLSchemaValidatorTest.class.getName());

    XMLSchemaValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        validator = new XMLSchemaValidator(new ResolverMap(), "src/test/resources/validation/order.xsd", (message, exc) -> log.info("Validation failure: " + message));
    }


    @Test
    void validate() throws Exception {

        Exchange exc = Request.post("/foo").body(this.getClass().getResourceAsStream("/validation/order.xml").readAllBytes()).buildExchange();
        Outcome res = validator.validateMessage(exc, exc.getRequest());
        Assertions.assertEquals(Outcome.CONTINUE, res);
    }
}