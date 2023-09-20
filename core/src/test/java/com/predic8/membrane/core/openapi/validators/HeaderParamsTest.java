package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.predic8.membrane.core.openapi.util.Utils.getOpenapiValidatorRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class HeaderParamsTest extends AbstractValidatorTest {

    @Override
    String getOpenAPIFileName() {
        return "/openapi/specs/header-params.yml";
    }

    static Stream<Arguments> headerDataProvider() {
        return Stream.of(
                arguments(new Header() {{
                    setValue("X-Padding", "V0hQCMkJV4mKigp");
                    setValue("X-Token", "122");
                }}, 0),
                arguments(new Header() {{
                    setValue("X-Padding", "V0hQCMkJV4mKigp");
                    setValue("X-Token", "foo");
                }}, 1),
                arguments(new Header() {{
                    setValue("X-Padding", "V0hQCMkJV4mKigp");
                }}, 1),
                arguments(new Header() {{
                    setValue("X-Token", "1222");
                    setValue("X-Test", "V0hQCMkJV4mKigp");
                }}, 0)
        );
    }

    @ParameterizedTest
    @MethodSource("headerDataProvider")
    public void headerParamTest(Header header, int expectedResult) throws Exception {
        Request request = Request.get("/cities").header(header).build();
        Exchange exc = new Exchange(null);
        exc.setRequest(request);
        exc.setOriginalRequestUri("/cities");
        ValidationErrors errors = validator.validate(getOpenapiValidatorRequest(exc));
        System.out.println("errors = " + errors);
        assertEquals(expectedResult, errors.size());
    }
}
