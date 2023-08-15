package com.predic8.membrane.core.openapi.serviceproxy;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.*;

class OpenAPIProxyServiceKeyTest {

    OpenAPIProxyServiceKey k1;

    @BeforeEach
    void setup() {
        k1 = new OpenAPIProxyServiceKey("","", 80);
    }

    @DisplayName("Access old path /api-doc")
    @ParameterizedTest
    @MethodSource("urls")
    void checkAcceptsPath(String url, boolean expected) {
        assertEquals(expected, k1.complexMatch("predic8.de","GET",url, "", 80, "192.168.0.1"));
    }

    private static Stream<Arguments> urls() {
        return Stream.of(
                of("/api-docs",true),
                of("/api-docs/ui",true),
                of("/api-doc",true),
                of("/api-doc/ui",true),
                of("/apidoc",false)
                );
    }

}