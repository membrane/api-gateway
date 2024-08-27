package com.predic8.membrane.core.security;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyStore;
import java.util.Optional;

import static com.predic8.membrane.core.transport.ssl.StaticSSLContext.openKeyStore;
import static org.junit.jupiter.api.Assertions.*;

public class KeyStoreUtilTest {

    private static Router router;

    @BeforeAll
    public static void before() {
        router = new HttpRouter();
    }

    @Test
    public void containsCertWithAlias_PresentAlias() throws Exception {
        KeyStore keystore = getAliasKeystoreByAlias("key1", router);
        assertTrue(KeyStoreUtil.containsCertWithAlias(keystore, "key1"));
    }

    @Test
    public void containsCertWithAlias_AbsentAlias() throws Exception {
        KeyStore keystore = getAliasKeystoreByAlias("key1", router);
        assertFalse(KeyStoreUtil.containsCertWithAlias(keystore, "key2"));
    }

    @Test
    public void containsCertWithAlias_EmptyKeystore() throws Exception {
        KeyStore keystore = getAliasKeystoreByAlias("key3", router);
        assertFalse(KeyStoreUtil.containsCertWithAlias(keystore, "key3"));
    }

    @Test
    public void getFirstCertAlias_PresentAlias() throws Exception {
        KeyStore keystore = getAliasKeystoreByAlias("key1", router);
        Optional<String> firstAlias = KeyStoreUtil.getFirstCertAlias(keystore);
        assertTrue(firstAlias.isPresent());
        assertEquals("key1", firstAlias.get());
    }

    @Test
    public void getFirstCertAlias_MultipleAliases() throws Exception {
        KeyStore keystore = getAliasKeystoreByAlias("key2", router);
        Optional<String> firstAlias = KeyStoreUtil.getFirstCertAlias(keystore);
        assertTrue(firstAlias.isPresent());
        assertEquals("key1", firstAlias.get());
    }

    @Test
    public void getFirstCertAlias_EmptyKeystore() throws Exception {
        KeyStore keystore = getAliasKeystoreByAlias("key3", router);
        Optional<String> firstAlias = KeyStoreUtil.getFirstCertAlias(keystore);
        assertEquals("",firstAlias.get());
    }

    private static @NotNull KeyStore getAliasKeystoreByAlias(String alias, Router router) throws Exception {
        return openKeyStore(new com.predic8.membrane.core.config.security.KeyStore() {{
            setLocation("classpath:/alias-keystore.p12");
            setKeyPassword("secret");
            setKeyAlias(alias);
        }}, "PKCS12", "secret".toCharArray(), router.getResolverMap(), router.getBaseLocation());
    }
}