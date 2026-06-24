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
