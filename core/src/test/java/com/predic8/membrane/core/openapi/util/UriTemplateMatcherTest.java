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

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.openapi.util.UriTemplateMatcher.*;
import static java.util.List.of;
import static java.util.Map.*;
import static org.junit.jupiter.api.Assertions.*;

public class UriTemplateMatcherTest {

    @Test
    public void simple() throws PathDoesNotMatchException {
        assertEquals(0, matchTemplate("/foo", "/foo").size());
    }

    @Test
    public void prepareRegex() {
        assertEquals("/foo/([^/]+)/([^/]+)", prepareTemplate("/foo/{id1}/{id2}"));
    }

    @Test
    public void simpleNoneMatch() {
        assertThrows(PathDoesNotMatchException.class, () -> matchTemplate("/foo", "/bar"));
    }

    @Test
    public void noneMatch() {
        assertThrows(PathDoesNotMatchException.class, () -> matchTemplate("/foo/{fid}", "/bar/7"));
    }

    @Test
    void bad() throws PathDoesNotMatchException {
        Map<String, String> match = matchTemplate("aaa{id}bbb", "aaa7bbb");
        assertEquals(1, match.size());
        assertEquals("7", match.get("id"));
    }

    @Test
    public void simpleOneParam() throws PathDoesNotMatchException {
        Map<String, String> match = matchTemplate("/foo/{id}", "/foo/7");
        assertEquals(1, match.size());
        assertEquals("7", match.get("id"));
    }

    @Test
    public void matchNoSlashAtEnd() throws PathDoesNotMatchException {
        assertEquals(0, matchTemplate("/foo", "/foo/").size());
    }

    @Test
    public void matchTemplateTrailingSlash() throws PathDoesNotMatchException {
        assertEquals(0, matchTemplate("/foo/", "/foo").size());
    }

    @Test
    public void matchUriAndTemplateTrailingSlash() throws PathDoesNotMatchException {
        assertEquals(0, matchTemplate("/foo/", "/foo/").size());
    }

    @Test
    public void matchUriTrailingSlashWithParams() throws PathDoesNotMatchException {
        assertEquals(0, matchTemplate("/foo", "/foo/?x=1").size());
    }

    @Test
    public void matchNoTrailingSlashesWithParams() throws PathDoesNotMatchException {
        assertEquals(0, matchTemplate("/foo", "/foo?x=1").size());
    }

    @Test
    public void matchUriAndTemplateWithFourParams() throws PathDoesNotMatchException {
        Map<String, String> match = matchTemplate("/foo/{id1}/{id2}/{id3}/{id4}", "/foo/a/b/c/d");
        assertEquals(4, match.size());
        assertEquals("a", match.get("id1"));
        assertEquals("b", match.get("id2"));
        assertEquals("c", match.get("id3"));
        assertEquals("d", match.get("id4"));
    }

    @Test
    public void matchUriAndTemplateWithFourParamsWithTrailingSlash() throws PathDoesNotMatchException {
        assertEquals(4, matchTemplate("/foo/{id1}/{id2}/{id3}/{id4}", "/foo/a/b/c/d/").size());
    }

    @Test
    public void matchUriWithTwoParamsAgainstOneParam() {
        assertThrows(PathDoesNotMatchException.class, () -> matchTemplate("/foo/{id1}/{id2}", "/foo/a"));
    }

    @Test
    public void matchUriWithThreeParamsAgainstOneParam() {
        assertThrows(PathDoesNotMatchException.class, () -> matchTemplate("/foo/{id1}/{id2}/{id3}", "/foo/a"));
    }

    @Test
    public void matchUriWithOneParamsAgainstTwoParam() {
        assertThrows(PathDoesNotMatchException.class, () -> matchTemplate("/foo/{id1}", "/foo/a/b"));
    }

    @Test
    public void matchUriWithOneParamsAgainstThreeParam() {
        assertThrows(PathDoesNotMatchException.class, () -> matchTemplate("/foo/{id1}", "/foo/a/b/c"));
    }

    @Test
    public void matchUriWithTwoParams() throws PathDoesNotMatchException {
        Map<String, String> match = matchTemplate("/foo/{id1}/{id2}", "/foo/a/b");
        assertEquals(2, match.size());
        assertTrue(match.containsKey("id1"));
        assertTrue(match.containsKey("id2"));
    }

    @Test
    public void matchSingleSlashes() throws PathDoesNotMatchException {
        assertEquals(0, matchTemplate("/", "/").size());
    }

    @Test
    public void matchSingleTemplateSlash() throws PathDoesNotMatchException {
        assertEquals(0, matchTemplate("/", "").size());
    }

    @Test
    public void matchSingleUriSlash() throws PathDoesNotMatchException {
        assertEquals(0, matchTemplate("", "/").size());
    }

    @Test
    public void matchNoSlashes() throws PathDoesNotMatchException {
        assertEquals(0, matchTemplate("", "").size());
    }

    @Test
    public void match() throws PathDoesNotMatchException {
        assertEquals(Map.of("fid", "7"), matchTemplate("/foo/{fid}", "/foo/7"));
        assertEquals(Map.ofEntries(entry("cid","42"), entry("coid","abc")), matchTemplate("/customer/{cid}/contracts/{coid}", "/customer/42/contracts/abc"));
    }

    @Test
    void getNameMatcher() {
        Matcher m = UriTemplateMatcher.getNameMatcher("/foo/{id}/");
        assertTrue(m.find());
        assertEquals("id",m.group(1));
    }

    @Test
    void getNameMatcherTwoParameters() {
        Matcher m = UriTemplateMatcher.getNameMatcher("/foo/{id1}/{id2}");
        assertTrue(m.find());
        assertEquals("id1",m.group(1));
        assertTrue(m.find());
        assertEquals("id2",m.group(1));
        assertFalse(m.find());
    }

    @Test
    public void exoticParameterNames() throws PathDoesNotMatchException {
        var matches = matchTemplate("/foo/{i_d1}/{Id-2}/{id%3}/{id<>4}", "/foo/1/2/3/4/");
        assertEquals(4,matches.size());
        assertEquals("1", matches.get("i_d1"));
        assertEquals("2", matches.get("Id-2"));
        assertEquals("3", matches.get("id%3"));
        assertEquals("4", matches.get("id<>4"));
    }

    @Test
    void normalizePathEmpty() {
       assertEquals("/", normalizePath(""));
    }

    @Test
    void normalizePathSlash() {
        assertEquals("/", normalizePath("/"));
    }

    @Test
    void normalizePathSimple() {
        assertEquals("/foo/", normalizePath("/foo"));
    }

    @Test
    void getParameterNamesSimple() {
        assertEquals(of("id"), getParameterNames("/foo/{id}"));
    }

    @Test
    void getParameterNamesThree() {
        assertEquals(of("foo","baz","boo"), getParameterNames("/foo/{foo}/baz/{baz}/boo{boo}"));
    }

    @Test
    void getNoParameterNames() {
        assertEquals(of(), getParameterNames("/foo/baz/"));
    }
}
