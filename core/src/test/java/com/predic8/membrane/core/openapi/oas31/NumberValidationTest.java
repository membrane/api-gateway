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

public class NumberValidationTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp() {
        OpenAPIRecord apiRecord = new OpenAPIRecord(parseOpenAPI(getResourceAsStream(this, "/openapi/specs/oas31/number-validation.yaml")), null, new OpenAPISpec());
        validator = new OpenAPIValidator(new URIFactory(), apiRecord);
    }

    static Stream<Arguments> responseProvider() {
        return Stream.of(
                Arguments.of("/number", 123.45, 0),
                Arguments.of("/number", 678, 0),
                Arguments.of("/number", "invalid", 1),

                Arguments.of("/integer", 42, 0),
                Arguments.of("/integer", -1, 0),
                Arguments.of("/integer", 123.456, 2)
        );
    }

    @ParameterizedTest
    @MethodSource("responseProvider")
    void testNumberAndIntegerResponses(String path, Object responseBody, int expectedErrorSize) throws ParseException {
        ValidationErrors errors = validator.validate(
                Request.post().path(path).body(responseBody.toString()).mediaType(APPLICATION_JSON)
        );
        System.out.println("errors = " + errors);
        assertEquals(expectedErrorSize, errors.size());
    }
}
