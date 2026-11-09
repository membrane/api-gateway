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

/**
 * Creates the implementations of optional Membrane extensions (e.g. Kafka, JMS) that are only present when the
 * extension is on the classpath.
 */
public final class ExtensionLoader {

    private ExtensionLoader() {
    }

    /**
     * Creates the implementation from the extension.
     *
     * @param className      Class of the extension, needs a public constructor without parameters
     * @param type           Interface the class has to implement
     * @param missingMessage Message of the ConfigurationException thrown if the class is not on the classpath
     */
    public static <T> T load(final String className, final Class<T> type, final String missingMessage) {
        try {
            return Class.forName(className, true, ExtensionLoader.class.getClassLoader())
                    .asSubclass(type)
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (ClassNotFoundException e) {
            throw new ConfigurationException(missingMessage);
        } catch (NoClassDefFoundError e) {
            throw new ConfigurationException("Could not create %s, because the class %s it needs is missing. Put the library that contains it into the lib folder."
                    .formatted(className, e.getMessage()), e);
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            throw new ConfigurationException("Could not create %s. It needs to implement %s and have a public constructor without parameters."
                    .formatted(className, type.getName()), e);
        }
    }
}
