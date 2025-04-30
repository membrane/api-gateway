package com.predic8.membrane.examples.withinternet.test.orchestatrion;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

public class CallGetExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "orchestration/call-get";
    }

    @Test
    void testCall() {
        given().when().get("http://localhost:2000").then().body(containsString("modified_at")).statusCode(200);
    }
}
