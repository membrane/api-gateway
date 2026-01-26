package com.predic8.membrane.tutorials.transformation;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class TemplateJsonFromMapTutorialTest extends AbstractTransformationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "30-Template-JSON-from-MAP.yaml";
    }

    @Test
    void responseMap() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(containsString("application/json"))
            .body("Host", not(empty()))
            .body("Accept", notNullValue())
            .body("User-Agent", not(empty()))
            .body("X-Forwarded-For", not(empty()))
            .body("X-Forwarded-Proto", anyOf(equalTo("http"), equalTo("https")))
            .body("X-Forwarded-Host", not(empty()));
        // @formatter:on
    }

}
