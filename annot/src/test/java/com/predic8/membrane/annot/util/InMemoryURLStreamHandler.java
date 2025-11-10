package com.predic8.membrane.annot.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;

/**
 * Helper to serve resources from the in-memory file system (and potentially its overlay file system).
 */
public class InMemoryURLStreamHandler extends URLStreamHandler {
    private static final Logger log = LoggerFactory.getLogger(InMemoryURLStreamHandler.class);
    private static InMemoryData data, overlay;

    static {
        URL.setURLStreamHandlerFactory(protocol -> new InMemoryURLStreamHandler());
    }

    public static void activate(InMemoryData data) {
        InMemoryURLStreamHandler.data = data;
    }

    public static void activateOverlay(InMemoryData overlay) {
        InMemoryURLStreamHandler.overlay = overlay;
    }

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        log.debug("openConnection({})", u);
        return new URLConnection(u) {
            @Override
            public void connect() throws IOException {

            }

            @Override
            public InputStream getInputStream() throws IOException {
                try {
                    URI uri = u.toURI();
                    if (overlay != null && overlay.content.containsKey(uri))
                        return new ByteArrayInputStream(overlay.content.get(uri));
                    return new ByteArrayInputStream(data.content.get(uri));
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

}
