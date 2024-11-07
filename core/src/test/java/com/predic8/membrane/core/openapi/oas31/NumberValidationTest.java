package com.predic8.membrane.core.openapi.oas31;

import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.openapi.OpenAPIValidator;
import com.predic8.membrane.core.openapi.model.Request;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.openapi.validators.ValidationErrors;
import com.predic8.membrane.core.util.URIFactory;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.stream.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

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
