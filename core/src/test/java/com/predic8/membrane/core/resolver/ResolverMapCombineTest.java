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

import com.predic8.membrane.core.util.OSUtil;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.*;
import java.security.*;

import static com.predic8.membrane.core.resolver.ResolverMap.*;
import static com.predic8.membrane.core.util.OSUtil.wl;
import static org.junit.jupiter.api.Assertions.*;

public class ResolverMapCombineTest {
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

    // Normal paths

    @Test
    void relativePlusFile() {
        assertEquals(current + "/a/c", combine("a/b","c"));
    }

    @Test
    void relativeWithSlashPlusFile() {
        assertEquals(wl(
                current + "/a/b/c".replaceAll("/", "\\\\"),
                current + "/a/b/c"
                ), combine("a/b/","c"));
    }

    @Test
    void relativeWithSpacePlusFile() {
        assertEquals(current + wl("\\a b\\e","/a b/e"), combine("a b/c d","e"));
    }

    // File paths

    @Test
    void fileSingleSlashPlusFile() {
        assertEquals("file:/chi/gnat", combine("file:/chi/elm","gnat"));
    }

    @Test
    void fileRelativePlusFile() {
        assertEquals("file:/chi/gnat", combine("file:///chi/elm","gnat"));
    }

    @Test
    void fileRelativeWithSlashPlusFile() {
        assertEquals("file:/chi/elm/gnat", combine("file:///chi/elm/","gnat"));
    }

    @Test
    void fileWithAbsoluteChild() {
        assertEquals(wl(
                "file:///array.yml",
                "file:/array.yml"
                ), combine("file://src/test/resources/openapi/specs", "/array.yml"));
    }

    @Test
    void fileUriPlusAbsolutePath() {
        assertEquals(wl(
                "file:///chi/elm",
                "file:/chi/elm"
                 ), combine("file:///foo","/chi/elm"));
    }


    @Test
    void fileWithSpacheAbsoluteChild() {
        assertEquals("file:/tang%20ting/yap%20lob.yml", combine("file:///tang%20ting/slob", "yap lob.yml"));
    }

    // URLS

    @Test
    void httpPlusHttp() {
        assertEquals("http://predic8.de/chi/gnat", combine("http://predic8.de/chi/elm","gnat"));
    }

    @Test
    void uriPlusSlash() {
        assertEquals("http://predic8.de/", combine("http://predic8.de/chi/elm","/"));
    }

    @Test
    void uriPlusAsolutePath() {
        assertEquals("http://predic8.de/cha", combine("http://predic8.de/chi/elm","/cha"));
    }

    @Test
    void pathPlusUrl() {
        assertEquals("http://predic8.de/chi/elm", combine("/foo","http://predic8.de/chi/elm"));
    }

    // Special

    @Test
    void moreThanTwoParameters() {
        assertEquals(current + "/src/test/resources/openapi/specs/array.yml", combine("src/test/resources/", "openapi/specs/foo", "array.yml"));
    }

    @Test
    void combineParentFileProtocolWithRelativeChildWithoutTrailingSlash() {
        assertEquals("file:/src/test/resources/openapi/array.yml", combine("file://src/test/resources/openapi/specs.wsdl", "array.yml"));
    }

    @Test
    void combineParentWithNonFileProtocolAndRelativeChild() {
        assertEquals("https://api.predic8.de/shop/v2/api-docs", combine("https://api.predic8.de/shop/", "v2/api-docs"));
    }

    @Test
    void combineParentWithInvalidURI() {
        assertThrows(RuntimeException.class, () -> combine("http://invalid:\\path", "array.yml"));
    }

    @Test
    void combineRelativeParentWithRelativeChild() {
        assertEquals(wl(
                current + "\\src\\test\\resources\\openapi\\specs\\array.yml",
                current + "/src/test/resources/openapi/specs/array.yml"
                ), combine("src/test/resources/openapi/specs/", "array.yml"));
    }

    @Test
    void combineRelativeParentWithRelativeChildParentDoesNotEndWithSlash() {
        assertEquals(wl(
                current + "\\src\\test\\resources\\openapi\\specs\\array.yml",
                current + "/src/test/resources/openapi/specs/array.yml"
                ), combine("src/test/resources/openapi/specs/foo", "array.yml"));
    }

    @Test
    void combineRelativeParentWithRelativeProtocolChild() {
        assertEquals("file://array.yml", combine("src/test/resources/openapi/specs/", "file://array.yml"));
    }

    // Tests with spaces

    @Test
    void relativeWithSlashPlusFileWithSpace() {
        assertEquals(wl(
                current + "\\a\\chi cha\\cock lock",
                current + "/a/chi cha/cock lock"
                ), combine("a/chi cha/","cock lock"));
    }

    @Test
    void fileSingleSlashPlusFileSpace() {
        assertEquals("file:/cock%20lock", combine("file:/chi%cha","file:/cock%20lock"));
    }

    @Test
    void filePlusPathSpace() {
        assertEquals("file:/cock%20lock", combine("file:/chi","cock lock"));
    }

    @Test
    void pathPlusPathSpace() {
        assertEquals(wl(
                current + "\\chi cha\\cock lock",
                current + "/chi cha/cock lock"
                ), combine("chi cha/cock","cock lock"));
    }

    @Test
    void urlPlusPathSpace() {
        assertEquals("http://localhost:2000/cock%20lock", combine("http://localhost:2000/chip","cock lock"));
    }
}