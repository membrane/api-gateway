package com.predic8.membrane.tutorials.json;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

public class JsonTransformationTutorialTest extends AbstractJsonTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "20-Json-Transformation.yaml";
    }

    @Test
    void orderJsonIsProcessed() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("order.json"))
            .contentType(JSON)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("number", equalTo(324))
            .body("positions.size()", equalTo(3))
            .body("positions.product", hasItems("Tea", "Butter", "Coffee"));
        // @formatter:on
    }

}
