package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.resolver.*;
import org.junit.jupiter.api.*;
import org.slf4j.*;

import java.net.*;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @TODO WSDL without service
 */
public class WSDLValidatorTest {

    public static final String CITIES_WSDL = "src/test/resources/ws/cities.wsdl";
    public static final String TWO_SEPARATED_SERVICES_WSDL = "src/test/resources/ws/two-separated-services.wsdl";
    public static final String MULTIPLE_PORTS_WSDL = "src/test/resources/ws/multiple-ports-in-a-service.wsdl";
    public static final String ABSTRACT_SERVICE_NO_BINDING_WSDL = "src/test/resources/ws/abstract-service-no-binding.wsdl";

    static Logger log = LoggerFactory.getLogger(WSDLValidatorTest.class.getName());

    @Test
    void invalidRequestElement() throws Exception {
        Exchange exc = getRequestExchange(soap11("""
                    <foo:notInSchema xmlns:foo="http://membrane-api.io/foo"/>
                """));

        assertEquals(ABORT, createValidator(CITIES_WSDL, null, false).validateMessage(exc, exc.getRequest()));
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("validation failed"));
    }

    @Test
    void validRequestElement() throws Exception {

        Exchange exc = getRequestExchange(soap11("""
                <cit:getCity xmlns:cit="https://predic8.de/cities">
                    <name>Bonn</name>
                </cit:getCity>
                """));

        Outcome actual = createValidator(CITIES_WSDL, null, false).validateMessage(exc, exc.getRequest());
        dumpResonseBody(exc);
        assertEquals(CONTINUE, actual);
        assertNull(exc.getResponse());
    }

    @Test
    void validResponseElement() throws Exception {
        Exchange exc = getResponseExchange(soap11("""
                <cit:getCityResponse xmlns:cit="https://predic8.de/cities">
                    <country>France</country>
                    <population>2000000</population>
                </cit:getCityResponse>
                """));

        Outcome actual = createValidator(CITIES_WSDL, null, false).validateMessage(exc, exc.getResponse());
        dumpResonseBody(exc);
        assertEquals(CONTINUE, actual);
    }

    @Test
    void wrongSoapVersion() throws Exception {

        Exchange exc = getRequestExchange(soap12("""
                <cit:getCity xmlns:cit="https://predic8.de/cities">
                    <name>Bonn</name>
                </cit:getCity>
                """));

        Outcome actual = createValidator(CITIES_WSDL, null, false).validateMessage(exc, exc.getRequest());
        dumpResonseBody(exc);
        assertEquals(ABORT, actual);
        assertNotNull(exc.getResponse());
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("SOAP version 1.2 is not valid"));
    }

    @Test
    void twoServicesElementOfProperService() throws Exception {

        Exchange exc = getRequestExchange( soap11("""
                <ns:a xmlns:ns="https://predic8.de/">Paris</ns:a> <!-- Element is in the proper Service -->
                """));

        Outcome outcome = createValidator(TWO_SEPARATED_SERVICES_WSDL, "ServiceA", false)
                .validateMessage(exc, exc.getRequest());
        dumpResonseBody(exc);
        assertEquals(CONTINUE, outcome);
        assertNull(exc.getResponse());
    }

    @Test
    void twoServicesElementOfWrongService() throws Exception {

        Exchange exc = getRequestExchange(soap11("""
                <ns:a xmlns:ns="https://predic8.de/">Paris</ns:a> <!-- Element is not referenced from Service B -->
                """));

        assertEquals(ABORT, createValidator(TWO_SEPARATED_SERVICES_WSDL, "ServiceB", false).validateMessage(exc, exc.getRequest()));
        assertNotNull(exc.getResponse());
        String body = exc.getResponse().getBodyAsStringDecoded();
        assertTrue(body.contains("not a valid request element"));
        dumpResonseBody(exc);
    }

    @Test
    void validateFaultsAndFail() throws Exception {
        Exchange exc = getResponseExchange(soap11("""
                <s11:Fault/>
                """));

        assertEquals(ABORT, createValidator(CITIES_WSDL, null, false).validateMessage(exc, exc.getResponse()));
        dumpResonseBody(exc);
        assertNotNull(exc.getResponse());
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("validation failed"));
    }

    @Test
    void skipFaults() throws Exception {
        Exchange exc = getResponseExchange(soap11("""
                <s11:Fault/>
                """));

        assertEquals(CONTINUE, createValidator(CITIES_WSDL, null, true).validateMessage(exc, exc.getResponse()));
        dumpResonseBody(exc);
    }

    @Test
    void multiplePortsSoap11() throws Exception {
        Exchange exc = getRequestExchange(soap11("""
                <ns:a xmlns:ns="https://predic8.de/">Paris</ns:a>
                """));

        Outcome outcome = createValidator(MULTIPLE_PORTS_WSDL, "Service", false).validateMessage(exc, exc.getRequest());
        dumpResonseBody(exc);
        assertEquals(CONTINUE, outcome);
    }

    @Test
    void abstractWsdl() throws Exception {
        Exchange exc = getRequestExchange(soap11("""
                <ns:a xmlns:ns="https://predic8.de/">Paris</ns:a>
                """));

        exc.getRequest().setUri("/port-b-path");

        Outcome outcome = createValidator(ABSTRACT_SERVICE_NO_BINDING_WSDL, null, false).validateMessage(exc, exc.getRequest());
        dumpResonseBody(exc);
        assertEquals(CONTINUE, outcome);
    }

    @Test
    void abstractWsdlNoReferencedRequestElement() throws Exception {

        Exchange exc = getRequestExchange(soap11("""
                <ns:b xmlns:ns="https://predic8.de/">7</ns:b> <!-- Declared in schema but not used as a SOAP message -->
                """));

        Outcome outcome = createValidator(ABSTRACT_SERVICE_NO_BINDING_WSDL, null, false).validateMessage(exc, exc.getRequest());
        dumpResonseBody(exc);
        assertEquals(ABORT, outcome);
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("is not a valid request element"));
    }

    private static Exchange getRequestExchange(String body) throws URISyntaxException {
        return Request.post("/foo").body(body).contentType(TEXT_XML).buildExchange();
    }

    private static String soap11(String body) {
        return """
               <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
                  <s11:Header><ns2:Block xmlns:ns2="http://membrane-api.io"/></s11:Header>
                  <s11:Body>%s</s11:Body>
               </s11:Envelope>
               """.formatted(body);
    }

    private static String soap12(String body) {
        return """
               <s12:Envelope xmlns:s12="http://www.w3.org/2003/05/soap-envelope">
                  <s12:Body>%s</s12:Body>
               </s12:Envelope>
               """.formatted(body);
    }

    private static void dumpResonseBody(Exchange exc) {
        if (exc.getResponse() == null)
            return;
        System.out.println("Response:" + exc.getResponse().getBodyAsStringDecoded());
    }

    private static Exchange getResponseExchange(String body) {
        Exchange exc = new Exchange(null);
        exc.setResponse(Response.ok().body(body).contentType(TEXT_XML).build());
        return exc;
    }

    private static WSDLValidator createValidator(String location, String serviceName, boolean skipFaults) throws Exception {
        WSDLValidator validator = new WSDLValidator(new ResolverMap(), location, serviceName, (msg, exc) -> log.info("Validation failure: " + msg), skipFaults);
        validator.init();
        return validator;
    }
}