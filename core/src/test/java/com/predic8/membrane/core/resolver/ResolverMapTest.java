package com.predic8.membrane.core.resolver;

import org.junit.jupiter.api.*;

import java.nio.file.*;

import static com.predic8.membrane.core.resolver.ResolverMap.*;
import static org.junit.jupiter.api.Assertions.*;

public class ResolverMapTest {

    /**
     * Current directory.
     */
    static String current;

    @BeforeAll
    static void setup() {
        current = String.valueOf(Paths.get("").toAbsolutePath());
    }

    @Test
    void combineRelativeDirAndFile() {
        assertEquals(current + "/src/test/resources/openapi/specs/array.yml", combine("src/test/resources/openapi/specs/","array.yml"));
    }

    @Test
    void combineAbsolutePath() {
        assertEquals("file://array.yml", combine("src/test/resources/openapi/specs/","file://array.yml"));
    }

    @Test
    void combineParentStartsWithFile() {
        assertEquals(current + "/src/test/resources/openapi/specs/array.yml", combine(current + "/src/test/resources/openapi/specs/","array.yml"));
    }

    @Test
    void combineParentStartsWithFileChild() {
        assertEquals(current + "/array.yml", combine(current + "/src/test/resources/openapi/specs/","/array.yml"));
    }

    @Test
    void removeFileProtocol() {
        assertEquals("foo", ResolverMap.removeFileProtocol("file:foo"));
        assertEquals("foo", ResolverMap.removeFileProtocol("foo"));
    }

    @Test
    void f() {
       // assertTrue(ResolverMap.keepTrailingSlash(new File("src/test/resources/openapi/"),"specs/"));


    }
}