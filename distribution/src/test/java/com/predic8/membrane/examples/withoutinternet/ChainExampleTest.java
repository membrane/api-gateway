package com.predic8.membrane.examples.withoutinternet;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class ChainExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "/extending-membrane/reusable-plugin-chains";
    }

    // @formatter:off
    @Test
    public void request1() {
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .assertThat()
            .body(containsString("CORS headers applied"))
            .statusCode(200);
    }

    @Test
    public void request2() {
        given()
        .when()
            .get("http://localhost:2001")
        .then()
            .assertThat()
            .body(containsString("CORS headers applied"))
            .statusCode(404);
    }
    // @formatter:on
}
