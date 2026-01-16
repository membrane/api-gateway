package com.predic8.membrane.tutorials.json;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;

public class JsonPathTutorialTest extends AbstractJsonTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "10-JSONPath.yaml";
    }

    @Test
    void animalsJsonIsProcessed() throws IOException {
        // @formatter:off
        given()
            .body(readFileFromBaseDir("animals.json"))
            .contentType(JSON)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("number", equalTo(2))
            .body("animals.size()", equalTo(2))
            .body("animals.name", hasItems("Skye", "Molly"));
        // @formatter:on
    }

    @Test
    void emptyJsonIsRejectedWithProblemDetails() {
        // @formatter:off
        given()
            .body("{}")
            .contentType(JSON)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(404)
            .body("status", equalTo(404))
            .body("title", containsStringIgnoringCase("invalid"))
            .body("type", containsString("problems/user"));
        // @formatter:on
    }

}
