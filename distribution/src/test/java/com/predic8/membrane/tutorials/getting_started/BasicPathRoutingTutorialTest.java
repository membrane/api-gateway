package com.predic8.membrane.tutorials.getting_started;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.core.IsNull.notNullValue;

public class BasicPathRoutingTutorialTest extends AbstractGettingStartedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "40-Basic-Path-Routing.yaml";
    }

    @Test
    void callProducts() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/shop/v2/products")
        .then()
            .statusCode(200)
            .body("meta.count", greaterThanOrEqualTo(0))
            .body("meta.start", greaterThanOrEqualTo(0))
            .body("meta.limit", greaterThan(0))
            .body("products.size()", greaterThan(0))
            .body("products[0].id", greaterThan(0));
        // @formatter:on
    }

    @Test
    void callCatFact() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/fact")
        .then()
            .statusCode(200)
            .body("fact", notNullValue())
            .body("length", greaterThan(0));
        // @formatter:on
    }

    @Test
    void callHttpbin() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/get")
        .then()
            .statusCode(200)
            .body("url", equalTo("https://localhost:2000/get"))
            .body("headers", notNullValue())
            .body("origin", notNullValue());
        // @formatter:on
    }

}
