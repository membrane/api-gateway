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
package com.predic8.membrane.core.interceptor.oauth2client;

import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static com.predic8.membrane.core.interceptor.session.SessionManager.SESSION;
import static org.junit.jupiter.api.Assertions.*;

class OAuth2Resource2InterceptorTest {

    private OAuth2Resource2Interceptor oAuth2Resource2Interceptor;

    @BeforeEach
    void setup() throws Exception {
        oAuth2Resource2Interceptor = new OAuth2Resource2Interceptor();

        oAuth2Resource2Interceptor.setLogoutUrl("/logout");
        oAuth2Resource2Interceptor.init();
    }

    @Test
    void logout() throws Exception {
        var exc = new Request.Builder()
                .delete("/logout")
                .buildExchange();
        exc.setOriginalRequestUri("/logout");

        oAuth2Resource2Interceptor.handleRequestInternal(exc);

        var cookie = exc.getResponse().getHeader().getFirstValue("Set-Cookie");

        var expires = cookie.split("expires=")[1].trim().replace(";", "");

        var formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
        var zonedDateTime = ZonedDateTime.parse(expires, formatter);

        assertNull(exc.getProperty(SESSION));
        assertTrue(LocalDateTime.now().isAfter(zonedDateTime.toLocalDateTime()));
    }
}