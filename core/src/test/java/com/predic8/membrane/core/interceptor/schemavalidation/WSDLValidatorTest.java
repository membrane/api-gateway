/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.HeaderName;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.predic8.membrane.core.http.Header.VALIDATION_ERROR_SOURCE;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

public class WSDLValidatorTest {

    private static final Logger log = LoggerFactory.getLogger(WSDLValidatorTest.class.getName());

    public static final String CITIES_WSDL = "src/test/resources/ws/cities.wsdl";
    public static final String TWO_SEPARATED_SERVICES_WSDL = "src/test/resources/ws/two-separated-services.wsdl";
    public static final String MULTIPLE_PORTS_WSDL = "src/test/resources/ws/multiple-ports-in-a-service.wsdl";
    public static final String ABSTRACT_SERVICE_NO_BINDING_WSDL = "src/test/resources/ws/abstract-service-no-binding.wsdl";
    public static final String CITIES_WITH_FAULT_WSDL = "src/test/resources/ws/cities-with-fault.wsdl";
    public static final String HELLO_SOAP12_WSDL = "src/test/resources/ws/hello-soap12.wsdl";
    public static final String RPC_WITH_FAULT_WSDL = "src/test/resources/ws/rpc-with-fault.wsdl";
    public static final String TWO_FAULTS_PER_OPERATION_WSDL = "src/test/resources/ws/two-faults-per-operation.wsdl";

    private static final String WELL_FORMED_FAULT_11 = soap11("""
            <s11:Fault>
                <faultcode>Server</faultcode>
                <faultstring>City not found</faultstring>
            </s11:Fault>
            """);

    private static final String MALFORMED_FAULT_11 = soap11("""
            <s11:Fault>
                <faultcode>Server</faultcode>
            </s11:Fault>
            """);

