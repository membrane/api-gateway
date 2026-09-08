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

package com.predic8.membrane.core.transport.http.client.protocol;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.transport.http.ProtocolUpgradeDeniedException;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.predic8.membrane.core.http.Request.get;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolHandlerFactoryTest {

    ProtocolHandlerFactory factory;
    Exchange exc;

    @BeforeEach
    void setUp() throws Exception {
        factory = new ProtocolHandlerFactory(new HttpClientConfiguration(), null);
        exc = get("/foo").buildExchange();
    }

    @Test
    void noUpgradeProtocolUsesHttp1() throws Exception {
        assertInstanceOf(Http1ProtocolHandler.class, factory.getHandler(exc, null));
    }

    /**
     * Upgrade tokens are case-insensitive, so an h2c request has to reach the HTTP/2 handler in every
     * spelling. Otherwise no handler matches and the request is denied instead of being downgraded.
     */
    @ParameterizedTest
    @ValueSource(strings = {"h2c", "H2C", "h2C", "h2", "H2"})
    void http2IsMatchedRegardlessOfCase(String protocol) throws Exception {
        assertInstanceOf(Http2ProtocolHandler.class, factory.getHandler(exc, protocol));
    }

    @Test
    void unknownProtocolIsDenied() {
        assertThrows(ProtocolUpgradeDeniedException.class, () -> factory.getHandler(exc, "gopher"));
    }
}
