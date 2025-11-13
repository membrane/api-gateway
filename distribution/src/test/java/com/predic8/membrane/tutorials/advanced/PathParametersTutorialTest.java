package com.predic8.membrane.tutorials.advanced;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

public class PathParametersTutorialTest extends AbstractAdvancedTutorialTest{

    @Override
    protected String getTutorialYaml() {
        return "10-PathParameters.yaml";
    }

    @Test
    void pathParameterTemplate() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/customer/7/account/55")
        .then()
            .statusCode(200)
            .body(containsString("Customer: 7"))
            .body(containsString("Account: 55"));
        // @formatter:on
    }

}
