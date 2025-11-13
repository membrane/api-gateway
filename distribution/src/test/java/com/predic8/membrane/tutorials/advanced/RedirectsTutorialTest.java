package com.predic8.membrane.tutorials.advanced;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;


public class RedirectsTutorialTest extends AbstractAdvancedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "50-Redirects.yaml";
    }

    @Test
    void redirects() {
        // @formatter:off
        given()
            .redirects().follow(false)
        .when()
            .get("http://localhost:2000/fruits/7")
        .then()
            .statusCode(301)
            .header("Location", equalTo("https://api.predic8.de/shop/v2/products/7"));
        // @formatter:on
    }
}