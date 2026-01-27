package com.predic8.membrane.tutorials.transformation;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.withArgs;
import static org.hamcrest.Matchers.*;

public class GetToPostTutorialTest extends AbstractTransformationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "20-GET-to-POST.yaml";
    }

    @Test
    void addedProductIsPresentInUpstreamList() {
        var added =
                // @formatter:off
                given()
                    .queryParam("name", "Lemon")
                    .queryParam("price", "0.30")
                .when()
                    .get("http://localhost:2000/add")
                .then()
                    .statusCode(201)
                    .body("id", notNullValue())
                    .body("name", equalTo("Lemon"))
                    .body("price", equalTo(0.3F))
                    .body("self_link", startsWith("/shop/v2/products/"))
                    .extract()
                    .jsonPath();
                // @formatter:on

        int id = added.getInt("id");

        // @formatter:off
        given()
            .relaxedHTTPSValidation()
            .queryParam("limit", 1000)
        .when()
            .get("https://api.predic8.de/shop/v2/products")
        .then()
            .statusCode(200)
            .body("products.find { it.id == %s }.name", withArgs(id), equalTo("Lemon"))
            .body("products.find { it.id == %s }.self_link", withArgs(id), equalTo("/shop/v2/products/" + id));
        // @formatter:on
    }

}
