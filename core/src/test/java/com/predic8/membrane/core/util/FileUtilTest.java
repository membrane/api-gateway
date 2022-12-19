package com.predic8.membrane.core.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileUtilTest {

    InputStream is;

    @BeforeEach
    public void setUp() {
        is = new ByteArrayInputStream("Hello".getBytes());
    }

    @Test
    public void read() {
        assertEquals("Hello", FileUtil.readInputStream(is));
    }

}