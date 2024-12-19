/* Copyright 2024 predic8 GmbH, www.predic8.com

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

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.security.*;

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
    void notEnoughParameters() {
        assertThrows(InvalidParameterException.class, () -> combine("array.yml"));
    }

    @Test
    void moreThanTwoParameters() {
        assertEquals(
                current + "/src/test/resources/openapi/specs/array.yml",
                combine("src/test/resources/", "openapi/specs/foo", "array.yml")
        );
    }

    @Test
    void combineParentFileProtocolWithAbsoluteChild() {
        assertEquals(
                "file:///array.yml",
                combine("file://src/test/resources/openapi/specs", "/array.yml")
        );
    }

    @Test
    void combineParentFileProtocolWithRelativeChildWithoutTrailingSlash() {
        // TODO Soll specs verschwinden? Soll es auf einmal absolut sein?
        assertEquals(
                "file:///src/test/resources/openapi/array.yml",
                combine("file://src/test/resources/openapi/specs.wsdl", "array.yml")
        );
    }

    @Test
    void combineParentWithNonFileProtocolAndRelativeChild() {
        assertEquals(
                "https://api.predic8.de/shop/v2/api-docs",
                combine("https://api.predic8.de/shop/", "v2/api-docs")
        );
    }

    @Test
    void combineParentWithInvalidURI() {
        assertThrows(RuntimeException.class, () -> combine("http://invalid:\\path", "array.yml"));
    }

    @Test
    void combineRelativeParentWithRelativeChild() {
        assertEquals(current + "/src/test/resources/openapi/specs/array.yml", combine("src/test/resources/openapi/specs/", "array.yml"));
    }

    @Test
    void combineRelativeParentWithRelativeChildParentDoesNotEndWithSlash() {
        assertEquals(current + "/src/test/resources/openapi/specs/array.yml", combine("src/test/resources/openapi/specs/foo", "array.yml"));
    }

    @Test
    void combineRelativeParentWithRelativeProtocolChild() {
        assertEquals("file://array.yml", combine("src/test/resources/openapi/specs/", "file://array.yml"));
    }

}