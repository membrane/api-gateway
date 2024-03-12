package com.predic8.membrane.core.interceptor.apikey.stores.inConfig;

import com.predic8.membrane.core.interceptor.apikey.stores.UnauthorizedApiKeyException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static java.util.List.of;
import static java.util.Optional.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimpleKeyStoreTest {

    @Test
    void getScopes() throws UnauthorizedApiKeyException {
        SimpleKeyStore simpleKeyStore = new SimpleKeyStore();
        simpleKeyStore.setKeys(of(
                new Key(){{setValue("12345");}},
                new Key(){{
                    setValue("67890");
                    setScopes(of(
                            new Scope(){{setValue("admin");}},
                            new Scope(){{setValue("user");}})
                    );
                }})
        );
        assertEquals(empty(), simpleKeyStore.getScopes("12345"));
        assertEquals(Optional.of(of("admin", "user")), simpleKeyStore.getScopes("67890"));
        assertThrows(UnauthorizedApiKeyException.class, () -> simpleKeyStore.getScopes("ABCDE"));
    }
}