    @Test
    void invalidRequestElement() throws Exception {
        var exc = getRequestExchange(soap11("""
                    <foo:notInSchema xmlns:foo="http://membrane-api.io/foo"/>
                """));

        assertEquals(ABORT, createValidator(CITIES_WSDL, null, false).validateMessage(exc, REQUEST));
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("validation failed"));
    }

    @Test
    void validRequestElement() throws Exception {

        Exchange exc = getRequestExchange(soap11("""
                <cit:getCity xmlns:cit="https://predic8.de/cities">
                    <name>Bonn</name>
                </cit:getCity>
                """));

        Outcome actual = createValidator(CITIES_WSDL, null, false).validateMessage(exc, REQUEST);
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

        Outcome actual = createValidator(CITIES_WSDL, null, false).validateMessage(exc, RESPONSE);
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

        Outcome actual = createValidator(CITIES_WSDL, null, false).validateMessage(exc, REQUEST);
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
                .validateMessage(exc, REQUEST);
        dumpResonseBody(exc);
        assertEquals(CONTINUE, outcome);
        assertNull(exc.getResponse());
    }

    @Test
    void twoServicesElementOfWrongService() throws Exception {
        var exc = getRequestExchange(soap11("""
                <ns:a xmlns:ns="https://predic8.de/">Paris</ns:a> <!-- Element is not referenced from Service B -->
                """));

        assertEquals(ABORT, createValidator(TWO_SEPARATED_SERVICES_WSDL, "ServiceB", false).validateMessage(exc, REQUEST));
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

        assertEquals(ABORT, createValidator(CITIES_WSDL, null, false).validateMessage(exc, RESPONSE));
        dumpResonseBody(exc);
        assertNotNull(exc.getResponse());
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("validation failed"));
    }

    @Test
    void malformedFault11() throws Exception {
        Exchange exc = getResponseExchange(soap11("""
                <s11:Fault>
                    <faultcode>Server</faultcode>
                </s11:Fault>
                """));

        assertEquals(ABORT, createValidator(CITIES_WSDL, null, false).validateMessage(exc, RESPONSE));
        dumpResonseBody(exc);
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("faultstring"));
    }

    @Test
    void malformedFault12() throws Exception {
        Exchange exc = getResponseExchange(soap12("""
                <s12:Fault xmlns:s12="http://www.w3.org/2003/05/soap-envelope">
                    <s12:Code><s12:Value>s12:Receiver</s12:Value></s12:Code>
                </s12:Fault>
                """));

        assertEquals(ABORT, createValidator(HELLO_SOAP12_WSDL, null, false).validateMessage(exc, RESPONSE));
        dumpResonseBody(exc);
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("Reason"));
    }

    @Test
    void wellFormedFault12() throws Exception {
        Exchange exc = getResponseExchange(soap12("""
                <s12:Fault xmlns:s12="http://www.w3.org/2003/05/soap-envelope">
                    <s12:Code><s12:Value>s12:Receiver</s12:Value></s12:Code>
                    <s12:Reason><s12:Text xml:lang="en">Something went wrong</s12:Text></s12:Reason>
                </s12:Fault>
                """));

        assertEquals(CONTINUE, createValidator(HELLO_SOAP12_WSDL, null, false).validateMessage(exc, RESPONSE));
        dumpResonseBody(exc);
    }

    @Test
    void fault12ReasonTextWithoutLanguage() throws Exception {
        Exchange exc = getResponseExchange(soap12("""
                <s12:Fault xmlns:s12="http://www.w3.org/2003/05/soap-envelope">
                    <s12:Code><s12:Value>s12:Receiver</s12:Value></s12:Code>
                    <s12:Reason><s12:Text>Something went wrong</s12:Text></s12:Reason>
                </s12:Fault>
                """));

        assertEquals(ABORT, createValidator(HELLO_SOAP12_WSDL, null, false).validateMessage(exc, RESPONSE));
        dumpResonseBody(exc);
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("validation failed"));
    }

    @Test
    void fault12InvalidTopLevelCode() throws Exception {
        Exchange exc = getResponseExchange(soap12("""
                <s12:Fault xmlns:s12="http://www.w3.org/2003/05/soap-envelope">
                    <s12:Code><s12:Value>s12:NotARealFaultCode</s12:Value></s12:Code>
                    <s12:Reason><s12:Text xml:lang="en">Something went wrong</s12:Text></s12:Reason>
                </s12:Fault>
                """));

        assertEquals(ABORT, createValidator(HELLO_SOAP12_WSDL, null, false).validateMessage(exc, RESPONSE));
        dumpResonseBody(exc);
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("validation failed"));
    }

    @Test
    void wellFormedFaultWithoutDetail() throws Exception {
        Exchange exc = getResponseExchange(soap11("""
                <s11:Fault>
                    <faultcode>Server</faultcode>
                    <faultstring>City not found</faultstring>
                </s11:Fault>
                """));

        assertEquals(CONTINUE, createValidator(CITIES_WSDL, null, false).validateMessage(exc, RESPONSE));
        dumpResonseBody(exc);
    }

    @Test
    void faultDetailNotDeclaredAsWsdlFault() throws Exception {
        Exchange exc = getResponseExchange(soap11("""
                <s11:Fault>
                    <faultcode>Server</faultcode>
                    <faultstring>City not found</faultstring>
                    <detail>
                        <cit:getCityResponse xmlns:cit="https://predic8.de/cities">
                            <country>France</country>
                            <population>2000000</population>
                        </cit:getCityResponse>
                    </detail>
                </s11:Fault>
                """));

        assertEquals(ABORT, createValidator(CITIES_WITH_FAULT_WSDL, null, false).validateMessage(exc, RESPONSE));
        dumpResonseBody(exc);
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("not a valid fault detail element"));
    }

    @Test
    void faultDetailMatchesWsdlFaultButFailsSchema() throws Exception {
        Exchange exc = getResponseExchange(soap11("""
                <s11:Fault>
                    <faultcode>Server</faultcode>
                    <faultstring>City not found</faultstring>
                    <detail>
                        <cit:cityNotFound xmlns:cit="https://predic8.de/cities">
                            <wrongElement>Springfield</wrongElement>
                        </cit:cityNotFound>
                    </detail>
                </s11:Fault>
                """));

        assertEquals(ABORT, createValidator(CITIES_WITH_FAULT_WSDL, null, false).validateMessage(exc, RESPONSE));
        dumpResonseBody(exc);
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("validation failed"));
    }

    @Test
    void faultDetailMatchesWsdlFaultAndSchema() throws Exception {
        Exchange exc = getResponseExchange(soap11("""
                <s11:Fault>
                    <faultcode>Server</faultcode>
                    <faultstring>City not found</faultstring>
                    <detail>
                        <cit:cityNotFound xmlns:cit="https://predic8.de/cities">
                            <name>Springfield</name>
                        </cit:cityNotFound>
                    </detail>
                </s11:Fault>
                """));

        assertEquals(CONTINUE, createValidator(CITIES_WITH_FAULT_WSDL, null, false).validateMessage(exc, RESPONSE));
        dumpResonseBody(exc);
    }

    /**
     * SOAP permits several detail entries, but a WSDL fault message has a single part, so a
     * described fault carries exactly one. Without an explicit check the second entry reaches the
     * validator as if it were a child of the first, which reports a misleading schema error.
     */
    @Test
    void faultDetailWithSeveralEntries() throws Exception {
        Exchange exc = getResponseExchange(soap11("""
                <s11:Fault>
                    <faultcode>Server</faultcode>
                    <faultstring>City not found</faultstring>
                    <detail>
                        <cit:cityNotFound xmlns:cit="https://predic8.de/cities"><name>Springfield</name></cit:cityNotFound>
                        <cit:cityNotFound xmlns:cit="https://predic8.de/cities"><name>Shelbyville</name></cit:cityNotFound>
                    </detail>
                </s11:Fault>
                """));

        assertEquals(ABORT, createValidator(CITIES_WITH_FAULT_WSDL, null, false).validateMessage(exc, RESPONSE));
        dumpResonseBody(exc);
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("must contain exactly one element"));
    }

    /**
     * An empty detail element carries no payload to validate.
     */
    @Test
    void faultWithEmptyDetail() throws Exception {
        Exchange exc = getResponseExchange(soap11("""
                <s11:Fault>
                    <faultcode>Server</faultcode>
                    <faultstring>City not found</faultstring>
                    <detail/>
                </s11:Fault>
                """));

        assertEquals(CONTINUE, createValidator(CITIES_WITH_FAULT_WSDL, null, false).validateMessage(exc, RESPONSE));
        dumpResonseBody(exc);
    }

    /**
     * Under an RPC binding the fault detail element must still be taken from the fault message's
     * part - deriving it like an RPC request/response wrapper would name it after the operation
     * and reject every legitimate fault.
     */
    @Test
    void faultDetailUnderRpcBinding() throws Exception {
        Exchange exc = getResponseExchange(soap11("""
                <s11:Fault>
                    <faultcode>Server</faultcode>
                    <faultstring>Division by zero</faultstring>
                    <detail>
                        <c:divByZero xmlns:c="http://example.com/calc">
                            <message>divisor was 0</message>
                        </c:divByZero>
                    </detail>
                </s11:Fault>
                """));

        assertEquals(CONTINUE, createValidator(RPC_WITH_FAULT_WSDL, null, false).validateMessage(exc, RESPONSE));
        dumpResonseBody(exc);
    }

    /**
     * A WSDL declares faults as service output only, so a fault arriving as a request describes
     * nothing the backend could handle and must not be forwarded.
     */
    @Test
    void faultAsRequest() throws Exception {
        Exchange exc = getRequestExchange(soap11("""
                <s11:Fault>
                    <faultcode>Server</faultcode>
                    <faultstring>City not found</faultstring>
                </s11:Fault>
                """));

        assertEquals(ABORT, createValidator(CITIES_WITH_FAULT_WSDL, null, false).validateMessage(exc, REQUEST));
        dumpResonseBody(exc);
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("A SOAP Fault is not a valid request"));
    }

    /**
     * skipFaults tolerates the faults a backend returns; it must not open the request direction.
     */
    @Test
    void faultAsRequestWithSkipFaults() throws Exception {
        Exchange exc = getRequestExchange(soap11("""
                <s11:Fault>
                    <faultcode>Server</faultcode>
                    <faultstring>City not found</faultstring>
                </s11:Fault>
                """));

        assertEquals(ABORT, createValidator(CITIES_WITH_FAULT_WSDL, null, true).validateMessage(exc, REQUEST));
        dumpResonseBody(exc);
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("A SOAP Fault is not a valid request"));
    }

    @Test
    void skipFaults() throws Exception {
        var exc = getResponseExchange(soap11("""
                <s11:Fault/>
                """));


        Outcome actual = createValidator(CITIES_WSDL, null, true).validateMessage(exc, RESPONSE);
        dumpResonseBody(exc);
        assertEquals(CONTINUE, actual);

    }

    @Test
    void multiplePortsSoap11() throws Exception {
        Exchange exc = getRequestExchange(soap11("""
                <ns:a xmlns:ns="https://predic8.de/">Paris</ns:a>
                """));

        Outcome outcome = createValidator(MULTIPLE_PORTS_WSDL, "Service", true).validateMessage(exc, REQUEST);
        dumpResonseBody(exc);
        assertEquals(CONTINUE, outcome);
    }

    @Test
    void abstractWsdl() throws Exception {
        var exc = getRequestExchange(soap11("""
                <ns:a xmlns:ns="https://predic8.de/">Paris</ns:a>
                """));

        exc.getRequest().setUri("/port-b-path");

        Outcome outcome = createValidator(ABSTRACT_SERVICE_NO_BINDING_WSDL, null, false).validateMessage(exc, REQUEST);
        dumpResonseBody(exc);
        assertEquals(CONTINUE, outcome);
    }

    @Test
    void abstractWsdlNoReferencedRequestElement() throws Exception {
        var exc = getRequestExchange(soap11("""
                <ns:b xmlns:ns="https://predic8.de/">7</ns:b> <!-- Declared in schema but not used as a SOAP message -->
                """));

        var outcome = createValidator(ABSTRACT_SERVICE_NO_BINDING_WSDL, null, false).validateMessage(exc, REQUEST);
        dumpResonseBody(exc);
        assertEquals(ABORT, outcome);
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("is not a valid request element"));
    }

    /**
     * An operation may declare several faults. Each of them is a valid fault detail, and each is
     * validated against its own declaration - matching one declared element must not let another
     * element's content through.
     */
    @Test
    void firstOfTwoDeclaredFaults() throws Exception {
        Exchange exc = getResponseExchange(soap11("""
                <s11:Fault>
                    <faultcode>Server</faultcode>
                    <faultstring>City not found</faultstring>
                    <detail>
                        <cit:cityNotFound xmlns:cit="https://predic8.de/cities">
                            <name>Springfield</name>
                        </cit:cityNotFound>
                    </detail>
                </s11:Fault>
                """));

        assertEquals(CONTINUE, createValidator(TWO_FAULTS_PER_OPERATION_WSDL, null, false).validateMessage(exc, RESPONSE));
        dumpResonseBody(exc);
    }

    @Test
    void secondOfTwoDeclaredFaults() throws Exception {
        Exchange exc = getResponseExchange(soap11("""
                <s11:Fault>
                    <faultcode>Server</faultcode>
                    <faultstring>Too many requests</faultstring>
                    <detail>
                        <cit:rateLimited xmlns:cit="https://predic8.de/cities">
                            <retryAfter>30</retryAfter>
                        </cit:rateLimited>
                    </detail>
                </s11:Fault>
                """));

        assertEquals(CONTINUE, createValidator(TWO_FAULTS_PER_OPERATION_WSDL, null, false).validateMessage(exc, RESPONSE));
        dumpResonseBody(exc);
    }

    /**
     * The detail element is declared, but carries the content model of the operation's other
     * fault - so membership alone must not be enough to pass.
     */
    @Test
    void declaredFaultElementWithTheOtherFaultsContent() throws Exception {
        Exchange exc = getResponseExchange(soap11("""
                <s11:Fault>
                    <faultcode>Server</faultcode>
                    <faultstring>City not found</faultstring>
                    <detail>
                        <cit:cityNotFound xmlns:cit="https://predic8.de/cities">
                            <retryAfter>30</retryAfter>
                        </cit:cityNotFound>
                    </detail>
                </s11:Fault>
                """));

        assertEquals(ABORT, createValidator(TWO_FAULTS_PER_OPERATION_WSDL, null, false).validateMessage(exc, RESPONSE));
        dumpResonseBody(exc);
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("retryAfter"));
    }

    /**
     * A fault the validator could never check must fail at startup, not silently disable fault
     * validation for a route that looks configured for it.
     */
    @Test
    void wsdlWithUnvalidatableFaultIsRejectedAtStartup() {
        var e = assertThrows(ConfigurationException.class,
                () -> createValidator("src/test/resources/ws/fault-part-with-type.wsdl", null, false));
        assertTrue(e.getMessage().contains("names a type instead of an element"), e.getMessage());
    }

    /**
     * Every rejection - not just a schema mismatch - must mark the response with the flow it was
     * rejected in, notify the configured failure handler and count as invalid.
     */
    @Test
    void rejectionReportsFlowAndNotifiesFailureHandler() throws Exception {
        var handled = new ArrayList<String>();
        var validator = new WSDLValidator(new ResolverMap(), CITIES_WSDL, null, (msg, exc) -> handled.add(msg), false);
        validator.init();

        Exchange exc = getRequestExchange(soap11("""
                <foo:notInWsdl xmlns:foo="http://membrane-api.io/foo"/>
                """));

        assertEquals(ABORT, validator.validateMessage(exc, REQUEST));
        assertEquals(List.of("REQUEST"), exc.getResponse().getHeader().getValues(new HeaderName(VALIDATION_ERROR_SOURCE))
                .stream().map(HeaderField::getValue).toList());
        assertEquals(1, handled.size(), handled.toString());
        assertTrue(handled.getFirst().contains("not a valid request element"), handled.toString());
        assertEquals(1, validator.getInvalid());
    }

    /**
     * Fault structure validators come from a pool and are reused, so a failed validation must not
     * leave error state behind that makes the next - valid - fault fail too.
     */
    @Test
    void faultValidatorsAreReusedAcrossCalls() throws Exception {
        var validator = createValidator(CITIES_WSDL, null, false);

        assertEquals(CONTINUE, validator.validateMessage(getResponseExchange(WELL_FORMED_FAULT_11), RESPONSE));
        assertEquals(CONTINUE, validator.validateMessage(getResponseExchange(WELL_FORMED_FAULT_11), RESPONSE));
        assertEquals(ABORT, validator.validateMessage(getResponseExchange(MALFORMED_FAULT_11), RESPONSE));
        assertEquals(CONTINUE, validator.validateMessage(getResponseExchange(WELL_FORMED_FAULT_11), RESPONSE));
    }

    /**
     * More concurrent faults than the pool holds: every message must get its own validator, and
     * the abort path must return its validator to the pool like the success path does.
     */
    @Test
    void faultValidationIsConcurrencySafe() throws Exception {
        var validator = createValidator(CITIES_WSDL, null, false);
        int tasks = Runtime.getRuntime().availableProcessors() * 2 + 4;

        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<Future<Outcome>>();
            for (int i = 0; i < tasks; i++) {
                boolean wellFormed = i % 2 == 0;
                futures.add(executor.submit(() -> validator.validateMessage(
                        getResponseExchange(wellFormed ? WELL_FORMED_FAULT_11 : MALFORMED_FAULT_11), RESPONSE)));
            }
            for (int i = 0; i < tasks; i++) {
                assertEquals(i % 2 == 0 ? CONTINUE : ABORT, futures.get(i).get(30, SECONDS), "task " + i);
            }
        }
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

    private static WSDLValidator createValidator(String location, String serviceName, boolean skipFaults) {
        var validator = new WSDLValidator(new ResolverMap(), location, serviceName, (msg, exc) -> log.info("Validation failure: {}", msg), skipFaults);
        validator.init();
        return validator;
    }
}