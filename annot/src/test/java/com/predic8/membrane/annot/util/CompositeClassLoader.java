/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.annot.util;

import java.net.URL;
import java.util.Enumeration;
import java.io.IOException;

/**
 * The CompositeClassLoader delegates calls to loaderA first, and if that fails, to loaderB second.
 */
public class CompositeClassLoader extends ClassLoader {

    private final ClassLoader loaderA;
    private final ClassLoader loaderB;

    public CompositeClassLoader(ClassLoader loaderA, ClassLoader loaderB) {
        super(null);
        this.loaderA = loaderA;
        this.loaderB = loaderB;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return loaderA.loadClass(name);
        } catch (ClassNotFoundException e) {
            return loaderB.loadClass(name);
        }
    }

    @Override
    protected URL findResource(String name) {
        URL resource = loaderA.getResource(name);
        if (resource == null) {
            resource = loaderB.getResource(name);
        }
        return resource;
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        return new Enumeration<>() {
            private final Enumeration<URL> enumA = loaderA.getResources(name);
            private final Enumeration<URL> enumB = loaderB.getResources(name);

            @Override
            public boolean hasMoreElements() {
                return enumA.hasMoreElements() || enumB.hasMoreElements();
            }

            @Override
            public URL nextElement() {
                if (enumA.hasMoreElements()) {
                    return enumA.nextElement();
                }
                return enumB.nextElement();
            }
        };
    }
}