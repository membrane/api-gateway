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
import org.slf4j.*;

import java.io.*;
import java.nio.charset.*;

public class XMLPrettifier implements Prettifier {

    private static final Logger log = LoggerFactory.getLogger(XMLPrettifier.class);

    @Override
    public byte[] prettify(byte[] c) {
        try {
            StringWriter sw = new StringWriter();
            XMLBeautifier xb = new XMLBeautifier(new StandardXMLBeautifierFormatter(sw, 4));
            xb.parse(new ByteArrayInputStream(c));

            return sw.toString().getBytes(xb.getDetectedEncoding() /* TODO charset from parse */);
        } catch (Exception e) {
            log.debug("", e);
            return c;
        }
    }

    @Override
    public byte[] prettify(byte[] c, Charset charset) {
        try {
            StringWriter sw = new StringWriter(250);
            XMLBeautifier xb = new XMLBeautifier(new StandardXMLBeautifierFormatter(sw, 4));

            // Important: give it the raw bytes
            xb.parse(new ByteArrayInputStream(c));

            if (xb.getDetectedEncoding() != null) {
                charset = Charset.forName(xb.getDetectedEncoding());
            }
            // If you want a BOM for UTF-16 output, use "UTF-16" (not BE/LE)
            Charset out = ("UTF-16BE".equalsIgnoreCase(charset.name()) ||
                           "UTF-16LE".equalsIgnoreCase(charset.name()))
                    ? java.nio.charset.StandardCharsets.UTF_16
                    : charset;

            return sw.toString().getBytes(out);
        } catch (Exception e) {
            log.debug("", e);
            return c;
        }
    }
}