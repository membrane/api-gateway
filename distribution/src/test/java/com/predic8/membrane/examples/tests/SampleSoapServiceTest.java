package com.predic8.membrane.examples.tests;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import static com.predic8.membrane.test.AssertUtils.getAndAssert200;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SampleSoapServiceTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "soap/sampleSoapService";
    }

    private final HashMap<String, String> methodGETmap = new HashMap<>() {{
        put("/foo?wsdl", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><wsdl:definitions xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\" xmlns:tns=\"https://predic8.de/cities\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\" targetNamespace=\"https://predic8.de/cities\" name=\"cities\">\n" +
                "    <wsdl:types>");
        put("/foo/bar?wSdL", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><wsdl:definitions xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\" xmlns:tns=\"https://predic8.de/cities\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\" targetNamespace=\"https://predic8.de/cities\" name=\"cities\">\n" +
                "    <wsdl:types>");
        put("/foo", "<faultstring>Method Not Allowed</faultstring>");

    }};

    final List<String> parameters() {
        return methodGETmap.keySet().stream().toList();
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testMethod(String query) throws Exception {
        given()
        .when()
            .get("http://localhost:2000/" + query)
        .then()
            .body(containsString(methodGETmap.get(query)));

    }

    @Test
    public void testPostBonn() throws IOException {
        given()
            .body(readFileFromBaseDir("request.xml"))
        .when()
            .post("http://localhost:2000/")
        .then()
            .body(containsString("<population>327000</population>"));
    }

    @Test
    public void testPostLondon() throws IOException {
        given()
            .body(readFileFromBaseDir("request.xml").replace("Bonn", "London"))
        .when()
            .post("http://localhost:2000/")
        .then()
            .body(containsString("<population>8980000</population>"));
    }

    @Test
    public void testNotFound() throws IOException {
        given()
                .body(readFileFromBaseDir("request.xml").replace("Bonn", "Berlin"))
                .when()
                .post("http://localhost:2000/")
                .then()
                .body(containsString("<errorcode>404</errorcode>"));
    }

}
