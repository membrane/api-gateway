package com.predic8.membrane.examples.tests.openapi;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class OpenAPIRewriteTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "rewriter/openapi";
    }

    // @formatter:off
    @Test
    public void test() throws Exception {
        given()
        .when()
            .get("http://localhost:2000/api-docs/demo-api-v1-0")
        .then()
            .body(containsString("http://predic8.de:3000/foo"));
    }
    // @formatter:on
}
