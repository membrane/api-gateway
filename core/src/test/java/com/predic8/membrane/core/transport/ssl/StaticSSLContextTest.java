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

import com.predic8.membrane.core.config.security.TrustStore;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.router.Router;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyStore;

import static com.predic8.membrane.core.transport.ssl.StaticSSLContext.openKeyStore;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code alias-cert.pem}/{@code alias-cert.der} (exported from {@code alias-keystore.p12}) are
 * valid 2024-08-23 through 2034-08-21 - re-export from that keystore once expired.
 */
class StaticSSLContextTest {

    private static Router router;

    @BeforeAll
    static void setUp() {
        router = new DummyTestRouter();
    }

    @AfterAll
    static void tearDown() {
        router.stop();
    }

    @Test
    void openKeyStoreLoadsPemCertificateAsTruststore() throws Exception {
        TrustStore store = new TrustStore();
        store.setType("PEM");
        store.setLocation("classpath:/alias-cert.pem");

        KeyStore ks = openKeyStore(store, null, router.getResolverMap(), router.getConfiguration().getBaseLocation());

        assertEquals(1, ks.size());
        assertNotNull(ks.getCertificate(ks.aliases().nextElement()));
    }

    @Test
    void openKeyStoreLoadsDerCertificateAsTruststore() throws Exception {
        TrustStore store = new TrustStore();
        store.setType("PEM");
        store.setLocation("classpath:/alias-cert.der");

        KeyStore ks = openKeyStore(store, null, router.getResolverMap(), router.getConfiguration().getBaseLocation());

        assertEquals(1, ks.size());
        assertNotNull(ks.getCertificate(ks.aliases().nextElement()));
    }

    @Test
    void openKeyStorePemTypeIgnoresPassword() throws Exception {
        TrustStore store = new TrustStore();
        store.setType("PEM");
        store.setLocation("classpath:/alias-cert.pem");
        store.setPassword("irrelevant");

        assertDoesNotThrow(() -> openKeyStore(store, null, router.getResolverMap(), router.getConfiguration().getBaseLocation()));
    }
}
