package com.predic8.membrane.core.openapi.util;

import org.junit.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.openapi.util.PathUtils.*;
import static org.junit.Assert.*;

public class PathUtilsTest {

    @Test
    public void withQuery() {
        assertEquals("foo",trimQueryString("foo?bar"));
    }

    @Test
    public void withoutQuery() {
        assertEquals("foo", trimQueryString("foo"));
    }

    @Test
    public void normalizeUri() {
        assertEquals("a/b/c/",PathUtils.normalizeUri("a//b///c//"));
    }

    @Test
    public void stringWithoutParams() {
        assertEquals(0,parseQueryString("foo").size());
    }

    @Test
    public void stringWithoutParamsAndQuestionmark() {
        assertEquals(0,parseQueryString("foo?").size());
    }

    @Test
    public void oneParam() {
        Map<String,String> m = parseQueryString("foo?answer=42");
        assertEquals(1,m.size());
        assertEquals("42",m.get("answer"));
    }

    @Test
    public void twoParams() {
        Map<String,String> m = parseQueryString("foo?answer=42&city=Bonn");
        assertEquals(2,m.size());
        assertEquals("42",m.get("answer"));
        assertEquals("Bonn",m.get("city"));
    }

    @Test
    public void ajustPathAccordingToBasePathTest() {
        assertEquals("/shop/products/", ajustPathAccordingToBasePath("/foo", "/shop/products/"));
        assertEquals("/products/", ajustPathAccordingToBasePath("/shop", "/shop/products/"));
        assertEquals("/products/", ajustPathAccordingToBasePath("/shop/", "/shop/products/"));
        assertEquals("/shop/products/", ajustPathAccordingToBasePath("/", "/shop/products/"));
        assertEquals("/shop/products/", ajustPathAccordingToBasePath("", "/shop/products/"));
        assertEquals("/products/shop/", ajustPathAccordingToBasePath("/shop/", "/products/shop/"));
    }

    @Test
    public void ajustPathAccordingToBasePathAbvancedTest() {
        assertEquals("/baz", ajustPathAccordingToBasePath("/foo/bar", "/foo/bar/baz"));
        assertEquals("/baz", ajustPathAccordingToBasePath("/foo/bar/", "/foo/bar/baz"));
    }

    @Test
    public void normalizeBasePathTest() {
        assertEquals("/foo/",normalizeBasePath("/foo"));
        assertEquals("",normalizeBasePath(""));
        assertEquals("/foo/",normalizeBasePath("/foo/"));
    }

    @Test
    public void getUrlWithoutPathTest() throws MalformedURLException {
        assertEquals("http://foo",getUrlWithoutPath(new URL("http://foo")));
        assertEquals("http://foo.de",getUrlWithoutPath(new URL("http://foo.de/bar")));
        assertEquals("http://foo",getUrlWithoutPath(new URL("http://foo/bar")));
    }

    @Test
    public void getUrlWithoutPathPort() throws MalformedURLException {
        assertEquals("http://foo",getUrlWithoutPath(new URL("http://foo:80")));
        assertEquals("http://foo.de:8080",getUrlWithoutPath(new URL("http://foo.de:8080/bar")));
        assertEquals("https://foo",getUrlWithoutPath(new URL("https://foo:443/bar")));
    }
}