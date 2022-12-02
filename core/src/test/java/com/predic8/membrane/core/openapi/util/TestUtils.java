package com.predic8.membrane.core.openapi.util;

import com.fasterxml.jackson.databind.*;

import java.io.*;
import java.util.*;

public class TestUtils {

    public static InputStream toInputStrom(String s) {
        return new ByteArrayInputStream(s.getBytes());
    }
}
