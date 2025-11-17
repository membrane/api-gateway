package com.predic8.membrane.annot.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Enumeration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.util.StreamUtils.copyToByteArray;

/**
 * Implements a class loader based on in-memory files. Also implements an 'overlay file system' which can on-the-fly
 * define resources which should also appear in the file system.
 */
class InMemoryClassLoader extends ClassLoader {
    private static final Logger log = LoggerFactory.getLogger(InMemoryClassLoader.class);

    private final InMemoryData data;
    private InMemoryData overlay =  new InMemoryData();

    public InMemoryClassLoader(InMemoryData data) {
        super(InMemoryClassLoader.class.getClassLoader());
        this.data = data;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            // 1. Check if the class is already loaded
            Class<?> c = findLoadedClass(name);
            if (c != null) {
                return c;
            }

            // 2. CRITICAL: Always delegate core Java classes to the parent
            // Trying to load these yourself will break the JVM.
            // A real implementation also delegates shared API classes (e.g., "javax.servlet.").
            if (name.startsWith("java.") || name.startsWith("javax.")) {
                try {
                    c = super.loadClass(name, false); // Use parent-first logic
                    if (c != null) {
                        return c;
                    }
                } catch (ClassNotFoundException e) {
                    // Fall through to local search (unlikely for java.*)
                }
            }

            // 3. Child-First: Try to find the class in our local URLs *first*
            try {
                c = findClass(name);
                return c; // Found it locally
            } catch (ClassNotFoundException e) {
                // Not found locally. Swallow this exception.
            }

            // 4. Parent-Last: If not found locally, *now* delegate to the parent.
            // We call the superclass's loadClass, which implements parent-first.
            return super.loadClass(name, false);
        }
    }


    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        log.debug("findClass({})", name);
        URI uri = URI.create("string:///" + name.replace('.', '/') + ".class");
        byte[] bytes = data.content.get(uri);
        if (bytes == null) {
            if (!delegateToRootClassLoader(name)) {
                try (java.io.InputStream is = this.getClass().getResourceAsStream(uri.toString().replaceAll("string://", ""))) {
                    if (is != null) {
                        byte[] buffer = copyToByteArray(is);
                        return defineClass(name, buffer, 0, buffer.length);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return super.findClass(name);
        }
        return defineClass(name, bytes, 0, bytes.length);
    }

    private boolean delegateToRootClassLoader(String name) {
        return name.startsWith("java.") || name.startsWith("javax.")
                || name.startsWith("org.xml.sax") || name.startsWith("org.w3c.dom");
    }

    @Override
    protected URL findResource(String name) {
        log.debug("findResource({})", name);
        return super.findResource(name);
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        log.debug("findResources({})", name);
        return super.findResources(name);
    }

    @Override
    protected URL findResource(String moduleName, String name) throws IOException {
        log.debug("findResource({}, {})", moduleName, name);
        return super.findResource(moduleName, name);
    }

    @Override
    public URL getResource(String name) {
        log.debug("getResource({})", name);
        URI uri = URI.create("string:///" + name);

        if (overlay.content.containsKey(uri)) {
            try {
                return uri.toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        if (data.content.containsKey(uri)) {
            try {
                return uri.toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
        return super.getResource(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        log.debug("getResourceAsStream({})", name);
        return super.getResourceAsStream(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        log.debug("getResources({})", name);
        URI uri = URI.create("string:///" + name);
        if (!data.content.containsKey(uri) && !overlay.content.containsKey(uri))
            uri = null;
        URI uri2 = uri;

        Enumeration<URL> r = super.getResources(name);
        return new ConcatenatingEnumeration<URL>(uri2 == null ? new URL[0] : new URL[] { uri2.toURL() }, r);
    }

    public void defineOverlay(OverlayInMemoryFile... files) {
        overlay = new InMemoryData();
        for (OverlayInMemoryFile file : files) {
            if (overlay.content.containsKey(file.toUri()))
                throw new IllegalArgumentException("Overlaying two resources with the same name has not been implemented yet.");
            overlay.content.put(file.toUri(), file.getCharContent(true).toString().getBytes(UTF_8));
        }
        InMemoryURLStreamHandler.activateOverlay(overlay);
    }
}
