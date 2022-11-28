package com.predic8.membrane.core.openapi;

import com.predic8.membrane.core.openapi.model.*;
import org.junit.*;

import java.io.*;

import static org.junit.Assert.*;

public class InputStreamBodyTest {

    @Test
    public void asString() throws IOException {
        assertEquals("foo", new InputStreamBody(TestUtils.toInputStrom("foo")).asString());
    }
}