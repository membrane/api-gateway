/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import static tools.jackson.core.json.JsonReadFeature.*;

public class JSONPrettifier implements Prettifier {

    private static final Logger log = LoggerFactory.getLogger(JSONPrettifier.class);

    private static final JsonFactory JSON_FACTORY = JsonFactory.builder()
            .enable(ALLOW_JAVA_COMMENTS)
            .enable(ALLOW_TRAILING_COMMA)
            .enable(ALLOW_SINGLE_QUOTES)
            .enable(ALLOW_UNQUOTED_PROPERTY_NAMES)
            .build();

    private static final ObjectMapper om = new ObjectMapper(JSON_FACTORY);


    public static final JSONPrettifier INSTANCE = new JSONPrettifier();

    private JSONPrettifier() {
    }

    /**
     * Assumes UTF-8 encoding cause it is JSON.
     * @return byte[] always UTF-8 encoded
     */
    @Override
    public byte[] prettify(byte[] c, Charset charset) {
        try {
            return om.writerWithDefaultPrettyPrinter().writeValueAsBytes(om.readTree(c));
        } catch (JacksonException e) {
            log.debug("Failed to prettify JSON. Returning input unmodified.", e);
            return c;
        }
    }

    /**
     * Assumes UTF-8 encoding cause it is JSON.
     * @return byte[] always UTF-8 encoded
     */
    @Override
    public byte[] prettify(InputStream is, Charset charset) throws IOException {
        return om.writerWithDefaultPrettyPrinter().writeValueAsBytes(om.readTree(is));
    }
}
