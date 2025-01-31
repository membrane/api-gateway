package com.predic8.membrane;

import com.predic8.membrane.core.*;
import org.junit.jupiter.api.*;

public abstract class AbstractTestWithRouter {

    protected static Router router;

    @BeforeAll
    static void setUp() {
        router = new HttpRouter();
    }

    @AfterAll
    static void shutDown() {
        router.shutdown();
    }
}
