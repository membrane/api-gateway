package com.predic8.membrane.examples.withinternet;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_SOAP;
import static com.predic8.membrane.core.util.FileUtil.writeInputStreamToFile;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.HTML;
import static org.hamcrest.Matchers.containsString;

public class TutorialSoapExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "../tutorials/soap";
    }

    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {
        writeInputStreamToFile(baseDir + "/proxies.xml", getResourceAsStream("com/predic8/membrane/examples/tutorials/soap/soap-tutorial-steps-proxies.xml"));
        process = startServiceProxyScript();
    }

    @Test
    void step1() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/city-service")
        .then()
            .statusCode(200)
            .contentType(HTML)
            .body(containsString("Copyright"));

        given()
        .when()
            .get("http://localhost:9000/admin")
        .then()
            .statusCode(200)
            .contentType(HTML)
            .body(containsString("Statistics"));
        // @formatter:on
    }

    @Test
    void step2() {
        // @formatter:off
        given()
        .when()
            .body(validSoapRequest)
            .contentType(APPLICATION_SOAP)
            .post("http://localhost:2001/soap-service")
        .then()
            .statusCode(200)
            .body(containsString("England"));
        // @formatter:on
    }

    @Test
    void step3() {
        // @formatter:off
        given()
        .when()
            .body(invalidSoapRequest)
            .contentType(APPLICATION_SOAP)
            .post("http://localhost:2001/soap-service")
        .then()
            .statusCode(200)
            .body(containsString("<faultcode>s11:Client</faultcode>"));
        // @formatter:on
    }

    @Test
    void step4() {
        // @formatter:off
        given()
        .when()
            .body(invalidSoapRequest)
            .contentType(APPLICATION_SOAP)
        .post("http://localhost:2002/soap-service")
            .then()
            .statusCode(200)
            .body(containsString("<faultstring>WSDL message validation failed</faultstring>"));
        // @formatter:on
    }

    String validSoapRequest = """
            <s11:Envelope xmlns:s11='http://schemas.xmlsoap.org/soap/envelope/'>
              <s11:Body>
                <cs:getCity xmlns:cs='https://predic8.de/cities'>
                  <name>London</name>
                </cs:getCity>
              </s11:Body>
            </s11:Envelope>
            """;

    String invalidSoapRequest = """
            <s11:Envelope xmlns:s11='http://schemas.xmlsoap.org/soap/envelope/'>
              <s11:Body>
                <cs:getCity xmlns:cs='https://predic8.de/cities'>
                  <foo>London</foo>
                </cs:getCity>
              </s11:Body>
            </s11:Envelope>
            """;
}