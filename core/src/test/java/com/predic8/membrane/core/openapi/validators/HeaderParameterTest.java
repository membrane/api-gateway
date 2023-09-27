package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URISyntaxException;
import java.util.stream.Stream;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.openapi.util.Utils.getOpenapiValidatorRequest;
import static com.predic8.membrane.core.openapi.util.Utils.getOpenapiValidatorResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class HeaderParameterTest extends AbstractValidatorTest {

    Exchange exc = new Exchange(null);
    Request request;
    Response response;

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
        response = new Response();
        response.setStatusCode(200);
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
        response.setHeader(header);
        response.getHeader().setValue(Header.CONTENT_TYPE, APPLICATION_JSON);

        exc.setRequest(request);

        ValidationErrors reqErrors = validator.validate(getOpenapiValidatorRequest(exc));

        assertEquals(expected, reqErrors.size());

        exc.setResponse(response);
        ValidationErrors resErrors = validator.validateResponse(getOpenapiValidatorRequest(exc), getOpenapiValidatorResponse(exc));
        System.out.println(resErrors.toString());
        assertEquals(expected, resErrors.size());
        if (reqErrors.isEmpty())
            return;

        ValidationError ve = reqErrors.get(0);
        assertEquals(400,ve.getContext().getStatusCode());
    }

}