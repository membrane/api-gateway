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
package com.predic8.membrane.core.openapi.oas31.multipleTypes;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.validators.*;
import com.predic8.membrane.core.util.*;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.stream.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class MultipleTypesComplexTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        validator = new OpenAPIValidator(new URIFactory(),
                new OpenAPIRecord(parseOpenAPI(getResourceAsStream(this, "/openapi/specs/oas31/multiple-types-complex.yaml")), new OpenAPISpec()));
    }

    static Stream<Arguments> requestBodyProvider() {
        return Stream.of(
                Arguments.of(
                        """
                        {
                            "user": { "name": "John", "age": null },
                            "tags": [
                                { "label": "tag1", "value": 1 },
                                { "label": null, "value": null }
                            ]
                        }
                        """,
                        0, "", ""
                ),
                Arguments.of(
                        """
                        {
                            "user": { "name": null, "age": 25 },
                            "tags": null
                        }
                        """,
                        0, "", ""
                ),
                Arguments.of(
                        """
                        {
                            "user": { "name": "Alice", "age": 30 },
                            "tags": [
                                { "label": 123, "value": "invalid" }
                            ]
                        }
                        """,
                        2,
                        "123 is of type integer which does not match any of [string, null]",
                        "\"invalid\" is of type string which does not match any of [integer, null]"
                ),
                Arguments.of(
                        """
                        {
                            "user": { "name": "Bob", "age": null },
                            "tags": [
                                { "label": "tag", "value": 10 },
                                { "label": null, "value": 20 }
                            ]
                        }
                        """,
                        0, "", ""
                ),
                Arguments.of(
                        """
                        {
                            "user": { "name": true, "age": "not-an-integer" },
                            "tags": [
                                { "label": "valid", "value": 5 }
                            ]
                        }
                        """,
                        2,
                        "true is of type boolean which does not match any of [string, null]",
                        "\"not-an-integer\" is of type string which does not match any of [integer, null]"
                )
        );

    }

    @ParameterizedTest
    @MethodSource("requestBodyProvider")
    void testComplexRequestBody(String requestBody, int expectedErrorSize, String errMsg1, String errMsg2) throws ParseException {
        ValidationErrors errors = validator.validate(
                Request.post().path("/complex").body(requestBody).mediaType(APPLICATION_JSON)
        );
        assertEquals(expectedErrorSize, errors.size());
        if(errors.hasErrors()) {
            assertEquals(errMsg1, errors.get(0).getMessage());
            assertEquals(errMsg2, errors.get(1).getMessage());
        }
    }
}
