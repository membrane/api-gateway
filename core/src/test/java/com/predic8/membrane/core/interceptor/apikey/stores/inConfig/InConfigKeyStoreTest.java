package com.predic8.membrane.core.interceptor.apikey.stores.inConfig;

import com.predic8.membrane.core.interceptor.apikey.stores.UnauthorizedApiKeyException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static java.util.List.of;
import static java.util.Optional.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InConfigKeyStoreTest {

    @Test
    void getScopes() throws UnauthorizedApiKeyException {
        InConfigKeyStore inConfigKeyStore = new InConfigKeyStore();
        inConfigKeyStore.setKeys(of(
                new Key(){{setValue("12345");}},
                new Key(){{
                    setValue("67890");
                    setScopes(of(
                            new Scope(){{setValue("admin");}},
                            new Scope(){{setValue("user");}})
                    );
                }})
        );
        assertEquals(empty(), inConfigKeyStore.getScopes("12345"));
        assertEquals(Optional.of(of("admin", "user")), inConfigKeyStore.getScopes("67890"));
        assertThrows(UnauthorizedApiKeyException.class, () -> inConfigKeyStore.getScopes("ABCDE"));
    }
}