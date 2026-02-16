package com.predic8.membrane.core.proxies;

import com.predic8.membrane.core.proxies.Target.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TargetTest {

    @Test
    void defaultEscaping() {
        assertEquals(new Target().getEscaping(), Escaping.URL);
    }
}