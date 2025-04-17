package com.predic8.membrane.examples.withinternet.test;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.predic8.membrane.core.interceptor.cors.CorsInterceptor.*;
import static com.predic8.membrane.core.util.FileUtil.writeInputStreamToFile;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class CorsExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "/security/cors";
    }

    @BeforeEach
    void startMembrane() throws InterruptedException, IOException {
        writeInputStreamToFile(baseDir + "/proxies.xml", getResourceAsStream("com/predic8/membrane/examples/tutorials/cors/proxies.xml"));
        process = startServiceProxyScript();
    }

    @Test
    void preflightShouldBeAccepted() {
        // @formatter:off
        given()
                .header(ORIGIN, "http://localhost:2001")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .when()
                .options("http://localhost:2000/")
                .then()
                .statusCode(204)
                .header(ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:2001")
                .header(ACCESS_CONTROL_ALLOW_METHODS, containsString("POST"));
        // @formatter:on
    }

    @Test
    void postRequestShouldSucceedWithCorsHeaders() {
        // @formatter:off
        given()
                .header(ORIGIN, "http://localhost:2001")
                .contentType("application/json")
                .when()
                .post("http://localhost:2000/")
                .then()
                .statusCode(210)
                .header(ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:2001");
        // @formatter:on
    }

    @Test
    void disallowedOriginShouldBeRejected() {
        // @formatter:off
        given()
                .header(ORIGIN, "http://not-allowed.com")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .when()
                .options("http://localhost:2000/")
                .then()
                .statusCode(403)
                .body("detail", containsString("not allowed by the CORS policy."));
        // @formatter:on
    }

}
