/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.lang;

import com.predic8.membrane.core.interceptor.lang.SetCookiesInterceptor.CookieDef;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static com.predic8.membrane.core.interceptor.lang.SetCookiesInterceptor.CookieDef.SameSite.NONE;
import static com.predic8.membrane.core.interceptor.lang.SetCookiesInterceptor.CookieDef.SameSite.STRICT;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetCookiesInterceptorTest {

    @Test
    void buildHeader_withDefaults() {
        CookieDef cd = new CookieDef();
        cd.setName("sessionId");
        cd.setValue("abc123");
        String header = cd.buildHeader();
        assertTrue(header.contains("sessionId=abc123"));
        assertTrue(header.contains("Path=/"));
        assertTrue(header.contains("SameSite=LAX"));
        assertFalse(header.contains("Domain="));
        assertFalse(header.contains("Max-Age="));
        assertFalse(header.contains("Expires="));
        assertFalse(header.contains("HttpOnly"));
        assertFalse(header.contains("Secure"));
    }

    @Test
    void buildHeader_allAttributes() {
        CookieDef cd = new CookieDef();
        cd.setName("user");
        cd.setValue("john");
        cd.setDomain("example.com");
        cd.setPath("/app");
        cd.setMaxAge(3600);
        String expires = ZonedDateTime.now().plusDays(1).format(RFC_1123_DATE_TIME);
        cd.setExpires(expires);
        cd.setHttpOnly(true);
        cd.setSecure(true);
        cd.setSameSite(STRICT);

        String header = cd.buildHeader();
        assertTrue(header.startsWith("user=john;"));
        assertTrue(header.contains("Domain=example.com"));
        assertTrue(header.contains("Path=/app"));
        assertTrue(header.contains("Max-Age=3600"));
        assertTrue(header.contains("Expires=" + expires));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("Secure"));
        assertTrue(header.contains("SameSite=STRICT"));
    }

    @Test
    void buildHeader_sameSiteNone() {
        CookieDef cd = new CookieDef();
        cd.setName("pref");
        cd.setValue("yes");
        cd.setSameSite(NONE);
        assertTrue(cd.buildHeader().contains("SameSite=NONE"));
        assertTrue(cd.buildHeader().contains("Secure"));
    }

}