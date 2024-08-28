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
package com.predic8.membrane.core.security;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.resolver.ResolverMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class KeyStoreUtilsTest {

    private static Router router;
    private static java.security.KeyStore keyStore;
    private static final String ALIAS = "key1";
    private static final String KEYSTORE_PASSWORD = "secret";
    private static final String EXPECTED_DIGEST = "96:ec:da:3d:2a:a3:a9:7e:3b:40:56:46:86:d7:1c:d2:a9:e1:69:3f:99:6b:8d:57:4a:4c:bb:7a:24:55:18:ed";

    @BeforeAll
    static void setUp() throws Exception {
        router = new HttpRouter();
        SSLParser sslParser = new SSLParser();
        sslParser.setKeyStore(new KeyStore());
        sslParser.getKeyStore().setLocation("classpath:/alias-keystore.p12");
        sslParser.getKeyStore().setKeyPassword(KEYSTORE_PASSWORD);
        keyStore = KeyStoreUtil.getAndLoadKeyStore(sslParser.getKeyStore(), router.getResolverMap(), router.getBaseLocation(), "PKCS12", KEYSTORE_PASSWORD.toCharArray());
    }

    @AfterAll
    static void tearDown() {
        router.stop();
    }

    @Test
    void testGetDigest() throws CertificateEncodingException, KeyStoreException, NoSuchAlgorithmException {
        String digest = KeyStoreUtil.getDigest(keyStore, ALIAS);
        assertEquals(EXPECTED_DIGEST, digest);
    }

    @Test
    void testGetAndLoadKeyStore() throws KeyStoreException, NoSuchProviderException, IOException, NoSuchAlgorithmException, CertificateException {
        KeyStore store = new KeyStore();
        store.setLocation("classpath:/alias-keystore.p12");
        store.setKeyPassword(KEYSTORE_PASSWORD);
        java.security.KeyStore loadedKeyStore = KeyStoreUtil.getAndLoadKeyStore(store, router.getResolverMap(), router.getBaseLocation(), "PKCS12", KEYSTORE_PASSWORD.toCharArray());
        assertNotNull(loadedKeyStore);
        assertTrue(loadedKeyStore.size() > 0);
        assertTrue(loadedKeyStore.containsAlias(ALIAS));
    }

    @Test
    void testFirstAliasOrThrowFound() throws KeyStoreException {
        String alias = KeyStoreUtil.firstAliasOrThrow(keyStore);
        assertEquals(ALIAS, alias);
    }

    @Test
    void testFirstAliasOrThrowNotFound() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        java.security.KeyStore emptyKeyStore = java.security.KeyStore.getInstance("PKCS12");
        emptyKeyStore.load(null, null);
        assertThrows(RuntimeException.class, () -> KeyStoreUtil.firstAliasOrThrow(emptyKeyStore));
    }

    @Test
    void testAliasOrThrowFound() throws KeyStoreException {
        String alias = KeyStoreUtil.aliasOrThrow(keyStore, ALIAS);
        assertEquals(ALIAS, alias);
    }

    @Test
    void testAliasOrThrowNotFound() {
        assertThrows(RuntimeException.class, () -> KeyStoreUtil.aliasOrThrow(keyStore, "nonexistent"));
    }

    @Test
    void testGetFirstCertAliasFound() throws KeyStoreException {
        Optional<String> alias = KeyStoreUtil.getFirstCertAlias(keyStore);
        assertTrue(alias.isPresent());
        assertEquals(ALIAS, alias.get());
    }

    @Test
    void testGetFirstCertAliasNotFound() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        java.security.KeyStore emptyKeyStore = java.security.KeyStore.getInstance("PKCS12");
        emptyKeyStore.load(null, null);
        Optional<String> alias = KeyStoreUtil.getFirstCertAlias(emptyKeyStore);
        assertFalse(alias.isPresent());
    }
}