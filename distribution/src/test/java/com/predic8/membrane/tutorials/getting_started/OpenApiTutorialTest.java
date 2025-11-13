package com.predic8.membrane.tutorials.getting_started;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThan;

public class OpenApiTutorialTest extends AbstractGettingStartedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "80-OpenAPI.yaml";
    }

    @Test
    void apiDocs() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/api-docs")
            .then()
        .statusCode(200)
            .body(containsString("openapi"))
            .body(containsString("Fruit Shop API"));
        // @formatter:on
    }

    @Test
    void fruitshopProducts() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/shop/v2/products")
        .then()
            .statusCode(200)
            .body("meta", notNullValue())
            .body("products", notNullValue())
            .body("products.size()", greaterThan(0));
        // @formatter:on
    }

    @Test
    void dlpFieldsCity() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/dlp/fields/city")
        .then()
            .statusCode(200)
            .body("field", notNullValue())
            .body("category", notNullValue())
            .body("description", notNullValue());
        // @formatter:on
    }






}
