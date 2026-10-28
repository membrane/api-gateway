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

package com.predic8.membrane.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionLoaderTest {

    public interface Extension {
    }

    public static class ExtensionImpl implements Extension {
    }

    @Test
    void loadsClassOnClasspath() {
        final var extension = ExtensionLoader.load(ExtensionImpl.class.getName(), Extension.class, "missing");
        assertEquals(ExtensionImpl.class, extension.getClass());
    }

    @Test
    void rejectsMissingClass() {
        final var e = assertThrows(ConfigurationException.class, () -> ExtensionLoader.load("io.membrane_api.ext.NotThere", Extension.class, "missing"));
        assertEquals("missing", e.getMessage());
    }

    /**
     * The class file is present but invalid, so this is no missing extension and the cause must be kept.
     */
    @Test
    void reportsClassThatCannotBeLoaded() {
        final var e = assertThrows(ConfigurationException.class, () -> ExtensionLoader.load(ExtensionLoaderTest.class.getPackageName() + ".BrokenExtension", Extension.class, "missing"));
        assertNotEquals("missing", e.getMessage());
        assertInstanceOf(LinkageError.class, e.getCause());
    }

    /**
     * MissingDependencyExtension.class extends a class that is not on the classpath, like an extension whose
     * library was not put into the lib folder.
     */
    @Test
    void namesMissingDependency() {
        final var e = assertThrows(ConfigurationException.class, () -> ExtensionLoader.load(ExtensionLoaderTest.class.getPackageName() + ".MissingDependencyExtension", Extension.class, "missing"));
        assertTrue(e.getMessage().contains("com/predic8/membrane/core/util/MissingDependency"), e.getMessage());
        assertTrue(e.getMessage().contains("lib folder"), e.getMessage());
        assertInstanceOf(NoClassDefFoundError.class, e.getCause());
    }

    @Test
    void rejectsClassOfWrongType() {
        assertThrows(ConfigurationException.class, () -> ExtensionLoader.load("java.lang.String", Extension.class, "missing"));
    }
}
