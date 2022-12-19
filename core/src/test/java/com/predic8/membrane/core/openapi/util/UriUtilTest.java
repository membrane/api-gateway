/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.util;

import org.junit.jupiter.api.Test;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.openapi.util.UriUtil.*;
import static org.junit.jupiter.api.Assertions.*;

public class UriUtilTest {

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
        assertEquals("a/b/c/", UriUtil.normalizeUri("a//b///c//"));
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

    @Test
    public void rewriteStartsWithHttp() throws MalformedURLException {
        assertEquals("http://localhost:8080", rewrite("http://predic8.de", "http","localhost", 8080));
    }

    @Test
    public void rewriteStartsWithHttps() throws MalformedURLException {
        assertEquals("https://localhost", rewrite("http://predic8.de", "https","localhost", 443));
        assertEquals("https://localhost:8443", rewrite("http://predic8.de", "https","localhost", 8443));
    }

    @Test
    public void rewriteWithoutHttp() throws MalformedURLException {
        assertEquals("http://predic8.de:2000", rewrite("localhost:3000","http","predic8.de",2000));
        assertEquals("http://predic8.de", rewrite("localhost:3000","http","predic8.de",80));
    }

    @Test
    public void rewriteWithoutHttps() throws MalformedURLException {
        assertEquals("https://predic8.de:2000", rewrite("localhost:3000","https","predic8.de",2000));
        assertEquals("https://predic8.de", rewrite("localhost:3000","https","predic8.de",443));
    }

    @Test
    public void rewritePath() throws MalformedURLException {
        assertEquals("https://predic8.de:2000/foo", rewrite("localhost:3000/foo","https","predic8.de",2000));
        assertEquals("https://predic8.de/foo", rewrite("localhost:3000/foo","https","predic8.de",443));
    }
}