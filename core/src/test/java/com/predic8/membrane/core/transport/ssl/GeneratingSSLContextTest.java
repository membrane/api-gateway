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

package com.predic8.membrane.core.transport.ssl;

import com.predic8.membrane.core.config.security.KeyGenerator;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratingSSLContextTest {

    /**
     * The generated contexts validate client certificates with the JDK's default trust store and ignore the
     * configured one. Behind a shared TLS port the client authentication was not enforced at all, see
     * {@link SSLContext#admitsSessionOf}.
     */
    @ParameterizedTest
    @ValueSource(strings = {"need", "want"})
    void clientAuthIsRejected(String clientAuth) {
        final var ssl = new SSLParser();
        ssl.setKeyGenerator(new KeyGenerator());
        ssl.setClientAuth(clientAuth);

        final var e = assertThrows(ConfigurationException.class, () -> new GeneratingSSLContext(ssl, new ResolverMap(), null));
        assertTrue(e.getMessage().contains("clientAuth"), e.getMessage());
    }
}
