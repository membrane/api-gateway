package com.predic8.membrane.core.util;

import org.junit.*;

import java.io.*;

import static org.junit.Assert.*;

public class FileUtilTest {

    InputStream is;

    @Before
    public void setUp() {
        is = new ByteArrayInputStream("Hello".getBytes());
    }

    @Test
    public void read() {
        Assert.assertEquals("Hello", FileUtil.readInputStream(is));
    }

}