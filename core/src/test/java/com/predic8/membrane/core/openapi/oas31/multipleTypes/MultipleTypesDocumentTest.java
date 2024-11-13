package com.predic8.membrane.core.openapi.oas31.multipleTypes;

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
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class MultipleTypesDocumentTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp() {
        OpenAPIRecord apiRecord = new OpenAPIRecord(parseOpenAPI(getResourceAsStream(this, "/openapi/specs/oas31/multiple-types-document.yaml")),null, new OpenAPISpec());
        validator = new OpenAPIValidator(new URIFactory(), apiRecord);
    }

    static Stream<Arguments> requestBodyProvider() {
        return Stream.of(
                Arguments.of("1.0", 1,"1.0 is of type number which does not match any of [string, null]"),
                Arguments.of("\"Bonn\"", 0, ""),
                Arguments.of("100", 1, "100 is of type integer which does not match any of [string, null]"),
                Arguments.of("true", 1, "true is of type boolean which does not match any of [string, null]"),
                Arguments.of("null", 0, "")
        );
    }

    @ParameterizedTest
    @MethodSource("requestBodyProvider")
    void testRequestBody(String requestBody, int expectedErrorSize, String msg) throws ParseException {
        ValidationErrors errors = validator.validate(
                post().path("/foo").body(requestBody).mediaType(APPLICATION_JSON)
        );
        assertEquals(expectedErrorSize, errors.size());

        if (errors.hasErrors())
            assertEquals(msg, errors.get(0).getMessage());
    }
}
