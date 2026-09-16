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
package com.predic8.membrane.core.proxies;

import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxyKey;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.openapi.serviceproxy.Rewrite;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.test.TestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.predic8.membrane.core.proxies.ApiInfo.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiInfoTest {

    @Test
    @DisplayName("Unnamed proxy on a wildcard host: fallback name, then the URL")
    void unnamedWildcardHost() {
        var proxy = new ServiceProxy(new ServiceProxyKey(2000), "backend", 80);

        assertEquals("0.0.0.0:2000  http://127.0.0.1:2000", proxyLabel(proxy));
    }

    @Test
    @DisplayName("Unnamed proxy with a path: fallback name, then the URL including the path")
    void unnamedWithPath() {
        var proxy = new ServiceProxy(new ServiceProxyKey("*", "*", "/shop/v2/", 2000), "backend", 80);

        assertEquals("0.0.0.0:2000 /shop/v2/  http://127.0.0.1:2000/shop/v2/", proxyLabel(proxy));
    }

    @Test
    @DisplayName("Named proxy: configured name, then the URL")
    void namedProxy() {
        var proxy = new ServiceProxy(new ServiceProxyKey("*", "*", "/shop/v2/", 2000), "backend", 80);
        proxy.setName("Fruitshop API");

        assertEquals("Fruitshop API  http://127.0.0.1:2000/shop/v2/", proxyLabel(proxy));
    }

    @Test
    @DisplayName("Explicit ip: is used as the displayed host")
    void explicitIp() {
        var key = new ServiceProxyKey(2000);
        key.setIp("192.168.1.5");
        var proxy = new ServiceProxy(key, "backend", 80);

        assertEquals("192.168.1.5", displayHost(key));
        assertEquals("http://192.168.1.5:2000", buildUrl(proxy));
    }

    @Test
    @DisplayName("A non-wildcard host: is used as the displayed host when no ip: is set")
    void nonWildcardHost() {
        var key = new ServiceProxyKey("api.example.com", "*", null, 2000);

        assertEquals("api.example.com", displayHost(key));
    }

    @Test
    @DisplayName("https when inbound SSL is configured")
    void httpsProtocol() {
        var proxy = new ServiceProxy(new ServiceProxyKey(2000), "backend", 80) {
            @Override
            public String getProtocol() {
                return "https";
            }
        };

        assertEquals("https://127.0.0.1:2000", buildUrl(proxy));
    }

    @Test
    @DisplayName("InternalProxy keeps just its name, no URL")
    void internalProxyHasNoUrl() {
        var proxy = new InternalProxy();
        proxy.setName("shared-auth");

        assertEquals("shared-auth", proxyLabel(proxy));
    }

    @Test
    @DisplayName("SSLProxy keeps just its name, no URL")
    void sslProxyHasNoUrl() {
        var proxy = new SSLProxy();
        proxy.setHost("api.example.com");
        proxy.setPort(8443);

        assertEquals("SSL api.example.com:8443", proxyLabel(proxy));
    }

    @Test
    @DisplayName("openapi: with one servers[] entry: the URL includes the OpenAPI's base path")
    void openApiSingleBasePath() {
        var proxy = openApiProxy("fruitshop-api-v2-openapi-3.yml");

        assertEquals(Set.of("/shop/v2/"), proxy.getBasePaths().keySet());
        assertEquals("http://127.0.0.1:2000/shop/v2/", buildUrl(proxy));
    }

    @Test
    @DisplayName("openapi: resolving to several base paths: each is rendered as a complete URL, comma-separated")
    void openApiMultipleBasePathsListedCommaSeparated() {
        var proxy = openApiProxy("customers.yml");

        assertEquals(Set.of("/", "/foo/"), proxy.getBasePaths().keySet());
        assertEquals("http://127.0.0.1:2000/, http://127.0.0.1:2000/foo/", buildUrl(proxy));
    }

    @Test
    @DisplayName("openapi: with a rewrite basePath: the rewritten path is used")
    void openApiRewrittenBasePath() {
        var spec = openApiSpec("fruitshop-api-v2-openapi-3.yml");
        var rewrite = new Rewrite();
        rewrite.setBasePath("/bar");
        spec.setRewrite(rewrite);

        var proxy = new APIProxy();
        proxy.setOpenapi(List.of(spec));
        proxy.setKey(new APIProxyKey(2000));
        proxy.init(new DummyTestRouter());

        assertEquals(Set.of("/bar/"), proxy.getBasePaths().keySet());
        assertEquals("http://127.0.0.1:2000/bar/", buildUrl(proxy));
    }

    @Test
    @DisplayName("APIProxy without any openapi: spec is unaffected, still uses key.getPath()")
    void apiProxyWithoutOpenApiUsesConfiguredPath() {
        var proxy = new APIProxy();
        proxy.setKey(new ServiceProxyKey("*", "*", "/shop/v2/", 2000));
        proxy.init(new DummyTestRouter());

        assertNull(proxy.getBasePaths());
        assertEquals("http://127.0.0.1:2000/shop/v2/", buildUrl(proxy));
    }

    private static APIProxy openApiProxy(String fixture) {
        var proxy = new APIProxy();
        proxy.setOpenapi(List.of(openApiSpec(fixture)));
        proxy.setKey(new APIProxyKey(2000));
        proxy.init(new DummyTestRouter());
        return proxy;
    }

    private static OpenAPISpec openApiSpec(String fixture) {
        var spec = new OpenAPISpec();
        spec.location = TestUtil.getPathFromResource("openapi/specs/" + fixture);
        return spec;
    }
}
