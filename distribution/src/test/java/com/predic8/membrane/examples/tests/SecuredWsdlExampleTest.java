package com.predic8.membrane.examples.tests;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.XML;
import static org.hamcrest.Matchers.containsString;

// TODO move to `withoutinternet` directory when pr #1631 is merged
public class SecuredWsdlExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    public String getExampleDirName() {
        return "web-services-soap/secured-wsdl";
    }

    @Test
    void testSecuredWsdl() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2010/services?wsdl")
        .then()
            .statusCode(200)
            .contentType(XML)
            .body(containsString("wsdl:definitions"));
    }
    // @formatter:on
}
