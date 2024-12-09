package com.predic8.membrane.core.util;

import org.junit.jupiter.api.*;

import javax.xml.namespace.*;

import static org.junit.jupiter.api.Assertions.*;

public class XMLUtilTest {

    static final QName JA = new QName("a","a");
    static final groovy.namespace.QName GA = new groovy.namespace.QName("a","a");

    static final QName PREFIX_A = new QName("ns","local","a");
    static final QName PREFIX_B = new QName("ns","local","b");

    @Test
    void groovyToJavaxQName() {
        assertEquals(JA, XMLUtil.groovyToJavaxQName(GA));
    }

    @Test
    void equalsIgnorePrefix() {
        assertEquals(PREFIX_A, PREFIX_B);
    }
}