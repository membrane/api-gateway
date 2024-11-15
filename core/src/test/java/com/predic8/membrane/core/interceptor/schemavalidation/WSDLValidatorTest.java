package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.resolver.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;
import org.slf4j.*;

import java.net.*;

import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @TODO
 * WSDL without service
 */
public class WSDLValidatorTest {

    public static final String CITIES_WSDL = "src/test/resources/ws/cities.wsdl";
    public static final String TWO_SEPARATED_SERVICES_WSDL = "src/test/resources/ws/two-separated-services.wsdl";
    public static final String MULTIPLE_PORTS_WSDL = "src/test/resources/ws/multiple-ports-in-a-service.wsdl";

    static Logger log = LoggerFactory.getLogger(WSDLValidatorTest.class.getName());

    @BeforeEach
    void setUp() throws Exception {

    }

    @Test
    void invalidRequestElement() throws Exception {

        Exchange exc = getRequestExchange("""
                <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
                  <s11:Body>
                    <foo:notInSchema xmlns:foo="http://membrane-api.io/foo">
                  </s11:Body>
                </s11:Envelope>
                """);

        assertEquals(ABORT, createValidator(CITIES_WSDL, null,false).validateMessage(exc, exc.getRequest()));
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("validation failed"));
    }

    @Test
    void validRequestElement() throws Exception {

        Exchange exc = getRequestExchange("""
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cit="https://predic8.de/cities">
                  <s:Header/>
                  <s:Body>
                    <cit:getCity>
                      <name>Bonn</name>
                    </cit:getCity>
                  </s:Body>
                </s:Envelope>
                """);

        Outcome actual = createValidator(CITIES_WSDL, null, false).validateMessage(exc, exc.getRequest());
        dumpResonseBody(exc);
        assertEquals(CONTINUE, actual);
        assertNull(exc.getResponse());
    }

    @Test
    void twoServicesElementOfProperService() throws Exception {

        Exchange exc = getRequestExchange("""
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ns="https://predic8.de/">
                  <s:Body>
                    <ns:a>Paris</ns:a> <!-- Element is in the proper Service -->
                  </s:Body>
                </s:Envelope>
                """);
        
        assertEquals(CONTINUE, createValidator(TWO_SEPARATED_SERVICES_WSDL, "ServiceA", false)
                .validateMessage(exc, exc.getRequest()));
        assertNull(exc.getResponse());
    }

    @Test
    void twoServicesElementOfWrongService() throws Exception {

        Exchange exc = getRequestExchange("""
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ns="https://predic8.de/">
                  <s:Body>
                    <ns:a>Paris</ns:a>
                  </s:Body>
                </s:Envelope>
                """);

        assertEquals(ABORT, createValidator(TWO_SEPARATED_SERVICES_WSDL, "ServiceB", false).validateMessage(exc, exc.getRequest()));
        assertNotNull(exc.getResponse());
        String body = exc.getResponse().getBodyAsStringDecoded();
        assertTrue(body.contains("not a valid request element"));
        dumpResonseBody(exc);
    }

    @Test
    void validateFaultsAndFail() throws Exception {

        Exchange exc = getResponseExchange("""
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cit="https://predic8.de/cities">
                  <s:Header/>
                  <s:Body>
                    <s:Fault/>
                  </s:Body>
                </s:Envelope>
                """);

        assertEquals(ABORT, createValidator(CITIES_WSDL, null, false).validateMessage(exc, exc.getResponse()));
        dumpResonseBody(exc);
        assertNotNull(exc.getResponse());
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("validation failed"));
    }

    @Test
    void multiplePortsSoap11() throws Exception {

        Exchange exc = getRequestExchange("""
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ns="https://predic8.de/">
                  <s:Body>
                    <ns:a>Paris</ns:a>
                  </s:Body>
                </s:Envelope>
                """);

        exc.getRequest().setUri("/port-b-path");

        Outcome outcome = createValidator(MULTIPLE_PORTS_WSDL, "Service", false).validateMessage(exc, exc.getRequest());
        dumpResonseBody(exc);
        assertEquals(CONTINUE, outcome);
        assertNotNull(exc.getResponse());
        String body = exc.getResponse().getBodyAsStringDecoded();
        assertTrue(body.contains("not a valid request element"));
    }

    private static void dumpResonseBody( Exchange exc) {
        if (exc.getResponse() == null)
            return;
        System.out.println("Response:" + exc.getResponse().getBodyAsStringDecoded());
    }


    private static Exchange getRequestExchange(String body) throws URISyntaxException {
        return post("/foo").body(body).buildExchange();
    }

    private static Exchange getResponseExchange(String body) throws URISyntaxException {
        Exchange exc = new Exchange(null);
        exc.setResponse( Response.ok().body(body).build());
        return exc;
    }

    private static @NotNull WSDLValidator createValidator(String location, String serviceName, boolean skipFaults) throws Exception {
        WSDLValidator validator = new WSDLValidator(new ResolverMap(), location, serviceName, (msg, exc) -> log.info("Validation failure: " + msg), skipFaults);
        validator.init();
        return validator;
    }
}