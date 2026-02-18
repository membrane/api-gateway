package com.predic8.membrane.core.proxies;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.util.uri.EscapingUtil.Escaping.*;
import static org.junit.jupiter.api.Assertions.*;

class TargetTest {

    @Test
    void defaultEscaping() {
        assertEquals(new Target().getEscaping(), URL);
    }
}