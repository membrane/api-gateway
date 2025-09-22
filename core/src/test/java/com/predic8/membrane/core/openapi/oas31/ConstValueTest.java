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
package com.predic8.membrane.core.openapi.oas31;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.validators.*;
import com.predic8.membrane.core.util.*;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.stream.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.openapi.model.Request.*;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class ConstValueTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp() {
        OpenAPIRecord apiRecord = new OpenAPIRecord(parseOpenAPI(getResourceAsStream(this, "/openapi/specs/oas31/const-value.yaml")), new OpenAPISpec());
        validator = new OpenAPIValidator(new URIFactory(), apiRecord);
    }

    static Stream<Arguments> jsonConstRequestBodyProvider() {
        return Stream.of(
                Arguments.of("{\"constantValue\": \"EXPECTED_VALUE\"}", 0),
                Arguments.of("{\"constantValue\": \"WRONG_VALUE\"}", 1),
                Arguments.of("{\"constantValue\": 123}", 1)
        );
    }

    @ParameterizedTest
    @MethodSource("jsonConstRequestBodyProvider")
    void testJsonConst(String requestBody, int expectedErrorSize) throws ParseException {
        ValidationErrors errors = validator.validate(
                post().path("/const-check").body(requestBody).mediaType(APPLICATION_JSON)
        );
        assertEquals(expectedErrorSize, errors.size());
    }
}
