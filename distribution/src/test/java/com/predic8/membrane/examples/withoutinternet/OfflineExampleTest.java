package com.predic8.membrane.examples.withoutinternet;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.*;

public class OfflineExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "/offline";
    }

    @Test
    public void toBackend() {
        // @formatter:off
        when()
            .get("http://localhost:2000/foo")
        .then()
            .statusCode(200)
            .body("success", equalTo(true));
        // @formatter:on
    }

    @Test
    public void apiDocs() {
        // @formatter:off
        when()
            .get("http://localhost:2000/api-docs")
        .then()
            .statusCode(200)
            .body("fruit-shop-api-v2-2-0.size()", equalTo(5))
            .body("fruit-shop-api-v2-2-0.openapi", equalTo("3.0.3"))
            .body("fruit-shop-api-v2-2-0.title", equalTo("Fruit Shop API"))
            .body("fruit-shop-api-v2-2-0.version", equalTo("2.2.0"))
            .body("fruit-shop-api-v2-2-0.openapi_link", equalTo("/api-docs/fruit-shop-api-v2-2-0"))
            .body("fruit-shop-api-v2-2-0.ui_link", equalTo("/api-docs/ui/fruit-shop-api-v2-2-0"));
        // @formatter:on
    }

    @Test
    public void shopV2_fruits() {
        // @formatter:off
        when()
            .get("http://localhost:2000/shop/v2")
        .then()
            .statusCode(200)
            .body("fruits", contains("apple", "cherry", "pear"))
            .body("fruits.size()", equalTo(3));
        // @formatter:on
    }

    @Test
    public void admin() {
        // @formatter:off
        when()
            .get("http://localhost:9000")
        .then()
            .statusCode(200)
            .body(containsString("/admin"));
        // @formatter:on
    }

}
