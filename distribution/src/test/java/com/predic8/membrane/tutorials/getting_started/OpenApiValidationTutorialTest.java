package com.predic8.membrane.tutorials.getting_started;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.core.IsNull.notNullValue;

public class OpenApiValidationTutorialTest extends AbstractGettingStartedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "90-OpenAPI-Validation.yaml";
    }

    @Test
    void createProductNegativePriceFailsValidation() {
        // @formatter:off
        given()
            .contentType("application/json")
            .body("""
            { "name": "Figs", "price": -2.7 }
            """)
        .when()
            .post("http://localhost:2000/shop/v2/products")
        .then()
            .statusCode(is(400))
            .body("title", equalTo("OpenAPI message validation failed"))
            .body("validation.errors", notNullValue())
            .body("validation.errors.size()", greaterThan(0));
        // @formatter:on
    }

}
