package com.predic8.membrane.core.openapi.oas31;

import com.predic8.membrane.core.openapi.OpenAPIValidator;
import com.predic8.membrane.core.openapi.model.Request;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.openapi.validators.ValidationErrors;
import com.predic8.membrane.core.util.URIFactory;
import jakarta.mail.internet.ParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.openapi.util.TestUtils.getResourceAsStream;
import static com.predic8.membrane.core.openapi.util.TestUtils.parseOpenAPI;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultipleTypesTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp() {
        OpenAPIRecord apiRecord = new OpenAPIRecord(parseOpenAPI(getResourceAsStream(this, "/openapi/specs/oas31/multiple-types.yaml")),null, new OpenAPISpec());
        validator = new OpenAPIValidator(new URIFactory(), apiRecord);
    }

    static Stream<Arguments> requestBodyProvider() {
        return Stream.of(
                Arguments.of("{\"name\": 1.0}", 2),
                Arguments.of("{\"name\": \"string\"}", 0),
                Arguments.of("{\"name\": 100}", 2),
                Arguments.of("{\"name\": true}", 2),
                Arguments.of("{\"name\": null}", 0)
        );
    }

    @ParameterizedTest
    @MethodSource("requestBodyProvider")
    void testRequestBody(String requestBody, int expectedErrorSize) throws ParseException {
        ValidationErrors errors = validator.validate(
                Request.post().path("/foo").body(requestBody).mediaType(APPLICATION_JSON)
        );

        System.out.println("errors = " + errors);
        assertEquals(expectedErrorSize, errors.size());
    }

}
