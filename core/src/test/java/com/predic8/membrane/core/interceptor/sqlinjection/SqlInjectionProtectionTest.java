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

package com.predic8.membrane.core.interceptor.sqlinjection;

import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.sqlinjection.SqlInjectionProtection.Detection;
import com.predic8.membrane.core.util.URIFactory;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.http.Request.post;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the detection engine directly, without the interceptor, router or blocking concerns.
 */
class SqlInjectionProtectionTest {

    private static SqlInjectionProtection protection(boolean inspectHeaders) {
        return new SqlInjectionProtection(SqlInjectionRuleSet.loadCrsRules(1), inspectHeaders, new URIFactory());
    }

    private static final SqlInjectionProtection PROTECTION = protection(false);

    @Test
    void detectsInQueryParameter() throws Exception {
        Optional<Detection> hit = PROTECTION.scan(
                get("/search?q=%27%20UNION%20SELECT%20pw%20FROM%20users").build());
        assertTrue(hit.isPresent());
        assertEquals("query", hit.get().location());
    }

    @Test
    void detectsInPath() throws Exception {
        Optional<Detection> hit = PROTECTION.scan(get("/users/%27%20OR%20sleep(5)").build());
        assertTrue(hit.isPresent());
        assertEquals("path", hit.get().location());
    }

    @Test
    void detectsInJsonValue() throws Exception {
        Optional<Detection> hit = PROTECTION.scan(post("/api").contentType(APPLICATION_JSON)
                .body("{\"q\":\"information_schema.tables\"}").build());
        assertTrue(hit.isPresent());
        assertEquals("json value", hit.get().location());
    }

    @Test
    void detectsInFormBody() throws Exception {
        Optional<Detection> hit = PROTECTION.scan(post("/login").contentType(APPLICATION_X_WWW_FORM_URLENCODED)
                .body("user=admin&pw=sleep(5)").build());
        assertTrue(hit.isPresent());
        assertEquals("form", hit.get().location());
    }

    @Test
    void detectsInRawTextBody() throws Exception {
        Optional<Detection> hit = PROTECTION.scan(post("/q").contentType(TEXT_PLAIN)
                .body("give me everything from information_schema.tables").build());
        assertTrue(hit.isPresent());
        assertEquals("body", hit.get().location());
    }

    @Test
    void detectsInResponseBody() throws Exception {
        Optional<Detection> hit = PROTECTION.scan(Response.ok().contentType(APPLICATION_JSON)
                .body("{\"error\":\"near information_schema.tables: syntax error\"}").build());
        assertTrue(hit.isPresent());
    }

    @Test
    void passesBenignRequest() throws Exception {
        assertTrue(PROTECTION.scan(get("/search?q=garden+furniture&page=2").build()).isEmpty());
        assertTrue(PROTECTION.scan(post("/api").contentType(APPLICATION_JSON)
                .body("{\"name\":\"Alice\",\"city\":\"Berlin\"}").build()).isEmpty());
    }

    @Test
    void headersInspectedOnlyWhenEnabled() throws Exception {
        Request req = get("/").header("X-Filter", "id=1 union select pw from users").build();
        assertTrue(protection(false).scan(req).isEmpty(), "headers off -> clean");
        Optional<Detection> hit = protection(true).scan(req);
        assertTrue(hit.isPresent(), "headers on -> detected");
        assertTrue(hit.get().location().startsWith("header"));
    }
}
