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

package com.predic8.membrane.core.prettifier;

import com.predic8.xml.beautifier.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.io.*;
import java.nio.charset.*;

import static com.predic8.membrane.core.util.TextUtil.*;
import static java.nio.charset.StandardCharsets.*;

public class XMLPrettifier implements Prettifier {

    public static final XMLPrettifier INSTANCE = new XMLPrettifier();
    private static final Logger log = LoggerFactory.getLogger(XMLPrettifier.class);

    private XMLPrettifier() {
    }

    /**
     * If you want a BOM for UTF-16 output, use "UTF-16" (not BE/LE)
     *
     * @param charset
     * @return
     */
    private static @NotNull Charset getOutputCharset(Charset charset) {
        return ("UTF-16BE".equalsIgnoreCase(charset.name()) ||
                "UTF-16LE".equalsIgnoreCase(charset.name()))
                ? UTF_16
                : getCharsetOrDefault(charset);
    }

    @Override
    public byte[] prettify(byte[] c) {
        try {
            return prettify(c, UTF_8);
        } catch (Exception e) {
            log.debug("Failed to prettify XML input (byte[]). Returning original bytes.", e);
            return c;
        }
    }

    @Override
    public byte[] prettify(InputStream is, Charset charset) throws IOException {
            StringWriter sw = new StringWriter(250);
            XMLBeautifier xb = new XMLBeautifier(new StandardXMLBeautifierFormatter(sw, 4));
            xb.parse(is);
            return sw.toString().getBytes(getCharset( xb.getDetectedEncoding()) /* Charset from parse */);
    }

    @Override
    public byte[] prettify(byte[] c, Charset charset) {
        try {
            StringWriter sw = new StringWriter(250);
            XMLBeautifier xb = new XMLBeautifier(new StandardXMLBeautifierFormatter(sw, 4));

            // Important: give it the raw bytes
            xb.parse(new ByteArrayInputStream(c));

            if (xb.getDetectedEncoding() != null) {
                try {
                    charset = Charset.forName(xb.getDetectedEncoding());
                } catch (IllegalArgumentException iae) {
                    log.debug("Unknown detected XML encoding '{}'. Falling back to requested charset {}.",
                            xb.getDetectedEncoding(), charset);
                }
            }

            return sw.toString().getBytes(getOutputCharset(charset));
        } catch (Exception e) {
            log.debug("Failed to prettify XML. Returning input unmodified.", e);
            return c;
        }
    }
}