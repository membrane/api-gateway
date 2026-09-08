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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

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
