package com.predic8.membrane.core.interceptor.apikey.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;


class ApiKeyFileStoreTest {

    ApiKeyFileStore f;

    @BeforeEach
    void setup() {
        f = new ApiKeyFileStore();
        f.setLocation(Objects.requireNonNull(getClass().getClassLoader().getResource("apikeys/keys.txt")).getPath());
    }

    @Test
    void readFile() throws IOException {
//        assertEquals("5XF27:finance,internal", f.readFile().get(0));
    }

    @Test
    void getScopesTest() {
        assertEquals("finance", f.getScopes("5XF27").get(0));
        assertEquals("internal", f.getScopes("5XF27").get(1));

//        assertEquals("finance", f.getScopes().get("5XF27").get(0));
//        assertEquals("internal", f.getScopes().get("5XF27").get(1));
    }

    @Test
    void getScopesTestWithWhiteSpaces() {
//        assertEquals("accounting", f.getScopes().get("73D29").get(0));
//        assertEquals("management", f.getScopes().get("73D29").get(1));
    }

    @Test
    void getScopesTestWithTrailingComma() {
//        assertEquals("internal", f.getScopes().get("89D5C").get(0));
    }
}
