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

import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import static java.nio.charset.StandardCharsets.UTF_8;

public class InnerFileObject extends SimpleJavaFileObject {
    private static final Logger log = LoggerFactory.getLogger(InnerFileObject.class);

    private final InMemoryData data;

    public InnerFileObject(InMemoryData data, URI uri, Kind kind) {
        super(uri, kind);
        this.data = data;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        byte[] bytes = data.content.get(toUri());
        if (bytes == null)
            return "";
        return new String(bytes, UTF_8);
    }

    @Override
    public OutputStream openOutputStream() {
        return new ByteArrayOutputStream() {
            @Override
            public void flush() throws IOException {
                super.flush();
                data.content.put(toUri(), toByteArray());
                log.debug("wrote {} : {}", toUri(), new String(toByteArray(), UTF_8));
            }

            @Override
            public void close() throws IOException {
                super.close();
                data.content.put(toUri(), toByteArray());
                log.debug("wrote {} : {}", toUri(), new String(toByteArray(), UTF_8));
            }
        };
    }
}
