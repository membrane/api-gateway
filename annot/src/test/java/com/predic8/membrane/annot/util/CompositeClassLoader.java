package com.predic8.membrane.annot.util;

import java.net.URL;
import java.util.Enumeration;
import java.io.IOException;

/**
 * The CompositeClassLoader delegates calls to loaderA first, and if that fails, to loaderB second.
 */
public class CompositeClassLoader extends ClassLoader {

    private ClassLoader loaderA;
    private ClassLoader loaderB;

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
        return new Enumeration<URL>() {
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