/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class Http2ClientTest {

    @Test
    public void getGoogleHomepage() throws Throwable {
        HttpClientConfiguration configuration = new HttpClientConfiguration();
        configuration.setUseExperimentalHttp2(true);
        ConnectionConfiguration connection = new ConnectionConfiguration();
        connection.setKeepAliveTimeout(100);
        configuration.setConnection(connection);

        Exchange e = new Request.Builder().get("https://www.google.de").buildExchange();

        try(HttpClient hc = new HttpClient(configuration)) {
            hc.call(e);

            assertEquals(200, e.getResponse().getStatusCode());

            String body = e.getResponse().getBodyAsStringDecoded();
            assertTrue(body.startsWith("<!doctype html>"));
            assertTrue(body.endsWith("</html>"));
        }
    }
}
