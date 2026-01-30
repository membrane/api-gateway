package com.predic8.membrane.tutorials.getting_started;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class SetBodyTutorialTest extends AbstractGettingStartedTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "65-SetBody.yaml";
    }

    @Test
    void path_and_headers_list() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/spel")
        .then()
            .statusCode(200)
            .body(allOf(
                    containsString("Path: /spel"),
                    containsString("Headers: Accept,"),
                    containsString("User-Agent"),
                    containsString("Host")
            ));
        // @formatter:on
    }

    @Test
    void extract_city() {
        // @formatter:off
        given()
            .contentType("application/json")
            .body("{\"city\":\"Seoul\"}")
        .when()
            .post("http://localhost:2000/jsonpath")
        .then()
            .statusCode(200)
            .body(startsWith("City: Seoul"));
        // @formatter:on
    }

}
