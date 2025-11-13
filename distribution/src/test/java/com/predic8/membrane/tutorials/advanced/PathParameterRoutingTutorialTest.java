package com.predic8.membrane.tutorials.advanced;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.IsNull.notNullValue;


public class PathParameterRoutingTutorialTest extends AbstractAdvancedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "20-Path-Parameter-Routing.yaml";
    }

    @Test
    void pathParameterRouting() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/fruits/7")
        .then()
            .statusCode(200)
            .body("id", equalTo(7))
            .body("name",  notNullValue())
            .body("modified_at", notNullValue());
        // @formatter:on
    }


}
