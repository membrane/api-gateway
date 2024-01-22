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

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings("CatchMayIgnoreException")
public class UriTemplateMatcherTest {

    UriTemplateMatcher matcher;

    @BeforeEach
    public void setUp() {
        matcher = new UriTemplateMatcher();
    }


    @Test
    public void escapeSlash() {
        assertEquals("a\\/b\\/c", matcher.escapeSlash("a/b/c"));
        assertEquals("a\\/\\/b", matcher.escapeSlash("a//b"));
    }

    @Test
    public void getVariables() {
        assertEquals(List.of("fid","bid"),matcher.getPathParameterNames("foo{fid}bar{bid}"));
    }

    @Test
    public void prepareRegex() {
        assertEquals("foo(.*)bar(.*)", matcher.prepareRegex("foo{fid}bar{bid}"));
    }

    @Test
    public void simpleMatch() throws PathDoesNotMatchException {
        assertEquals(0,matcher.match("/foo", "/foo").size());
    }

    @Test
    public void matchTrailingSlash() throws PathDoesNotMatchException {
        assertEquals(0,matcher.match("/foo/", "/foo").size());
    }

    @Test
    public void matchTrailingSlashWithParams() throws PathDoesNotMatchException {
        assertEquals(0,matcher.match("/foo/", "/foo?demo-test=3141").size());
    }

    @Test
    public void matchSingleSlash() throws PathDoesNotMatchException {
        assertEquals(0,matcher.match("/", "/").size());
    }

    @Test
    public void matchSingleSlashWithParams() throws PathDoesNotMatchException {
        assertEquals(0,matcher.match("/", "/?demo-test=3141").size());
    }

    @Test
    public void matchEmptyPath() throws PathDoesNotMatchException {
        assertEquals(0,matcher.match("/", "").size());
    }

    @Test
    public void matchEmptyPathWithParams() throws PathDoesNotMatchException {
        assertEquals(0,matcher.match("/", "?demo-test=3141").size());
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
    public void match() throws PathDoesNotMatchException {
        assertEquals(Map.of("fid", "7"), matcher.match("/foo/{fid}", "/foo/7"));
        assertEquals(Map.ofEntries(entry("cid","42"), entry("coid","abc")), matcher.match("/customer/{cid}/contracts/{coid}", "/customer/42/contracts/abc"));
    }
}
