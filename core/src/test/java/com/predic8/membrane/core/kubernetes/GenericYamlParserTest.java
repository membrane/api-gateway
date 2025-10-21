/* Copyright 2025 predic8 GmbH, www.predic8.com
   Licensed under the Apache License, Version 2.0 (the "License");
   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.kubernetes;

import com.predic8.membrane.core.interceptor.authentication.BasicAuthenticationInterceptor;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingInterceptor;
import com.predic8.membrane.core.interceptor.beautifier.BeautifierInterceptor;
import com.predic8.membrane.core.interceptor.flow.ResponseInterceptor;
import com.predic8.membrane.core.interceptor.lang.SetCookiesInterceptor;
import com.predic8.membrane.core.interceptor.log.LogInterceptor;
import com.predic8.membrane.core.interceptor.oauth2client.MemcachedOriginalExchangeStore;
import com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor;
import com.predic8.membrane.core.interceptor.ratelimit.RateLimitInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor;
import com.predic8.membrane.core.interceptor.xml.Xml2JsonInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.util.MemcachedConnector;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.events.*;

import java.io.StringReader;
import java.util.*;
import java.util.stream.Stream;

import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.NO;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.YES;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(Lifecycle.PER_CLASS)
class GenericYamlParserMembraneTest {

    @ParameterizedTest
    @MethodSource("successCases")
    void parses_with_expectations(Case c) {
        c.check().accept(parse(c.yaml(), c.reg()));
    }

    @ParameterizedTest
    @MethodSource("errorCases")
    void fails_with_exception(ErrCase c) {
        assertThrows(c.expected(), () -> parse(c.yaml(), c.reg()));
    }

    Stream<Case> successCases() {

        // Registries for ref/injection rows
        MemcachedConnector mem = new MemcachedConnector() {{
            setHost("localhost");
            setPort(3000);
        }};
        BeanRegistry memReg = new TestRegistry().with("mem", mem);

        Target target = new Target() {{
            setUrl("https://ref.example");
        }};
        BeanRegistry targetReg = new TestRegistry().with("target", target);

        ResponseInterceptor responseInterceptor = new ResponseInterceptor();
        BeanRegistry responseReg = new TestRegistry().with("response", responseInterceptor);

        return Stream.of(
                ok(
                        "port_parsed",
                        """
                        port: 2000
                        """,
                        a -> assertEquals(2000, a.getPort())
                ),
                ok(
                        "target_url_parsed",
                        """
                        target:
                          url: https://api.predic8.de
                        """,
                        a -> assertEquals("https://api.predic8.de", a.getTarget().getUrl())
                ),
                ok(
                        "path_value_parsed",
                        """
                        path:
                          uri: /names
                        """,
                        a -> assertEquals("/names", a.getPath().getUri())
                ),
                ok(
                        "interceptors_parsed",
                        """
                        flow:
                          - rateLimiter:
                              requestLimit: 3
                          - rewriter:
                              mappings: []
                          - response: []
                        """,
                        a -> assertAll(
                                () -> assertEquals(3, a.getFlow().size()),
                                () -> assertInstanceOf(RateLimitInterceptor.class, a.getFlow().getFirst()),
                                () -> assertInstanceOf(RewriteInterceptor.class, a.getFlow().get(1)),
                                () -> assertInstanceOf(ResponseInterceptor.class, a.getFlow().get(2))
                        )
                ),
                ok(
                        "rateLimiter_requestLimit_parsed",
                        """
                        flow:
                          - rateLimiter:
                              requestLimit: 3
                              requestLimitDuration: PT30S
                        """,
                        a -> assertAll(
                                () -> assertEquals(3, ((RateLimitInterceptor) a.getFlow().getFirst()).getRequestLimit()),
                                () -> assertEquals("PT30S", ((RateLimitInterceptor) a.getFlow().getFirst()).getRequestLimitDuration())
                        )
                ),
                ok(
                        "rewriter_mapping",
                        """
                        flow:
                          - rewriter:
                              mappings:
                                - map:
                                    from: ^/names/(.*)
                                    to: /restnames/name\\.groovy\\?name=$1
                        """,
                        a -> assertAll(
                                () -> assertEquals("^/names/(.*)", ((RewriteInterceptor) a.getFlow().getFirst()).getMappings().getFirst().getFrom()),
                                () -> assertEquals("/restnames/name\\.groovy\\?name=$1", ((RewriteInterceptor) a.getFlow().getFirst()).getMappings().getFirst().getTo())
                        )
                ),
                ok(
                        "response_interceptors_parsed",
                        """
                        flow:
                          - response:
                            - beautifier: {}
                            - xml2Json: {}
                            - log: {}
                        """,
                        a -> assertAll(
                                () -> assertEquals(3, ((ResponseInterceptor) a.getFlow().getFirst()).getFlow().size()),
                                () -> assertInstanceOf(BeautifierInterceptor.class, ((ResponseInterceptor) a.getFlow().getFirst()).getFlow().getFirst()),
                                () -> assertInstanceOf(Xml2JsonInterceptor.class, ((ResponseInterceptor) a.getFlow().getFirst()).getFlow().get(1)),
                                () -> assertInstanceOf(LogInterceptor.class, ((ResponseInterceptor) a.getFlow().getFirst()).getFlow().get(2))
                        )
                ),
                ok(
                        "balancer_sessionTimeout_parsed",
                        """
                        flow:
                          - balancer:
                              sessionTimeout: 10000
                        """,
                        a -> assertAll(
                                () ->assertInstanceOf(Long.class, ((LoadBalancingInterceptor) a.getFlow().getFirst()).getSessionTimeout()),
                                () -> assertEquals(10000L, ((LoadBalancingInterceptor) a.getFlow().getFirst()).getSessionTimeout())
                        )
                ),
                ok(
                        "setCookies_cookie_fields",
                        """
                        flow:
                          - setCookies:
                              cookies:
                                - cookie:
                                    name: foo
                                    value: bar
                                    secure: false
                        """,
                        a -> {
                            SetCookiesInterceptor.CookieDef c = ((SetCookiesInterceptor) a.getFlow().getFirst()).getCookies().getFirst();
                            assertEquals("foo", c.getName());
                            assertEquals("bar", c.getValue());
                            assertInstanceOf(Boolean.class, c.isSecure());
                            assertFalse(c.isSecure());
                        }
                ),
                ok(
                        "basicAuth_user_attributes_map",
                        """
                        flow:
                          - basicAuthentication:
                              staticUserDataProvider:
                                users:
                                  - user:
                                      username: foo
                                      password: bar
                                      "a1": "t1"
                                      "a2": "t2"
                        """,
                        a -> {
                            StaticUserDataProvider.User u = ((BasicAuthenticationInterceptor) a.getFlow().getFirst()).getUsers().getFirst();
                            assertInstanceOf(Map.class, u.getAttributes());
                            assertEquals(Map.of("a1","t1","a2","t2","password","bar","username","foo"), u.getAttributes());
                        }
                ),

                ok(
                        "oauth2_memcached_ref_injected",
                        """
                        flow:
                          - oauth2Resource2:
                              memcachedOriginalExchangeStore:
                                connector: mem
                              membrane:
                                src: https://localhost/oauth2/
                        """,
                        memReg,
                        a -> {
                            OAuth2Resource2Interceptor i = (OAuth2Resource2Interceptor) a.getFlow().getFirst();
                            MemcachedOriginalExchangeStore store = (MemcachedOriginalExchangeStore) i.getOriginalExchangeStore();
                            assertSame(mem, store.getConnector());
                        }
                ),
                ok(
                        "ref_top_level_target",
                        """
                        $ref: target
                        """,
                        targetReg,
                        a -> {
                            assertSame(target, a.getTarget());
                            assertEquals("https://ref.example", a.getTarget().getUrl());
                        }
                ),
                ok(
                        "ref_interceptor_response",
                        """
                        flow:
                          - $ref: response
                        """,
                        responseReg,
                        a -> {
                            assertEquals(1, a.getFlow().size());
                            assertSame(responseInterceptor, a.getFlow().getFirst());
                        }
                ),
                ok(
                        "openapi_spec_single",
                        """
                        specs:
                          - openapi:
                              location: fruitshop-api.yml
                              validateRequests: "yes"
                        """,
                        a -> {
                            assertEquals(1, a.getSpecs().size());
                            assertEquals("fruitshop-api.yml", a.getSpecs().getFirst().getLocation());
                            assertEquals(YES, a.getSpecs().getFirst().getValidateRequests());
                        }
                ),
                ok(
                        "openapi_spec_multiple",
                        """
                        specs:
                          - openapi:
                              location: a.yml
                              validateRequests: "no"
                          - openapi:
                              location: b.yml
                              validateRequests: "yes"
                        """,
                        a -> {
                            assertEquals(2, a.getSpecs().size());
                            assertEquals("a.yml", a.getSpecs().getFirst().getLocation());
                            assertEquals("b.yml", a.getSpecs().get(1).getLocation());
                            assertEquals(NO, a.getSpecs().get(0).getValidateRequests());
                            assertEquals(YES, a.getSpecs().get(1).getValidateRequests());
                        }
                )
        );
    }

    Stream<ErrCase> errorCases() {
        BeanRegistry empty = new TestRegistry();
        return Stream.of(
                err(
                        "invalid_ref",
                        """
                        $ref: target
                        """,
                        empty, RuntimeException.class
                ),
                err(
                        "port_not_a_number",
                        """
                        port: not-a-number
                        """,
                        empty, RuntimeException.class
                )
        );
    }

    record Case(String testName, String yaml, BeanRegistry reg, java.util.function.Consumer<APIProxy> check) {
        @Override public @NotNull String toString() { return testName; }
    }

    record ErrCase(String testName, String yaml, BeanRegistry reg, Class<? extends Throwable> expected) {
        @Override public @NotNull String toString() { return testName; }
    }

    private static Case ok(String testName, String yaml, java.util.function.Consumer<APIProxy> check) {
        return new Case(testName, yaml, null, check);
    }
    private static Case ok(String testName, String yaml, BeanRegistry reg, java.util.function.Consumer<APIProxy> check) {
        return new Case(testName, yaml, reg, check);
    }

    private static ErrCase err(String testName, String yaml, BeanRegistry reg, Class<? extends Throwable> expected) {
        return new ErrCase(testName, yaml, reg, expected);
    }

    static class TestRegistry implements BeanRegistry {
        private final Map<String, Object> refs = new HashMap<>();
        TestRegistry with(String key, Object v) { refs.put(key, v); return this; }
        @Override public Object resolveReference(String ref) { return refs.get(ref); }
    }

    private static APIProxy parse(String yaml, BeanRegistry reg) {
        return GenericYamlParser.parse("api", APIProxy.class, events(yaml), reg);
    }

    private static Iterator<Event> events(String yaml) {
        Iterable<Event> iterable = new Yaml().parse(new StringReader(yaml));
        List<Event> filtered = new ArrayList<>();
        for (Event e : iterable) {
            if (e instanceof StreamStartEvent || e instanceof DocumentStartEvent) continue;
            if (e instanceof StreamEndEvent || e instanceof DocumentEndEvent) break;
            filtered.add(e);
        }
        return filtered.iterator();
    }
}