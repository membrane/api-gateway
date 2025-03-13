package com.predic8.membrane.examples.tests.versioning;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

// TODO move to `withinternet` directory when pr #1631 is merged
public class ChainExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "/extending-membrane/using-chains";
    }

    // @formatter:off
    @Test
    public void request1() {
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .assertThat()
            .body(containsString("Response set by chain"))
            .statusCode(200);
    }

    @Test
    public void request2() {
        given()
        .when()
            .get("http://localhost:2001")
        .then()
            .assertThat()
            .body(containsString("Response set by chain"))
            .statusCode(404);
    }
    // @formatter:on
}
