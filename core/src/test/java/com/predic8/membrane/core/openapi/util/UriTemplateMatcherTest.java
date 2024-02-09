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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.predic8.membrane.core.openapi.util.UriTemplateMatcher.prepareTemplate;
import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("CatchMayIgnoreException")
public class UriTemplateMatcherTest {

    UriTemplateMatcher matcher;

    @BeforeEach
    public void setUp() {
        matcher = new UriTemplateMatcher();
    }

    @Test
    public void simpleMatch() throws PathDoesNotMatchException {
        assertEquals(0,matcher.match("/foo", "/foo").size());
    }

    @Test
    public void prepareRegexTest() {
        assertEquals("/foo/(?<id1>[^/]+)/(?<id2>[^/]+)", prepareTemplate("/foo/{id1}/{id2}"));
    }

    @Test
    public void simpleNoneMatch() {
        try {
            matcher.match("/foo", "/bar");
            fail();
        } catch (PathDoesNotMatchException e) {
        }
    }

    @Test
    public void noneMatch() {
        try {
            matcher.match("/foo/{fid}", "/bar/7");
            fail();
        } catch (PathDoesNotMatchException e) {
        }
    }

    @Test
    public void matchNoSlashAtEnd() throws PathDoesNotMatchException {
        assertEquals(0,matcher.match("/foo", "/foo/").size());
    }

    @Test
    public void matchUriTrailingSlashWithParams() throws PathDoesNotMatchException {
        assertEquals(0,matcher.match("/foo", "/foo/?x=1").size());
    }

    @Test
    public void matchNoTrailingSlashesWithParams() throws PathDoesNotMatchException {
        assertEquals(0,matcher.match("/foo", "/foo?x=1").size());
    }

    @Test
    public void matchTemplateTrailingSlash() throws PathDoesNotMatchException {
        assertEquals(0,matcher.match("/foo/", "/foo").size());
    }

    @Test
    public void matchUriAndTemplateTrailingSlash() throws PathDoesNotMatchException {
        assertEquals(0,matcher.match("/foo/", "/foo/").size());
    }

    @Test
    public void matchUriTrailingSlashAndNoTemplateSlash() throws PathDoesNotMatchException {
        assertEquals(0,matcher.match("/foo", "/foo/").size());
    }

    @Test
    public void matchUriAndTemplateWithFourParams() throws PathDoesNotMatchException {
        assertEquals(4,matcher.match("/foo/{id1}/{id2}/{id3}/{id4}", "/foo/a/b/c/d").size());
    }

    @Test
    public void matchUriAndTemplateWithFourParamsWithTrailingSlash() throws PathDoesNotMatchException {
        assertEquals(4,matcher.match("/foo/{id1}/{id2}/{id3}/{id4}", "/foo/a/b/c/d/").size());
    }

    @Test
    public void matchUriWithTwoParamsAgainstOneParam() {
        assertThrows(PathDoesNotMatchException.class, () -> {
            matcher.match("/foo/{id1}/{id2}", "/foo/a");
        });
    }

    @Test
    public void matchUriWithThreeParamsAgainstOneParam() {
        assertThrows(PathDoesNotMatchException.class, () -> {
            matcher.match("/foo/{id1}/{id2}/{id3}", "/foo/a");
        });
    }

    @Test
    public void matchUriWithOneParamsAgainstTwoParam() {
        assertThrows(PathDoesNotMatchException.class, () -> {
            matcher.match("/foo/{id1}", "/foo/a/b");
        });
    }

    @Test
    public void matchUriWithOneParamsAgainstThreeParam() {
        assertThrows(PathDoesNotMatchException.class, () -> {
            matcher.match("/foo/{id1}", "/foo/a/b/c");
        });
    }

    @Test
    public void matchUriWithTwoParams() throws PathDoesNotMatchException {
        Map<String, String> match = matcher.match("/foo/{id1}/{id2}", "/foo/a/b");
        assertEquals(2, match.size());
        assertTrue(match.containsKey("id1"));
        assertTrue(match.containsKey("id2"));
    }

    @Test
    public void matchSingleSlashes() throws PathDoesNotMatchException {
        assertEquals(0,matcher.match("/", "/").size());
    }

    @Test
    public void matchSingleTemplateSlash() throws PathDoesNotMatchException {
        assertEquals(0,matcher.match("/", "").size());
    }

    @Test
    public void matchSingleUriSlash() throws PathDoesNotMatchException {
        assertEquals(0,matcher.match("", "/").size());
    }

    @Test
    public void matchNoSlashes() throws PathDoesNotMatchException {
        assertEquals(0,matcher.match("", "").size());
    }

    @Test
    public void match() throws PathDoesNotMatchException {
        assertEquals(Map.of("fid", "7"), matcher.match("/foo/{fid}", "/foo/7"));
        assertEquals(Map.ofEntries(entry("cid","42"), entry("coid","abc")), matcher.match("/customer/{cid}/contracts/{coid}", "/customer/42/contracts/abc"));
    }
}
