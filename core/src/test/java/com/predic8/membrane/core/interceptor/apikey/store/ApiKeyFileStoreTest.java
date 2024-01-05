package com.predic8.membrane.core.interceptor.apikey.store;

import com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyFileStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.*;

import java.io.IOException;
import java.util.*;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiKeyFileStoreTest {

    ApiKeyFileStore f;

    @BeforeEach
    void setup() {
        f = new ApiKeyFileStore();
        f.setLocation(requireNonNull(getClass().getClassLoader().getResource("apikeys/keys.txt")).getPath());

        //noinspection DataFlowIssue
        f.onApplicationEvent(null);
    }

    @Test
    void readFile() throws IOException {
        assertEquals("5XF27:finance,internal", f.readFile().get(0));
    }

    @Test
    void getScopesTest() {
        assertEquals(List.of("finance","internal"), f.getScopes("5XF27"));
    }

    @Test
    void getMissingScopes() {
        assertEquals(new ArrayList<String>(), f.getScopes("ABCDE"));
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
