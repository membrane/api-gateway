package com.predic8.membrane.examples.withinternet.test.orchestatrion;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

public class ForLoopExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "orchestration/for-loop";
    }

    // @formatter:off
    @Test
    void testCall() {
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .body(
                containsString("\"products\":["),
                containsString("\"name\":"),
                containsString("\"price\":")
            ).statusCode(200);
    }
    // @formatter:on
}
