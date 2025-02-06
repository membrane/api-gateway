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
import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.validators.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.test.*;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.stream.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static com.predic8.membrane.core.util.URIUtil.convertPath2FilePathString;
import static org.junit.jupiter.api.Assertions.*;

public class RequestReferenceTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp() {
        OpenAPIRecord apiRecord = new OpenAPIRecord(parseOpenAPI( convertPath2FilePathString(TestUtil.getPathFromResource("openapi/specs/oas31/request-reference.yaml"))), new OpenAPISpec());
        validator = new OpenAPIValidator(new URIFactory(), apiRecord);
    }

    static Stream<Arguments> createUserRequestProvider() {
        return Stream.of(
                Arguments.of("""
                        {"email": "max@example.com", "createdAt": "2024-01-01T12:00:00Z"}""", 0, ""),
                Arguments.of("""
                        {"email": "max@example.com", "id": 123, "createdAt": "2024-01-01T12:00:00Z"}""", 0, ""),
                Arguments.of("""
                        {}""", 1, "Required property email is missing."),
                Arguments.of("""
                        {"email": "invalid-email"}""", 1, "The string 'invalid-email' is not a valid email."),
                Arguments.of("""
                        {"email": "max@example.com", "createdAt": "not-a-datetime"}""", 1, "The string 'not-a-datetime' is not a valid date-time according to ISO 8601."),
                Arguments.of("""
                        {"id": 123}""", 1, "Required property email is missing."),
                Arguments.of("""
                        {"email": "max@example.com", "id": "not-a-number"}""", 1, "\"not-a-number\" is of type string which does not match any of [integer]")
        );
    }

    @ParameterizedTest
    @MethodSource("createUserRequestProvider")
    void testUserCreationRequestValidation(String requestBody, int expectedErrorSize, String errMsg) throws ParseException {
         ValidationErrors errors = validator.validate(
                Request.post().path("/users").body(requestBody).mediaType(APPLICATION_JSON)
        );

        System.out.println("errors = " + errors);

        assertEquals(expectedErrorSize, errors.size());
        if(errors.hasErrors())
            assertEquals(errMsg, errors.getErrors().getFirst().getMessage());
    }
}