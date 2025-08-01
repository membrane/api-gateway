package com.predic8.membrane.examples.withinternet.test;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.containsString;

public class OutgoingAPIGatewayExampleTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "routing-traffic/outgoing-api-gateway";
    }

    @Test
    void test() throws Exception {
        try (Process2 ignored = startServiceProxyScript()) {
            // @formatter:off
            given()
                    .header("X-Api-Key", "10430")
                    .header("User-Agent", "secret")
                    .header("Authorization", "secret")
            .when()
                    .get("http://localhost:2000")
            .then()
                    .statusCode(200)
                    .body(containsString("X-Api-Key"))
                    .body(not(containsString("User-Agent")))
                    .body(not(containsString("Authorization")));
            // @formatter:on
        }
    }
}

