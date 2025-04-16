package com.predic8.membrane.examples.withinternet.test;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class CallExampleTest extends AbstractSampleMembraneStartStopTestcase {

//    @Override
//    protected String getExampleDirName() {
//        return "extending-membrane/call";
//    }
//
//    @Test
//    void testSecuredBackend() {
//        given().when().get("http://localhost:3001").then().assertThat().statusCode(401);
//        given().when().get("http://localhost:2000").then().statusCode(200).body(containsString("Secured backend!"));
//    }

}
