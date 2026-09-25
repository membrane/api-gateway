/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.wsdl2openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.predic8.membrane.core.http.Header.CONTENT_LENGTH;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.Wsdl2OpenApiConverter.ApiInfo;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.camelToKebab;
import static com.predic8.membrane.test.TestUtil.getPathFromResource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class Wsdl2OpenapiInterceptorTest {

    @Test
    void queryParametersAreMergedIntoTheJsonBody() throws Exception {
        assertEquals("{\"status\":\"active\",\"id\":\"42\"}",
                Wsdl2OpenapiInterceptor.mergeUrlParamsIntoJson(null, Map.of("status", "active"), Map.of("id", "42")));
    }

    @Test
    void pathParameterWinsOverAQueryParameterOfTheSameName() throws Exception {
        assertEquals("{\"id\":\"42\"}",
                Wsdl2OpenapiInterceptor.mergeUrlParamsIntoJson("{\"id\":\"7\"}", Map.of("id", "13"), Map.of("id", "42")));
    }

    @Test
    void bodyIsUntouchedWithoutUrlParameters() throws Exception {
        assertEquals("{\"id\":\"7\"}",
                Wsdl2OpenapiInterceptor.mergeUrlParamsIntoJson("{\"id\":\"7\"}", Map.of(), Map.of()));
    }

    @Test
    void queryParameterNamesAreCollectedPerOperation() throws Exception {
        var definitions = Definitions.parse(new ResolverMap(), "classpath:/ws/extended-types.wsdl");
        var settings = new OperationSettings();
        settings.setMethod("GET");
        settings.setPath("/search/{byId}");
        var api = new Wsdl2OpenApiConverter(definitions, "/", Map.of("search", settings), ApiInfo.NONE).generate();

        assertEquals(Map.of("search", Set.of("byName", "code")),
                Wsdl2OpenapiInterceptor.collectQueryParamNames(api));
    }

    @Test
    void declaredQueryParameterReachesTheSoapRequestAndAnUndeclaredOneDoesNot() throws Exception {
        // A GET has no body, so the operation's input fields travel as query parameters. Anything
        // the client appends beyond them must not be forwarded to the service.
        var settings = new OperationSettings();
        settings.setMethod("GET");
        settings.setPath("/cities");
        var operations = new OperationsConfig();
        operations.setEntry(Map.of("getCity", settings));

        var interceptor = wsdl2openapi("classpath:/ws/cities.wsdl");
        interceptor.setOperations(operations);
        interceptor.init(new DummyTestRouter(), apiProxyWith(interceptor));

        var exc = new Exchange(null);
        exc.setRequest(new Request.Builder().get("/cities?name=Berlin&bogus=Atlantis").build());

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exc));
        String soap = exc.getRequest().getBodyAsStringDecoded();
        assertTrue(soap.contains("Berlin"), "the declared query parameter must reach the service: " + soap);
        assertFalse(soap.contains("bogus"), "an undeclared query parameter must not: " + soap);
        assertFalse(soap.contains("Atlantis"), "an undeclared query parameter's value must not: " + soap);
    }

    @Test
    void aDuplicateQueryParameterIsRejectedWith400() throws Exception {
        // A duplicate key is a client mistake, so it must not surface as a transformation failure.
        var settings = new OperationSettings();
        settings.setMethod("GET");
        settings.setPath("/cities");
        var operations = new OperationsConfig();
        operations.setEntry(Map.of("getCity", settings));

        var interceptor = wsdl2openapi("classpath:/ws/cities.wsdl");
        interceptor.setOperations(operations);
        interceptor.init(new DummyTestRouter(), apiProxyWith(interceptor));

        var exc = new Exchange(null);
        exc.setRequest(new Request.Builder().get("/cities?name=Berlin&name=Bonn").build());

        assertEquals(Outcome.ABORT, interceptor.handleRequest(exc));
        assertEquals(400, exc.getResponse().getStatusCode());
    }

    @Test
    void urlParametersAreMappedBackToTheXsdAttributesTheyStandFor() throws Exception {
        // record's input declares the attributes id and type. Both are published without the "@"
        // the JSON needs, so the interceptor has to put it back before the SOAP transformation —
        // one of them through the path, the other through the query string.
        var settings = new OperationSettings();
        settings.setMethod("GET");
        settings.setPath("/records/{id}");
        var operations = new OperationsConfig();
        operations.setEntry(Map.of("record", settings));

        var interceptor = wsdl2openapi("classpath:/ws/attributes.wsdl");
        interceptor.setOperations(operations);
        interceptor.init(new DummyTestRouter(), apiProxyWith(interceptor));

        var exc = new Exchange(null);
        exc.setRequest(new Request.Builder().get("/records/42?type=partner&name=Alice").build());

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exc));
        String soap = exc.getRequest().getBodyAsStringDecoded();
        assertTrue(soap.contains("id=\"42\""), "the path parameter must become the id attribute: " + soap);
        assertTrue(soap.contains("type=\"partner\""), "the query parameter must become the type attribute: " + soap);
        assertTrue(soap.contains("<name>Alice</name>"), "an element stays an element: " + soap);
    }

    @Test
    void aResponseFieldOfANamedTypeIsStillTypedAfterTheSoapConversion() throws Exception {
        var interceptor = wsdl2openapi("classpath:/ws/cross-namespace.wsdl");
        interceptor.init(new DummyTestRouter(), apiProxyWith(interceptor));

        var exc = new Exchange(null);
        exc.setProperty(operationPropertyKey(interceptor), "getItem");
        exc.setResponse(Response.ok("""
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <getItemResponse xmlns="https://example.com/service">
                      <result>
                        <itemName>Chair</itemName>
                        <itemCount>3</itemCount>
                      </result>
                    </getItemResponse>
                  </soap:Body>
                </soap:Envelope>
                """).build());

        assertEquals(Outcome.CONTINUE, interceptor.handleResponse(exc));

        // ItemType is published as a component and referenced from the response schema, so typing
        // the value depends on the runtime resolving the very names the document declares.
        var result = new ObjectMapper().readTree(exc.getResponse().getBodyAsStringDecoded()).get("result");
        assertTrue(result.get("itemCount").isNumber(), "xsd:int must not arrive as a string: " + result);
        assertEquals("Chair", result.get("itemName").asText());
    }

    @Test
    void anOutputMessageWithNoPartsStillReturnsABody() throws Exception {
        var interceptor = wsdl2openapi("classpath:/special/empty-message.wsdl");
        interceptor.init(new DummyTestRouter(), apiProxyWith(interceptor));

        var exc = new Exchange(null);
        exc.setProperty(operationPropertyKey(interceptor), "ping");
        exc.setResponse(Response.ok("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body><pingResponse/></soap:Body>
                </soap:Envelope>
                """).build());

        assertEquals(Outcome.CONTINUE, interceptor.handleResponse(exc));
        // The WSDL declares an output message for "ping", even though it carries no parts: the
        // service still sends an envelope, which may turn out to hold a fault instead. That is
        // reason enough not to collapse it to 204 the way a truly one-way operation is.
        assertEquals(200, exc.getResponse().getStatusCode());
        assertTrue(exc.getResponse().getHeader().getContentType().startsWith(APPLICATION_JSON));
        assertTrue(new ObjectMapper().readTree(exc.getResponse().getBodyAsStringDecoded()).isObject());
    }

    @Test
    void anOutputPartWithoutFieldsStillReturnsABody() throws Exception {
        var interceptor = wsdl2openapi("classpath:/ws/scalar-output.wsdl");
        interceptor.init(new DummyTestRouter(), apiProxyWith(interceptor));

        var exc = new Exchange(null);
        exc.setProperty(operationPropertyKey(interceptor), "refresh");
        exc.setResponse(Response.ok("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body><refreshResponse xmlns="https://example.com/scalar-output"/></soap:Body>
                </soap:Envelope>
                """).build());

        assertEquals(Outcome.CONTINUE, interceptor.handleResponse(exc));
        // The output message has a part, so the service does send a response element — an empty
        // complex type is an empty JSON object, not "no content".
        assertEquals(200, exc.getResponse().getStatusCode());
        assertTrue(exc.getResponse().getHeader().getContentType().startsWith(APPLICATION_JSON));
        assertTrue(new ObjectMapper().readTree(exc.getResponse().getBodyAsStringDecoded()).isObject());
    }

    @Test
    void aScalarOutputPartReturnsTheScalarValueNotAnEmptyObject() throws Exception {
        // https://github.com/membrane/api-gateway/issues/3280 — getNameResponse is declared as
        // plain xsd:string, so the published OpenAPI schema for this operation is `type: string`.
        // The runtime response must honor that: a bare JSON string, not "{}".
        var interceptor = wsdl2openapi("classpath:/ws/scalar-output.wsdl");
        interceptor.init(new DummyTestRouter(), apiProxyWith(interceptor));

        var exc = new Exchange(null);
        exc.setProperty(operationPropertyKey(interceptor), "getName");
        exc.setResponse(Response.ok("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body><getNameResponse xmlns="https://example.com/scalar-output">Alice</getNameResponse></soap:Body>
                </soap:Envelope>
                """).build());

        assertEquals(Outcome.CONTINUE, interceptor.handleResponse(exc));
        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals("\"Alice\"", exc.getResponse().getBodyAsStringDecoded().trim(),
                "a root element with no children must be emitted as its scalar text, not an empty object");
    }

    @Test
    void twoInstancesInOneFlowAreRejected() {
        var router = new DummyTestRouter();
        var proxy = apiProxyWith(wsdl2openapi("classpath:/ws/cities.wsdl"), wsdl2openapi("classpath:/blz-service.wsdl"));
        var first = (Wsdl2OpenapiInterceptor) proxy.getFlow().getFirst();
        var second = (Wsdl2OpenapiInterceptor) proxy.getFlow().getLast();

        var e = assertThrows(ConfigurationException.class, () -> first.init(router, proxy));
        assertTrue(e.getMessage().contains("wsdl2openapi"), "Message should name the plugin");
        assertTrue(e.getMessage().contains("TestAPI"), "Message should name the offending API");

        // The whole flow is populated before any interceptor inits, so the rejection must not
        // depend on which of the two initialises first.
        assertThrows(ConfigurationException.class, () -> second.init(router, proxy));
    }

    @Test
    void singleInstanceInitsAndRegistersRoutes() {
        var router = new DummyTestRouter();
        var proxy = apiProxyWith(wsdl2openapi("classpath:/ws/cities.wsdl"));
        var interceptor = (Wsdl2OpenapiInterceptor) proxy.getFlow().getFirst();

        interceptor.init(router, proxy);

        assertEquals(List.of("POST"), interceptor.getOperationRouter().allowedMethods("/get-city"));
    }

    @Test
    void proxyThatIsNotAnApiIsRejected() {
        var router = new DummyTestRouter();
        var interceptor = wsdl2openapi("classpath:/ws/cities.wsdl");
        var proxy = new ServiceProxy(new ServiceProxyKey(2000), "localhost", 2001);
        proxy.setName("TestServiceProxy");
        proxy.setPath(new Path(false, "/purchasing"));
        proxy.getFlow().add(interceptor);

        var e = assertThrows(ConfigurationException.class, () -> interceptor.init(router, proxy));
        assertTrue(e.getMessage().contains("wsdl2openapi"), "Message should name the plugin");
        assertTrue(e.getMessage().contains("api"), "Message should say an api is required");
    }

    @Test
    void combiningWithOpenapiDocumentsIsRejected() {
        var router = new DummyTestRouter();
        var proxy = apiProxyWith(wsdl2openapi("classpath:/ws/cities.wsdl"));
        var spec = new OpenAPISpec();
        spec.location = getPathFromResource("openapi/openapi-proxy/no-extensions.yml");
        proxy.setOpenapi(List.of(spec));

        // APIProxy.init() adds the OpenAPIPublisherInterceptor for the spec and then inits the
        // flow, so the plugin sees the conflict; both would publish at /api-docs.
        var e = assertThrows(ConfigurationException.class, () -> proxy.init(router));
        assertTrue(e.getMessage().contains("wsdl2openapi"), "Message should name the plugin");
        assertTrue(e.getMessage().contains("/api-docs"), "Message should name the conflicting path");
    }

    @Test
    void wsdlWithUnresolvableImportIsRejected() {
        var router = new DummyTestRouter();
        var proxy = apiProxyWith(wsdl2openapi("classpath:/ws/missing-import.wsdl"));
        var interceptor = (Wsdl2OpenapiInterceptor) proxy.getFlow().getFirst();

        // An unresolved schemaLocation leaves the element set incomplete, so no OpenAPI can be
        // generated from it with any confidence.
        var e = assertThrows(ConfigurationException.class, () -> interceptor.init(router, proxy));
        assertTrue(e.getMessage().contains("does-not-exist.xsd"), e.getMessage());
        assertNotNull(e.getCause());
    }

    @Test
    void reInitDoesNotAccumulateRoutes() {
        var router = new DummyTestRouter();
        var proxy = apiProxyWith(wsdl2openapi("classpath:/ws/cities.wsdl"));
        var interceptor = (Wsdl2OpenapiInterceptor) proxy.getFlow().getFirst();

        // AbstractProxy.clone() and RuleManager.replaceRule both call init on the same
        // interceptor instance, so init must be repeatable without piling up state.
        interceptor.init(router, proxy);
        int routesAfterFirstInit = routeCount(interceptor);
        interceptor.init(router, proxy);

        assertEquals(routesAfterFirstInit, routeCount(interceptor), "Routes must not be registered twice");
        assertTrue(interceptor.getOperationRouter().match("/get-city", "POST").isPresent(),
                "Route must still match after re-init");
    }

    @Test
    void reInitRebuildsTheOperationRuntimes() throws Exception {
        var router = new DummyTestRouter();
        var proxy = apiProxyWith(wsdl2openapi("classpath:/ws/cities.wsdl"));
        var interceptor = (Wsdl2OpenapiInterceptor) proxy.getFlow().getFirst();

        interceptor.init(router, proxy);
        interceptor.init(router, proxy);

        // init replaces the runtimes wholesale, so the second one must leave a usable transformer
        // and response schema behind — not an empty map that fails every request after a reload.
        var exc = new Exchange(null);
        exc.setRequest(new Request.Builder().post("/get-city").body("{\"name\":\"Bonn\"}").build());
        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exc));
        assertTrue(exc.getRequest().getBodyAsStringDecoded().contains("Bonn"),
                "the request transformer must still convert after a re-init: " + exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void titleComesFromTheEnclosingApiNameAndDescriptionIsApplied() throws Exception {
        var router = new DummyTestRouter();
        var interceptor = wsdl2openapi("classpath:/ws/cities.wsdl");
        interceptor.setDescription("Custom description.");
        var proxy = apiProxyWith(interceptor); // apiProxyWith names the proxy "TestAPI"

        interceptor.init(router, proxy);

        var info = generatedOpenApi(interceptor).getInfo();
        assertEquals("TestAPI", info.getTitle(), "the OpenAPI title must be the enclosing api's name");
        assertTrue(info.getDescription().startsWith("Custom description."),
                "the configured description must appear at the start of info.description");
    }

    /** A SOAP 1.1 fault carrying the cityNotFound detail that cities-with-fault.wsdl declares. */
    private static final String CITY_NOT_FOUND_FAULT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <soap:Fault>
                  <faultcode>soap:Client</faultcode>
                  <faultstring>City not found</faultstring>
                  <detail>
                    <cityNotFound xmlns="https://predic8.de/cities">
                      <name>Atlantis</name>
                    </cityNotFound>
                  </detail>
                </soap:Fault>
              </soap:Body>
            </soap:Envelope>
            """;

    /** A successful getCity response as cities-with-fault.wsdl declares it. */
    private static final String GET_CITY_RESPONSE = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body><getCityResponse xmlns="https://predic8.de/cities">
                <country>Germany</country><population>123</population>
              </getCityResponse></soap:Body>
            </soap:Envelope>
            """;

    @Test
    void declaredFaultBecomesProblemDetailsWithDetailsMember() throws Exception {
        var response = faultResponse(false);

        assertEquals(500, response.getStatusCode());
        assertEquals("application/problem+json", response.getHeader().getContentType());

        var body = new ObjectMapper().readTree(response.getBodyAsStringDecoded());
        assertEquals("Operation failed", body.get("title").asText(),
                "the title is fixed: the backend's faultstring may name internals");
        assertEquals(500, body.get("status").asInt());
        assertEquals("Atlantis", body.at("/details/cityNotFound/name").asText(),
                "the declared fault's content appears under details, keyed by the fault element name");
    }

    @ParameterizedTest
    @MethodSource("successStatuses")
    void successStatusIsPublishedInTheDocument(Integer status) throws Exception {
        var responses = generatedOpenApi(interceptorWithStatus(status))
                .getPaths().get("/get-city").getPost().getResponses();

        assertNotNull(responses.get(Integer.toString(expectedStatus(status)))
                .getContent().get("application/json").getSchema());
        assertEquals(2, responses.size());
        assertNotNull(responses.getDefault(), "the configured status and default are the only two responses");
    }

    @ParameterizedTest
    @MethodSource("successStatuses")
    void successStatusIsReturnedOnTheWire(Integer status) throws Exception {
        var interceptor = interceptorWithStatus(status);
        var exc = getCityExchange(interceptor, GET_CITY_RESPONSE);

        assertEquals(Outcome.CONTINUE, interceptor.handleResponse(exc));
        assertEquals(expectedStatus(status), exc.getResponse().getStatusCode());
        assertEquals(expectedStatus(status) == 201 ? "Created" : "OK", exc.getResponse().getStatusMessage());
        assertEquals(123, new ObjectMapper().readTree(exc.getResponse().getBodyAsStringDecoded()).get("population").asInt());
    }

    @ParameterizedTest
    @MethodSource("faultResponsesForConfiguredStatus")
    void configuredStatusDoesNotApplyToFaultsOrConversionErrors(Integer status, String soap) throws Exception {
        var interceptor = interceptorWithStatus(status);
        var exc = getCityExchange(interceptor, soap);

        assertEquals(Outcome.ABORT, interceptor.handleResponse(exc));
        assertEquals(500, exc.getResponse().getStatusCode());
    }

    static Stream<Arguments> successStatuses() {
        return Stream.of(arguments((Integer) null), arguments(200), arguments(201));
    }

    static Stream<Arguments> faultResponsesForConfiguredStatus() {
        return Stream.<Integer>of(null, 200, 201).flatMap(status ->
                Stream.of(CITY_NOT_FOUND_FAULT, "invalid XML").map(soap -> arguments(status, soap)));
    }

    private static int expectedStatus(Integer configured) {
        return configured == null ? 200 : configured;
    }

    /** The interceptor for cities-with-fault.wsdl, with getCity's status configured unless {@code null}. */
    private static Wsdl2OpenapiInterceptor interceptorWithStatus(Integer status) {
        var interceptor = wsdl2openapi("classpath:/ws/cities-with-fault.wsdl");
        if (status != null) {
            var settings = new OperationSettings();
            settings.setStatus(status);
            var operations = new OperationsConfig();
            operations.setEntry(Map.of("getCity", settings));
            interceptor.setOperations(operations);
        }
        interceptor.init(new DummyTestRouter(), apiProxyWith(interceptor));
        return interceptor;
    }

    /** Runs the request leg so the operation property is set, then stages {@code soap} as the backend's answer. */
    private static Exchange getCityExchange(Wsdl2OpenapiInterceptor interceptor, String soap) throws Exception {
        var exc = new Exchange(null);
        exc.setRequest(new Request.Builder().post("/get-city").body("{\"name\":\"Bonn\"}").build());
        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exc));
        exc.setResponse(Response.ok().body(soap).build());
        return exc;
    }

    @Test
    void oneWayOperationIsAnsweredWithoutABody() throws Exception {
        var interceptor = oneWayInterceptor(null);
        var exc = sendMessageExchange(interceptor, Response.ok().build());

        assertEquals(Outcome.CONTINUE, interceptor.handleResponse(exc));
        assertEquals(204, exc.getResponse().getStatusCode());
        assertEquals("No Content", exc.getResponse().getStatusMessage());
        assertEquals(0, exc.getResponse().getBody().getLength());
        // Not decoration: Response.ok() already sets Content-Length: 0, so Message.emptyBody() would
        // take its isBodyEmpty() early return and leave these headers in place. The 202 case below
        // passes either way — that early return happens to leave the Content-Length a 202 needs —
        // so this is the only assertion that tells the two implementations apart.
        assertNull(exc.getResponse().getHeader().getFirstValue(CONTENT_LENGTH),
                "a 204 must not carry Content-Length");
        assertNull(exc.getResponse().getHeader().getContentType());
    }

    @Test
    void oneWayFaultIsNotSwallowed() throws Exception {
        var interceptor = oneWayInterceptor(null);
        // The WSDL declares no fault for sendMessage: detection is by the element name, not by a
        // declaration, so a fault must still abort rather than pass as an accepted one-way call.
        var exc = sendMessageExchange(interceptor, Response.ok().body(CITY_NOT_FOUND_FAULT).build());

        assertEquals(Outcome.ABORT, interceptor.handleResponse(exc));
        assertEquals(500, exc.getResponse().getStatusCode());
    }

    @Test
    void oneWayBodyFromTheServiceIsDiscarded() throws Exception {
        var interceptor = oneWayInterceptor(null);
        var exc = sendMessageExchange(interceptor, Response.ok().body(GET_CITY_RESPONSE).build());

        assertEquals(Outcome.CONTINUE, interceptor.handleResponse(exc));
        assertEquals(204, exc.getResponse().getStatusCode());
        assertEquals(0, exc.getResponse().getBody().getLength(), "the envelope must not reach the client");
        assertNull(exc.getResponse().getHeader().getContentType());
    }

    @Test
    void oneWayWithConfiguredStatusKeepsTheBodilessResponse() throws Exception {
        var interceptor = oneWayInterceptor(202);
        var exc = sendMessageExchange(interceptor, Response.ok().build());

        assertEquals(Outcome.CONTINUE, interceptor.handleResponse(exc));
        assertEquals(202, exc.getResponse().getStatusCode());
        assertEquals(0, exc.getResponse().getBody().getLength());
        assertEquals("0", exc.getResponse().getHeader().getFirstValue(CONTENT_LENGTH),
                "a bodiless 202 still needs Content-Length: 0 to frame the response");
    }

    @Test
    void oneWayErrorFromTheServiceIsNotAnnouncedAsSuccess() throws Exception {
        var interceptor = oneWayInterceptor(null);
        var exc = sendMessageExchange(interceptor, Response.statusCode(503).build());

        assertEquals(Outcome.ABORT, interceptor.handleResponse(exc));
        assertEquals(500, exc.getResponse().getStatusCode());
    }

    @Test
    void oneWayWhitespaceOnlyBodyCountsAsEmpty() throws Exception {
        var interceptor = oneWayInterceptor(null);
        var exc = sendMessageExchange(interceptor, Response.ok().body("\r\n").build());

        assertEquals(Outcome.CONTINUE, interceptor.handleResponse(exc));
        assertEquals(204, exc.getResponse().getStatusCode());
    }

    @Test
    void operationWithAnOutputKeepsItsJsonBodyAtAnExplicit204() throws Exception {
        var interceptor = interceptorWithStatus(204);
        var exc = getCityExchange(interceptor, GET_CITY_RESPONSE);

        assertEquals(Outcome.CONTINUE, interceptor.handleResponse(exc));
        assertEquals(204, exc.getResponse().getStatusCode());
        // Deliberate: the operation has a response message, so it is sent. Choosing a status that
        // must not carry one is the operator's call, not something the gateway overrules.
        assertEquals(123, new ObjectMapper().readTree(exc.getResponse().getBodyAsStringDecoded()).get("population").asInt());
    }

    @Test
    void oneWayOperationIsPublishedWithoutContent() throws Exception {
        var responses = generatedOpenApi(oneWayInterceptor(null))
                .getPaths().get("/send-message").getPost().getResponses();

        assertNull(responses.get("200"));
        assertNotNull(responses.get("204"));
        assertNull(responses.get("204").getContent(), "a bodiless response declares no content at all");
        assertEquals(2, responses.size());
        assertNotNull(responses.getDefault(), "a one-way operation can still fault");
    }

    @Test
    void configuredStatusWinsOverTheDerived204() throws Exception {
        var responses = generatedOpenApi(oneWayInterceptor(202))
                .getPaths().get("/send-message").getPost().getResponses();

        assertNull(responses.get("204"));
        assertNotNull(responses.get("202"));
        assertNull(responses.get("202").getContent());
    }

    /** The interceptor for qualified-elements.wsdl, whose sendMessage is one-way (input, no output). */
    private static Wsdl2OpenapiInterceptor oneWayInterceptor(Integer status) {
        var interceptor = wsdl2openapi("classpath:/ws/qualified-elements.wsdl");
        if (status != null) {
            var settings = new OperationSettings();
            settings.setStatus(status);
            var operations = new OperationsConfig();
            operations.setEntry(Map.of("sendMessage", settings));
            interceptor.setOperations(operations);
        }
        interceptor.init(new DummyTestRouter(), apiProxyWith(interceptor));
        return interceptor;
    }

    /** Runs the one-way request leg, then stages {@code backendResponse} as the service's answer. */
    private static Exchange sendMessageExchange(Wsdl2OpenapiInterceptor interceptor, Response backendResponse) throws Exception {
        var exc = new Exchange(null);
        exc.setRequest(new Request.Builder().post("/send-message").body("{\"text\":\"hi\",\"priority\":1}").build());
        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exc));
        exc.setResponse(backendResponse);
        return exc;
    }

    @ParameterizedTest
    @MethodSource("soapResponseEncodings")
    void responseUsesXmlEncoding(String encoding, boolean fault) throws Exception {
        var interceptor = wsdl2openapi("classpath:/ws/cities-with-fault.wsdl");
        interceptor.init(new DummyTestRouter(), apiProxyWith(interceptor));
        String soap = fault
                ? CITY_NOT_FOUND_FAULT.replace("Atlantis", "München")
                : """
                  <?xml version="1.0" encoding="UTF-8"?>
                  <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Body>
                      <getCityResponse xmlns="https://predic8.de/cities">
                        <country>Österreich</country>
                        <population>123</population>
                      </getCityResponse>
                    </soap:Body>
                  </soap:Envelope>
                  """;
        var exc = new Exchange(null);
        exc.setProperty(operationPropertyKey(interceptor), "getCity");
        exc.setResponse(Response.ok().body(soap.replace("UTF-8", encoding)
                .getBytes(Charset.forName(encoding))).build());
        exc.getResponse().getHeader().setContentType("text/xml");

        assertEquals(fault ? Outcome.ABORT : Outcome.CONTINUE, interceptor.handleResponse(exc));

        var body = new ObjectMapper().readTree(exc.getResponse().getBodyAsStringDecoded());
        if (fault) {
            assertEquals(500, exc.getResponse().getStatusCode());
            assertEquals("München", body.at("/details/cityNotFound/name").asText());
        } else {
            assertEquals("application/json", exc.getResponse().getHeader().getContentType());
            assertEquals("Österreich", body.get("country").asText());
            assertEquals(123, body.get("population").intValue());
        }
    }

    static Stream<Arguments> soapResponseEncodings() {
        return Stream.of("UTF-8", "ISO-8859-1", "UTF-16")
                .flatMap(encoding -> Stream.of(arguments(encoding, false), arguments(encoding, true)));
    }
                                    
    private static final String GET_CITY_RESPONSE_WITH_XML_DECLARATION = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <getCityResponse xmlns="https://predic8.de/cities">
                  <country>Österreich</country>
                  <population>123</population>
                </getCityResponse>
              </soap:Body>
            </soap:Envelope>
            """;

    @ParameterizedTest
    @ValueSource(strings = {"UTF-8", "ISO-8859-1", "UTF-16"})
    void responseUsesTheEncodingOfItsXmlDeclaration(String encoding) throws Exception {
        var response = transformGetCityResponse(
                GET_CITY_RESPONSE_WITH_XML_DECLARATION.replace("UTF-8", encoding), encoding, "text/xml", Outcome.CONTINUE);

        assertEquals("application/json", response.getHeader().getContentType());
        var body = new ObjectMapper().readTree(response.getBodyAsStringDecoded());
        assertEquals("Österreich", body.get("country").asText());
        assertEquals(123, body.get("population").intValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTF-8", "ISO-8859-1", "UTF-16"})
    void faultResponseUsesTheEncodingOfItsXmlDeclaration(String encoding) throws Exception {
        var response = transformGetCityResponse(
                CITY_NOT_FOUND_FAULT.replace("Atlantis", "München").replace("UTF-8", encoding),
                encoding, "text/xml", Outcome.ABORT);

        assertEquals(500, response.getStatusCode());
        assertEquals("München", new ObjectMapper().readTree(response.getBodyAsStringDecoded())
                .at("/details/cityNotFound/name").asText());
    }

    /**
     * A backend that omits the XML declaration leaves the Content-Type's charset as the only thing
     * naming the encoding; ignoring it would fall back to UTF-8 and mangle the body.
     */
    @ParameterizedTest
    @ValueSource(strings = {"UTF-8", "ISO-8859-1", "UTF-16"})
    void responseUsesTheContentTypeCharsetWhenTheXmlDeclarationIsAbsent(String encoding) throws Exception {
        var response = transformGetCityResponse(withoutXmlDeclaration(GET_CITY_RESPONSE_WITH_XML_DECLARATION), encoding,
                "text/xml; charset=" + encoding, Outcome.CONTINUE);

        assertEquals("Österreich", new ObjectMapper().readTree(response.getBodyAsStringDecoded())
                .get("country").asText());
    }

    /** RFC 7303 makes the Content-Type's charset authoritative for XML, over the document's own declaration. */
    @Test
    void contentTypeCharsetWinsOverAContradictingXmlDeclaration() throws Exception {
        var response = transformGetCityResponse(GET_CITY_RESPONSE_WITH_XML_DECLARATION, "ISO-8859-1",
                "text/xml; charset=ISO-8859-1", Outcome.CONTINUE);

        assertEquals("Österreich", new ObjectMapper().readTree(response.getBodyAsStringDecoded())
                .get("country").asText());
    }

    /** An encoding no JVM knows must not fail the exchange: the parser detects the encoding instead. */
    @Test
    void unknownContentTypeCharsetFallsBackToDetection() throws Exception {
        var response = transformGetCityResponse(GET_CITY_RESPONSE_WITH_XML_DECLARATION, "UTF-8",
                "text/xml; charset=no-such-charset", Outcome.CONTINUE);

        assertEquals("Österreich", new ObjectMapper().readTree(response.getBodyAsStringDecoded())
                .get("country").asText());
    }

    /**
     * Runs a SOAP response for the getCity operation through the interceptor and returns what the
     * exchange ends up carrying. {@code contentType} is set verbatim, so a test decides whether the
     * charset is declared in the header, in the document, in both, or in neither.
     */
    private static Response transformGetCityResponse(String soap, String encoding, String contentType,
                                                     Outcome expectedOutcome) throws Exception {
        var interceptor = wsdl2openapi("classpath:/ws/cities-with-fault.wsdl");
        interceptor.init(new DummyTestRouter(), apiProxyWith(interceptor));

        var exc = new Exchange(null);
        exc.setProperty(operationPropertyKey(interceptor), "getCity");
        exc.setResponse(Response.ok().body(soap.getBytes(Charset.forName(encoding))).build());
        exc.getResponse().getHeader().setContentType(contentType);

        assertEquals(expectedOutcome, interceptor.handleResponse(exc));
        return exc.getResponse();
    }

    private static String withoutXmlDeclaration(String xml) {
        return xml.substring(xml.indexOf("?>") + 2).stripLeading();
    }

    @Test
    void faultResponseDoesNotRevealTheSoapBackendInProduction() throws Exception {
        String body = faultResponse(true).getBodyAsStringDecoded();

        assertFalse(body.contains("faultCode"), "the SOAP fault code is a development-mode aid only");
        assertFalse(body.contains("City not found"), "the backend's faultstring must not reach the client");
        assertFalse(body.toLowerCase().contains("soap"), "nothing may name the technology behind the API");
        assertTrue(body.contains("Atlantis"), "the declared fault content is part of the contract and stays");
    }

    @Test
    void faultCodeIsAvailableAsInternalInformationOutsideProduction() throws Exception {
        // Internal fields are flattened into the document outside production mode; in production
        // they are replaced by a log key, which is what keeps the fault code off the wire.
        var body = new ObjectMapper().readTree(faultResponse(false).getBodyAsStringDecoded());

        assertEquals("soap:Client", body.get("faultCode").asText());
        assertEquals("City not found", body.get("faultMessage").asText());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void fieldNameThatIsNoXmlNameIsAClientError(String body) throws Exception {
        // The key reaches the DOM as an element or attribute name, which rejects it. That is the
        // client's mistake, so it must not be reported as a gateway failure.
        var response = requestResponse(body);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBodyAsStringDecoded().contains("Invalid field name"));
    }

    static Stream<Arguments> fieldNameThatIsNoXmlNameIsAClientError() {
        return Stream.of(
                arguments("{\"a b\": \"x\"}"),   // whitespace in an element name
                arguments("{\"1\": \"x\"}"),     // element name starting with a digit
                arguments("{\"@a b\": \"x\"}")   // whitespace in an attribute name
        );
    }

    @Test
    void pathParameterWinsOverTheSameFieldInTheBody() throws Exception {
        // The URL selected the resource. A body repeating the field — it is not in the published
        // request schema, but a client can still send it — must not redirect the call elsewhere.
        var settings = new OperationSettings();
        settings.setPath("/cities/{name}");
        settings.setMethod("PUT");
        var operations = new OperationsConfig();
        operations.setEntry(Map.of("getCity", settings));

        var interceptor = wsdl2openapi("classpath:/ws/cities.wsdl");
        interceptor.setOperations(operations);
        interceptor.init(new DummyTestRouter(), apiProxyWith(interceptor));

        var exc = new Exchange(null);
        exc.setRequest(new Request.Builder().put("/cities/Berlin").body("{\"name\":\"Atlantis\"}").build());

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exc));
        String soap = exc.getRequest().getBodyAsStringDecoded();
        assertTrue(soap.contains("Berlin"), "the value from the URL must reach the service: " + soap);
        assertFalse(soap.contains("Atlantis"), "the value from the body must not: " + soap);
    }

    /** Runs a request body through handleRequest on getCity and returns the aborting response. */
    private static Response requestResponse(String body) throws Exception {
        var interceptor = wsdl2openapi("classpath:/ws/cities.wsdl");
        var proxy = apiProxyWith(interceptor);
        interceptor.init(new DummyTestRouter(), proxy);

        var exc = new Exchange(null);
        exc.setRequest(new Request.Builder().post("/get-city").body(body).build());

        assertEquals(Outcome.ABORT, interceptor.handleRequest(exc));
        return exc.getResponse();
    }

    /** Runs a declared SOAP fault through handleResponse and returns the client-facing response. */
    private static Response faultResponse(boolean production) throws Exception {
        var router = new DummyTestRouter();
        router.getConfiguration().setProduction(production);
        var interceptor = wsdl2openapi("classpath:/ws/cities-with-fault.wsdl");
        var proxy = apiProxyWith(interceptor);
        interceptor.init(router, proxy);

        var exc = new Exchange(null);
        exc.setProperty(operationPropertyKey(interceptor), "getCity");
        exc.setResponse(Response.ok(CITY_NOT_FOUND_FAULT).build());

        assertEquals(Outcome.ABORT, interceptor.handleResponse(exc));
        return exc.getResponse();
    }

    private static String operationPropertyKey(Wsdl2OpenapiInterceptor interceptor) throws Exception {
        Field field = Wsdl2OpenapiInterceptor.class.getDeclaredField("operationPropertyKey");
        field.setAccessible(true);
        return (String) field.get(interceptor);
    }

    private static int routeCount(Wsdl2OpenapiInterceptor interceptor) {
        return interceptor.getOperationRouter().getRoutes().size();
    }

    private static io.swagger.v3.oas.models.OpenAPI generatedOpenApi(Wsdl2OpenapiInterceptor interceptor) throws Exception {
        Field publisherField = Wsdl2OpenapiInterceptor.class.getDeclaredField("publisher");
        publisherField.setAccessible(true);
        var publisher = (OpenAPIPublisherInterceptor) publisherField.get(interceptor);

        Field apisField = OpenAPIPublisherInterceptor.class.getDeclaredField("apis");
        apisField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var apis = (Map<String, OpenAPIRecord>) apisField.get(publisher);
        return apis.values().iterator().next().getApi();
    }

    private static Wsdl2OpenapiInterceptor wsdl2openapi(String wsdl) {
        var interceptor = new Wsdl2OpenapiInterceptor();
        interceptor.setWsdl(wsdl);
        return interceptor;
    }

    private static APIProxy apiProxyWith(Wsdl2OpenapiInterceptor... interceptors) {
        var proxy = new APIProxy();
        proxy.setName("TestAPI");
        proxy.setKey(new APIProxyKey(2000));
        proxy.getFlow().addAll(List.of(interceptors));
        return proxy;
    }

    @Test
    void chainedInstancesDoNotShareOperationProperty() throws Exception {
        var first = new Wsdl2OpenapiInterceptor();
        var second = new Wsdl2OpenapiInterceptor();

        Field kf = Wsdl2OpenapiInterceptor.class.getDeclaredField("operationPropertyKey");
        kf.setAccessible(true);
        String firstKey = (String) kf.get(first);
        String secondKey = (String) kf.get(second);
        assertNotEquals(firstKey, secondKey);

        var exc = new Exchange(null);
        exc.setProperty(firstKey, "someOperationOnlyFirstDefines");

        // second's handleResponse must not see first's matched operation and must not
        // attempt to transform the (SOAP) response body a second time.
        assertEquals(Outcome.CONTINUE, second.handleResponse(exc));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource
    void camelToKebabConv(String input, String expected) {
        assertEquals(expected, camelToKebab(input));
    }

    static Stream<Arguments> camelToKebabConv() {
        return Stream.of(
                arguments("getCity",                   "get-city"),
                arguments("getBank",                   "get-bank"),
                arguments("changeOtherIiDs",           "change-other-ii-ds"),       // pure camelCase
                arguments("change_OtherIiDs",          "change-other-ii-ds"),       // underscore + camelCase
                arguments("Get_Budget_Structures",     "get-budget-structures"),    // PascalCase + underscores
                arguments("Get_Allocation_Group_Sets", "get-allocation-group-sets"), // user-reported
                arguments("getURLs",                   "get-urls"),                 // acronym + plural suffix
                arguments("getUserID",                 "get-user-id"),              // acronym at end
                arguments("ID",                        "id"),                       // pure acronym
                arguments("change_other_ii",           "change-other-ii"),          // snake_case
                arguments("simple",                    "simple"),
                arguments("A",                         "a"),
                arguments("_leading",                  "leading"),
                arguments("double__under",             "double-under")
        );
    }
}
