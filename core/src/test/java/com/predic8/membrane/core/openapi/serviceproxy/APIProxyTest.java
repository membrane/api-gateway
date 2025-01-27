package com.predic8.membrane.core.openapi.serviceproxy;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class APIProxyTest {

    @Test
    void port() {
        var p = new APIProxy();
        p.setKey(new APIProxyKey(80));
        assertTrue(p.getName().contains(":80"));
    }

    @Test
    void getNameWithName() {
        var p = new APIProxy();
        p.setName("Wonderproxy");
        assertEquals("Wonderproxy",p.getName());
    }

    @Test
    void getName() {
        var p = new APIProxy();
        var key = new APIProxyKey(80);
        key.setHost("localhost");
        key.setMethod("POST");
        key.setPath("/foo");
        p.setKey(key);
//        System.out.println("p.getName() = " + p.getName());
        assertEquals("localhost:80 POST /foo", p.getName());
    }

    @Test
    void getNameWithTest() {
        var p = new APIProxy();
        p.setTest("header.ContentType == 'text/plain'");
        p.init();
        assertEquals("0.0.0.0:80 header.ContentType == 'text/plain'",p.getName());
    }
}