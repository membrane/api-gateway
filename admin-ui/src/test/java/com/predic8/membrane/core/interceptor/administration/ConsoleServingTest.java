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
package com.predic8.membrane.core.interceptor.administration;

import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.net.http.*;
import java.util.regex.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The built UI is on the classpath of this module's tests, as it is in the distribution.
 */
class ConsoleServingTest {

    private static final int PORT = 3081;
    private static final String BASE = "http://localhost:" + PORT;
    private static final HttpClient http = HttpClient.newHttpClient();

    private static DefaultRouter router;

    @BeforeAll
    static void setUp() throws Exception {
        router = new DefaultRouter();
        final var proxy = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", PORT, null), "localhost", 80);
        proxy.getFlow().add(new AdminConsoleInterceptor());
        router.add(proxy);
        router.start();
    }

    @AfterAll
    static void tearDown() {
        router.stop();
    }

    /**
     * The UI is not built with -Dskip.npm; then only the old console is served.
     */
    private static void assumeUiBuilt() {
        Assumptions.assumeTrue(AdminConsoleInterceptor.class.getResource(AdminConsoleInterceptor.CONSOLE_INDEX) != null,
                "The admin UI was not built.");
    }

    @Test
    void rootRedirectsToTheNewConsole() throws Exception {
        assumeUiBuilt();
        final var res = get("/");
        assertEquals(3, res.statusCode() / 100);
        assertTrue(res.headers().firstValue("Location").orElseThrow().endsWith("/console/"));
    }

    @Test
    void consoleWithoutSlashRedirects() throws Exception {
        assertTrue(get("/console").headers().firstValue("Location").orElseThrow().endsWith("/console/"));
    }

    @Test
    void servesTheConsoleAndItsAssets() throws Exception {
        assumeUiBuilt();
        final var index = get("/console/");
        assertEquals(200, index.statusCode());
        assertTrue(index.body().contains("<div id=\"root\">"), index.body());

        final var script = Pattern.compile("src=\"\\./(assets/[^\"]+\\.js)\"").matcher(index.body());
        assertTrue(script.find(), index.body());
        final var asset = get("/console/" + script.group(1));
        assertEquals(200, asset.statusCode());
        assertTrue(asset.headers().firstValue("Content-Type").orElse("").contains("javascript"));
    }

    @Test
    void oldConsoleIsStillServed() throws Exception {
        assertEquals(200, get("/admin").statusCode());
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(BASE + path)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
