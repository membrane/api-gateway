package com.predic8.membrane.core.openapi.util;

import org.junit.*;

import java.io.*;
import java.net.*;

import static com.predic8.membrane.core.openapi.util.Utils.getPathFromURL;
import static org.junit.Assert.*;

public class UtilsTest {

    @Test
    public void inputStreamToString() throws IOException {
        assertEquals("foo",Utils.inputStreamToString(Utils.stringToInputStream("foo")));
    }

    @Test
    public void inputStreamToStringUmlauts() throws IOException {
        assertEquals("äöü",Utils.inputStreamToString(Utils.stringToInputStream("äöü")));
    }

    @Test
    public void getRequestBodyFromRef() {
        assertEquals("CustomerRequest",Utils.getComponentLocalNameFromRef("#/components/requestBodies/CustomerRequest"));
    }

    @Test
    public void getSchemaTypeFromRef() {
        assertEquals("Customer",Utils.getComponentLocalNameFromRef("#/components/schemas/Customer"));
    }

    @Test
    public void getPathFromURLTest() throws MalformedURLException {
        assertEquals("/foo",    getPathFromURL("localhost/foo"));
        assertEquals("/foo",    getPathFromURL("http://localhost/foo"));
        assertEquals("/foo",    getPathFromURL("localhost/foo"));
        assertEquals("/foo",    getPathFromURL("localhost.de/foo"));
        assertEquals("/foo",    getPathFromURL("localhost.de:3000/foo"));
    }

    @Test
    public void getMediaTypeFromContentTypeHeader() {
        assertEquals("application/json",    Utils.getMediaTypeFromContentTypeHeader("application/json; charset=utf-8"));
        assertEquals("application/json",    Utils.getMediaTypeFromContentTypeHeader("application/json"));
    }
}