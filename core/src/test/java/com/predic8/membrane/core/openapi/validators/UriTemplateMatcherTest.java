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

package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.util.*;
import org.junit.*;

import java.util.*;

import static com.predic8.membrane.core.util.MapUtils.stringMap;
import static org.junit.Assert.*;

@SuppressWarnings("CatchMayIgnoreException")
public class UriTemplateMatcherTest {

    UriTemplateMatcher matcher;

    @Before
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
        assertEquals(ListUtils.stringList("fid","bid"),matcher.getPathParameterNames("foo{fid}bar{bid}"));
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
        assertEquals(stringMap("fid","7"), matcher.match("/foo/{fid}", "/foo/7"));
        assertEquals(stringMap("cid","42","coid","abc"), matcher.match("/customer/{cid}/contracts/{coid}", "/customer/42/contracts/abc"));
    }
}