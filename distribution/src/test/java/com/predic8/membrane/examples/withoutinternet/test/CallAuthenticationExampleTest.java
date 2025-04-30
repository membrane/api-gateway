package com.predic8.membrane.examples.withoutinternet.test;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

public class CallAuthenticationExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "orchestration/call-authentication";
    }

    @Test
    void testCall() {
        given().when().get("http://localhost:2000").then().body(containsString("Secured backend!")).statusCode(200);
    }

    @Test
    void testAuthService() {
        given().when().get("http://localhost:3000/login").then().header("X-Api-Key", "ABCDE").statusCode(200);
    }

    @Test
    void testSecuredBackend() {
        given().when().get("http://localhost:3001").then().statusCode(401);
    }
}
