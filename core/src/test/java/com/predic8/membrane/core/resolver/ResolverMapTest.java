/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.resolver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResolverMapTest {

    @Test
    void urlWithoutPathAndRelativePath() {
        var s = ResolverMap.combine("http://localhost:2000", "/foo?a=foo");
        assertEquals("http://localhost:2000/foo?a=foo", s);
    }

    @Test
    void schemeInsideQueryIsNotTreatedAsChildScheme() {
        var s = ResolverMap.combine("http://localhost:2000", "/foo?a=http://dummy");
        assertEquals("http://localhost:2000/foo?a=http://dummy", s);
    }

    @Test
    void childWithOwnSchemeReplacesParent() {
        var s = ResolverMap.combine("http://localhost:2000/foo", "http://other:3000/bar");
        assertEquals("http://other:3000/bar", s);
    }

    @Test
    void relativeChildResolvesAgainstParentDirectory() {
        var s = ResolverMap.combine("http://localhost:2000/a/b.xml", "c.xml");
        assertEquals("http://localhost:2000/a/c.xml", s);
    }

    @Test
    void absoluteChildReplacesParentPath() {
        var s = ResolverMap.combine("http://localhost:2000/a/b.xml", "/c.xml");
        assertEquals("http://localhost:2000/c.xml", s);
    }

    @Test
    void emptyParentReturnsChild() {
        var s = ResolverMap.combine("", "/foo");
        assertEquals("/foo", s);
    }

    @Test
    void classpathParentWithRelativeChild() {
        var s = ResolverMap.combine("classpath:/a/b.xml", "c.xml");
        assertEquals("classpath:/a/c.xml", s);
    }

    @Test
    void internalParentWithRelativeChild() {
        var s = ResolverMap.combine("internal:/a/b.xml", "c.xml");
        assertEquals("internal:/a/c.xml", s);
    }

    @Test
    void moreThanTwoLocationsAreFoldedLeftToRight() {
        var s = ResolverMap.combine("http://localhost:2000", "/a/", "b.xml");
        assertEquals("http://localhost:2000/a/b.xml", s);
    }

    @Test
    void spaceInChildIsEncoded() {
        var s = ResolverMap.combine("http://localhost:2000", "/foo bar");
        assertEquals("http://localhost:2000/foo%20bar", s);
    }
}
