package com.predic8.membrane.core.interceptor.apikey;

import com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyFileStoreTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.core.interceptor.apikey.ApiKeyUtils.readFile;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiKeyUtilsTest {

    @Test
    void readFileTest() throws IOException {
        List<String> lines = readFile(Objects.requireNonNull(ApiKeyFileStoreTest.class.getClassLoader().getResource("apikeys/keys.txt")).getPath()).toList();
        assertEquals(5, lines.size());
        assertEquals("5XF27: finance,internal", lines.get(0));
    }
}