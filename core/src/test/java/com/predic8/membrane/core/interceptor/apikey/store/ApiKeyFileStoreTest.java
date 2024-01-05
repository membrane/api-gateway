package com.predic8.membrane.core.interceptor.apikey.store;

import com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyFileStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

class ApiKeyFileStoreTest {

    ApiKeyFileStore f;

    @BeforeEach
    void setup() {
        f = new ApiKeyFileStore();
        f.setLocation(requireNonNull(getClass().getClassLoader().getResource("apikeys/keys.txt")).getPath());
        f.onApplicationEvent(null);
    }

    @Test
    void readFile() throws IOException {
        assertEquals("5XF27:finance,internal", f.readFile().get(0));
    }

    @Test
    void getScopesTest() {
        assertEquals("finance", f.getScopes("5XF27").get(0));
        assertEquals("internal", f.getScopes("5XF27").get(1));
    }

    @Test
    void getScopesWithRefreshTest() {
        f.setLocation(requireNonNull(getClass().getClassLoader().getResource("apikeys/keys2.txt")).getPath());
        assertEquals("finance", f.getScopes("5XF27").get(0));
        f.setRefresh(true);
        assertNull(f.getScopes("5XF27"));
        assertEquals("administrator", f.getScopes("78AB5").get(0));
    }

    @Test
    void getScopesTestWithWhiteSpace() {
        assertEquals("accounting", f.getScopes("73D29").get(0));
        assertEquals("management", f.getScopes("73D29").get(1));
    }

    @Test
    void getScopesTestWithTrailingComma() {
        assertEquals("internal", f.getScopes("89D5C").get(0));
    }
}
