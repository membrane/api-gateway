/* Copyright 2010, 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.xmlprotection;

import org.junit.jupiter.api.*;

import java.io.*;

import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

class XMLProtectorTest {

    private byte[] input, output;

    private boolean runOn(String resource) throws Exception {
        return runOn(resource, true);
    }

    private boolean runOn(String resource, boolean removeDTD) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos, UTF_8);
        XMLProtector xmlProtector = new XMLProtector(writer, removeDTD, 1000, 1000);
        try(var is = this.getClass().getResourceAsStream(resource)) {
            input = is.readAllBytes();
        }
        if (resource.endsWith(".lmx")) {
            reverse();
        }
        boolean valid = xmlProtector.protect(new InputStreamReader(new ByteArrayInputStream(input), UTF_8));
        writer.flush(); // Flush before calling baos.toByteArray() to avoid truncated output on some JDKs
        if (!valid) {
            output = null;
        } else {
            output = baos.toByteArray();
        }
        return valid;
    }

    private void reverse() {
        for (int i = 0, j = input.length - 1; i < j; i++, j--) {
            byte tmp = input[i];
            input[i] = input[j];
            input[j] = tmp;
        }
    }

    @Test
    void invariant() throws Exception {
        assertTrue(runOn("/customer.xml"));
    }

    @Test
    void notWellformed() throws Exception {
        assertFalse(runOn("/xml/not-wellformed.xml"));
    }

    @Test
    void DTDRemoval() throws Exception {
        assertTrue(runOn("/xml/entity-expansion.lmx"));
        assertTrue(output.length < input.length / 2);
        assertFalse(new String(output, UTF_8).contains("ENTITY"));
    }

    @Test
    void expandingEntities() throws Exception {
        assertTrue(runOn("/xml/entity-expansion.lmx", false));
        assertTrue(output.length > input.length / 2);
        assertTrue(new String(output, UTF_8).contains("ENTITY"));
    }

    @Test
    void externalEntities() {
        assertThrows(XMLProtectionException.class, () -> runOn("/xml/entity-external.xml", false));
    }

    @Test
    void longElementName() throws Exception {
        assertFalse(runOn("/xml/long-element-name.xml"));
    }

    @Test
    void manyAttributes() throws Exception {
        assertFalse(runOn("/xml/many-attributes.xml"));
    }
}
