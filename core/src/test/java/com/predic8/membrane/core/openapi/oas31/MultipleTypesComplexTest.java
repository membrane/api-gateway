package com.predic8.membrane.core.openapi.oas31;

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

public class MultipleTypesComplexTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp() {
        OpenAPIRecord apiRecord = new OpenAPIRecord(parseOpenAPI(getResourceAsStream(this, "/openapi/specs/oas31/multiple-types-complex.yaml")), null, new OpenAPISpec());
        validator = new OpenAPIValidator(new URIFactory(), apiRecord);
    }

    static Stream<Arguments> requestBodyProvider() {
        return Stream.of(
                Arguments.of("{\"user\": {\"name\": \"John\", \"age\": null}, \"tags\": [{\"label\": \"tag1\", \"value\": 1}, {\"label\": null, \"value\": null}]}", 0),
                Arguments.of("{\"user\": {\"name\": null, \"age\": 25}, \"tags\": null}", 0),
                Arguments.of("{\"user\": {\"name\": \"Alice\", \"age\": 30}, \"tags\": [{\"label\": 123, \"value\": \"invalid\"}]}", 2),
                Arguments.of("{\"user\": {\"name\": \"Bob\", \"age\": null}, \"tags\": [{\"label\": \"tag\", \"value\": 10}, {\"label\": null, \"value\": 20}]}", 0),
                Arguments.of("{\"user\": {\"name\": true, \"age\": \"not-an-integer\"}, \"tags\": [{\"label\": \"valid\", \"value\": 5}]}", 2)
        );
    }

    @ParameterizedTest
    @MethodSource("requestBodyProvider")
    void testComplexRequestBody(String requestBody, int expectedErrorSize) throws ParseException {
        ValidationErrors errors = validator.validate(
                Request.post().path("/complex").body(requestBody).mediaType(APPLICATION_JSON)
        );

        System.out.println("errors = " + errors);
        assertEquals(expectedErrorSize, errors.size());
    }
}
