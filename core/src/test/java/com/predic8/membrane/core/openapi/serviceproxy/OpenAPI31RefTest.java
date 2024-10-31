package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.URIFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.predic8.membrane.core.openapi.util.TestUtils.createProxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OpenAPI31RefTest {

    OpenAPIInterceptor interceptor;
    OpenAPISpec spec;
    Exchange exc;
    Router router;

    @BeforeEach
    public void setUp() throws Exception {
        router = new Router();
        router.setUriFactory(new URIFactory());

        spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/specs/openapi-v3_1/external-references/client.yaml";

        interceptor = new OpenAPIInterceptor(createProxy(router, spec), router);
        interceptor.init(router);
    }

    static Stream<Arguments> provideTestCases() {
        return Stream.of(
                Arguments.of("GET", "/clients/123", "Accept", "application/json", null, 200),
                Arguments.of("POST", "/clients", "Content-Type", "application/json",
                        "{\"firstName\": \"John\", \"lastName\": \"Doe\", \"contactDetails\": {\"email\": \"john.doe@example.com\", \"phoneNumber\": \"+1234567890\"}}",
                        201),
                Arguments.of("POST", "/clients/with-person", "Content-Type", "application/json",
                        "{\"firstName\": \"Jane\", \"lastName\": \"Smith\", \"contactDetails\": {\"email\": \"jane.smith@example.com\", \"phoneNumber\": \"+0987654321\"}, \"membershipLevel\": \"Gold\"}",
                        201)
        );
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    void testRequests(String method, String url, String headerName, String headerValue, String body, int expectedStatusCode) throws Exception {
        exc = new Exchange(null);
        Request.Builder requestBuilder = new Request.Builder()
                .method(method)
                .url(new URIFactory(), "http://localhost:2000" + url)
                .header(headerName, headerValue);

        if (body != null) {
            requestBuilder.body(body);
        }

        exc.setRequest(requestBuilder.build());
        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exc));
        assertEquals(expectedStatusCode, exc.getResponse().getStatusCode());
    }
}
