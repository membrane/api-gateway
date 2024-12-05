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
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class MultipleTypesNestedTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp() {
        OpenAPIRecord apiRecord = new OpenAPIRecord(parseOpenAPI(getResourceAsStream(this, "/openapi/specs/oas31/multiple-types-nested.yaml")), null, new OpenAPISpec());
        validator = new OpenAPIValidator(new URIFactory(), apiRecord);
    }

    static Stream<Arguments> requestBodyProvider() {
        return Stream.of(
                Arguments.of(
                        """
                        {
                            "root-object": {
                                "string-null-date-time": "2023-01-01T12:00:00Z",
                                "boolean-null": null
                            }
                        }
                        """,
                        0
                ),
                Arguments.of(
                        """
                        {
                            "root-object": {
                                "string-null-date-time": null,
                                "boolean-null": true
                            }
                        }
                        """,
                        0
                ),
                Arguments.of(
                        """
                        {
                            "root-object": {
                                "string-null-date-time": null,
                                "boolean-null": null
                            }
                        }
                        """,
                        0
                ),
                Arguments.of(
                        """
                        {
                            "root-object": {
                                "string-null-date-time": "foo",
                                "boolean-null": 123
                            }
                        }
                        """,
                        2
                ),
                Arguments.of(
                        """
                        {
                            "root-object": null
                        }
                        """,
                        0
                )
        );
    }

    @ParameterizedTest
    @MethodSource("requestBodyProvider")
    void testNestedRequestBody(String requestBody, int expectedErrorSize) throws ParseException {
        ValidationErrors errors = validator.validate(
                Request.post().path("/nested").body(requestBody).mediaType(APPLICATION_JSON)
        );
        System.out.println("errors = " + errors);
        assertEquals(expectedErrorSize, errors.size());
    }
}
