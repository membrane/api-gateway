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

import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.predic8.membrane.core.interceptor.wsdl2openapi.Wsdl2OpenapiInterceptor.buildPathPattern;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.Wsdl2OpenapiInterceptor.extractParamNames;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.camelToKebab;
import static com.predic8.membrane.test.TestUtil.getPathFromResource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class Wsdl2OpenapiInterceptorTest {

    private static Wsdl2OpenapiInterceptor interceptorWith(String basePath, String segment, String method, String opName) {
        return new Wsdl2OpenapiInterceptor(basePath,
                List.of(new Wsdl2OpenapiInterceptor.RouteEntry(
                        buildPathPattern(segment),
                        extractParamNames(segment),
                        method, opName)));
    }

    @Test
    void matchRouteStripsQueryString() {
        var interceptor = interceptorWith("/purchasing", "get-city", "POST", "getCity");
        var result = interceptor.matchRoute("/purchasing/get-city?format=json", "POST");
        assertTrue(result.isPresent());
        assertEquals("getCity", result.get().operationName());
    }

    @Test
    void matchRouteWithoutQueryString() {
        var interceptor = interceptorWith("/purchasing", "get-city", "POST", "getCity");
        var result = interceptor.matchRoute("/purchasing/get-city", "POST");
        assertTrue(result.isPresent());
        assertEquals("getCity", result.get().operationName());
    }

    @Test
    void matchRoutePreservesOperationName() {
        var interceptor = interceptorWith("/api", "get-city", "GET", "GetCity");
        var result = interceptor.matchRoute("/api/get-city", "GET");
        assertTrue(result.isPresent());
        assertEquals("GetCity", result.get().operationName());
    }

    @Test
    void matchRouteHandlesRegexMetacharsInBasePath() {
        var interceptor = interceptorWith("/api/v1.0", "get-city", "POST", "getCity");
        var result = interceptor.matchRoute("/api/v1.0/get-city", "POST");
        assertTrue(result.isPresent());
        assertEquals("getCity", result.get().operationName());
    }

    @Test
    void matchRouteDistinguishesByMethod() {
        var interceptor = new Wsdl2OpenapiInterceptor("/", List.of(
                new Wsdl2OpenapiInterceptor.RouteEntry(buildPathPattern("articles"), extractParamNames("articles"), "GET", "getAll"),
                new Wsdl2OpenapiInterceptor.RouteEntry(buildPathPattern("articles"), extractParamNames("articles"), "POST", "create")
        ));
        assertEquals("getAll", interceptor.matchRoute("/articles", "GET").map(Wsdl2OpenapiInterceptor.RouteMatch::operationName).orElse(null));
        assertEquals("create", interceptor.matchRoute("/articles", "POST").map(Wsdl2OpenapiInterceptor.RouteMatch::operationName).orElse(null));
        assertTrue(interceptor.matchRoute("/articles", "DELETE").isEmpty());
    }

    @Test
    void matchRouteExtractsPathParam() {
        var interceptor = interceptorWith("/api", "partners/{id}", "GET", "getPartner");
        var result = interceptor.matchRoute("/api/partners/42", "GET");
        assertTrue(result.isPresent());
        assertEquals("getPartner", result.get().operationName());
        assertEquals(Map.of("id", "42"), result.get().pathParams());
    }

    @Test
    void matchRouteExtractsMultiplePathParams() {
        var interceptor = interceptorWith("/", "foo/{a}/bar/{b}", "GET", "fooBar");
        var result = interceptor.matchRoute("/foo/hello/bar/world", "GET");
        assertTrue(result.isPresent());
        assertEquals("fooBar", result.get().operationName());
        assertEquals("hello", result.get().pathParams().get("a"));
        assertEquals("world", result.get().pathParams().get("b"));
    }

    @Test
    void allowedMethodsListsEveryMethodRegisteredForThePath() {
        var interceptor = new Wsdl2OpenapiInterceptor("/", List.of(
                new Wsdl2OpenapiInterceptor.RouteEntry(buildPathPattern("partners/{id}"), extractParamNames("partners/{id}"), "GET", "getPartner"),
                new Wsdl2OpenapiInterceptor.RouteEntry(buildPathPattern("partners/{id}"), extractParamNames("partners/{id}"), "PUT", "updatePartner"),
                new Wsdl2OpenapiInterceptor.RouteEntry(buildPathPattern("partners"), extractParamNames("partners"), "POST", "createPartner")
        ));

        // A path mapped for other methods must report them all, so the 405 carries a correct
        // Allow header instead of the single method of whichever route happened to be found.
        assertEquals(List.of("GET", "PUT"), interceptor.allowedMethods("/partners/42"));
        assertEquals(List.of("POST"), interceptor.allowedMethods("/partners"));
        // An unmapped path has no allowed methods at all — the request must fall through, not 405.
        assertEquals(List.of(), interceptor.allowedMethods("/unmapped"));
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

        assertEquals(List.of("POST"), interceptor.allowedMethods("/get-city"));
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
    void reInitDoesNotAccumulateRoutes() throws Exception {
        var router = new DummyTestRouter();
        var proxy = apiProxyWith(wsdl2openapi("classpath:/ws/cities.wsdl"));
        var interceptor = (Wsdl2OpenapiInterceptor) proxy.getFlow().getFirst();

        // AbstractProxy.clone() and RuleManager.replaceRule both call init on the same
        // interceptor instance, so init must be repeatable without piling up state.
        interceptor.init(router, proxy);
        int routesAfterFirstInit = routeCount(interceptor);
        interceptor.init(router, proxy);

        assertEquals(routesAfterFirstInit, routeCount(interceptor), "Routes must not be registered twice");
        assertTrue(interceptor.matchRoute("/get-city", "POST").isPresent(), "Route must still match after re-init");
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

    @SuppressWarnings("unchecked")
    private static int routeCount(Wsdl2OpenapiInterceptor interceptor) throws Exception {
        Field field = Wsdl2OpenapiInterceptor.class.getDeclaredField("routes");
        field.setAccessible(true);
        return ((List<Wsdl2OpenapiInterceptor.RouteEntry>) field.get(interceptor)).size();
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
