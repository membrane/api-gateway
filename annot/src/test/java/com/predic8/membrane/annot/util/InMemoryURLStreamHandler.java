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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
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
        URL.setURLStreamHandlerFactory(protocol -> "string".equals(protocol) ? new InMemoryURLStreamHandler() : null);
    }

    public static void activate(InMemoryData data) {
        InMemoryURLStreamHandler.data = data;
    }

    public static void activateOverlay(InMemoryData overlay) {
        InMemoryURLStreamHandler.overlay = overlay;
    }

    @Override
    protected URLConnection openConnection(URL u) {
        log.debug("openConnection({})", u);
        return new URLConnection(u) {
            @Override
            public void connect() {

            }

            @Override
            public InputStream getInputStream() throws IOException {
                try {
                    URI uri = u.toURI();
                    if (overlay != null && overlay.content.containsKey(uri)) {
                        byte[] buffer = overlay.content.get(uri);
                        if (buffer == null)
                            throw new FileNotFoundException("No in-memory resource for " + uri);
                        return new ByteArrayInputStream(buffer);
                    }
                    return new ByteArrayInputStream(data.content.get(uri));
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

}
