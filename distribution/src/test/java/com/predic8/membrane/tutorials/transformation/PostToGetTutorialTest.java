package com.predic8.membrane.tutorials.transformation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PostToGetTutorialTest extends AbstractTransformationTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "10-POST-to-GET.yaml";
    }

    @Test
    void verifyProductListIsSortedAfterPost() {
        var res =
                // @formatter:off
                given()
                    .contentType("application/json")
                    .body("""
                      {"limit":100,"sort":"name"}"""
                    )
                .when()
                    .post("http://localhost:2000/shop/v2/products")
                .then()
                    .statusCode(200)
                    .body("meta", notNullValue())
                    .body("meta.count", greaterThan(0))
                    .body("products", notNullValue())
                    .body("products.size()", greaterThan(0))
                    .body("products[0].name", not(empty()))
                    .body("products[0].self_link", startsWith("/shop/v2/products/"))
                    .extract();
                // @formatter:on

        List<String> names = res.jsonPath().getList("products.name", String.class);

        for (int i = 1; i < names.size(); i++) {
            String prev = names.get(i - 1);
            String cur  = names.get(i);
            if (prev == null || cur == null) continue;
            assertTrue(prev.compareToIgnoreCase(cur) <= 0);
        }
    }
}
