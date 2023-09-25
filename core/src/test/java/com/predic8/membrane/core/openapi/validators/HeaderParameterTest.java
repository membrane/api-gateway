package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.*;
import java.util.stream.Stream;

import static com.predic8.membrane.core.openapi.util.Utils.getOpenapiValidatorRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class HeaderParameterTest extends AbstractValidatorTest {

    Exchange exc = new Exchange(null);
    Request request;

    @Override
    String getOpenAPIFileName() {
        return "/openapi/specs/header-params.yml";
    }

    @BeforeEach
    public void setUp() {
        try {
            request = Request.get("/cities").build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        exc.setOriginalRequestUri("/cities");
        super.setUp();
    }

    static Stream<Arguments> headerDataProvider() {
        return Stream.of(
                arguments(new Header() {{
                    setValue("X-Padding", "V0hQCMkJV4mKigp");
                    setValue("X-Token", "122"); // Must be Integer
                }}, 0),
                arguments(new Header() {{
                    setValue("X-Padding", "V0hQCMkJV4mKigp");
                    setValue("X-Token", "foo"); // Shoud be Integer
                }}, 1),
                arguments(new Header() {{
                    setValue("X-Padding", "V0hQCMkJV4mKigp");
                }}, 1),
                arguments(new Header() {{
                    setValue("X-Token", "1222");
                    setValue("X-Test", "V0hQCMkJV4mKigp"); // Should be ignored by validation
                }}, 0)
        );
    }

    @ParameterizedTest
    @MethodSource("headerDataProvider")
    public void headerParamTest(Header header, int expected) throws Exception {
        request.setHeader(header);
        exc.setRequest(request);

        ValidationErrors errors = validator.validate(getOpenapiValidatorRequest(exc));

        assertEquals(expected, errors.size());

        if (errors.isEmpty())
            return;

        ValidationError ve = errors.get(0);
        assertEquals(400,ve.getContext().getStatusCode());
    }
}