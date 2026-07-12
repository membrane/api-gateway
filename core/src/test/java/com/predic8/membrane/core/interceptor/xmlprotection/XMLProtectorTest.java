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

import com.predic8.membrane.core.HttpRouter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.predic8.membrane.core.interceptor.xmlprotection.XMLProtector.getHeaderAfterRootName;
import static com.predic8.membrane.core.util.xml.parser.HardenedSaxParserTest.freePort;
import static com.predic8.membrane.core.util.xml.parser.HardenedSaxParserTest.startRecordingServer;
import static java.nio.charset.StandardCharsets.UTF_8;
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
        try (var is = this.getClass().getResourceAsStream(resource)) {
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

    @Test
    void rejectsExternalDtdSubsetWhenNotRemovingDtd() {
        // When removeDTD=false, a bare DOCTYPE with a SYSTEM reference has no entity declarations
        // and would previously pass checkExternalEntities() undetected, writing the external
        // reference to output. It must now throw instead.
        assertThrows(XMLProtectionException.class, () -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            var protector = new XMLProtector(new OutputStreamWriter(baos, UTF_8), false, 1000, 1000);
            String xml = "<?xml version='1.0'?><!DOCTYPE r SYSTEM 'http://127.0.0.1:1/x.dtd'><r/>";
            protector.protect(new InputStreamReader(new ByteArrayInputStream(xml.getBytes(UTF_8)), UTF_8));
        });
    }

    @Test
    void allowsDoctypeNameContainingKeywordSubstringWhenNotRemovingDtd() throws Exception {
        // PUBLICATIONS contains "PUBLIC" but is not an external ID keyword — must not be rejected
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        var protector = new XMLProtector(new OutputStreamWriter(baos, UTF_8), false, 1000, 1000);
        String xml = "<?xml version='1.0'?><!DOCTYPE PUBLICATIONS [<!ELEMENT PUBLICATIONS ANY>]><PUBLICATIONS/>";
        assertTrue(protector.protect(new InputStreamReader(new ByteArrayInputStream(xml.getBytes(UTF_8)), UTF_8)));
    }

    @Test
    void allowsDoctypeRootNamedSystemOrPublic() throws Exception {
        // The DOCTYPE root name itself may legally be "SYSTEM" or "PUBLIC" — must not be
        // mistaken for an external identifier keyword.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        var protector = new XMLProtector(new OutputStreamWriter(baos, UTF_8), false, 1000, 1000);
        String xml = "<?xml version='1.0'?><!DOCTYPE SYSTEM []><SYSTEM/>";
        assertTrue(protector.protect(new InputStreamReader(new ByteArrayInputStream(xml.getBytes(UTF_8)), UTF_8)));
    }

    @Test
    void doesNotFetchExternalDtd() throws Exception {
        var received = new AtomicBoolean(false);
        int port = freePort();
        HttpRouter router = startRecordingServer(port, received);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean result;
        try {
            var protector = new XMLProtector(new OutputStreamWriter(baos, UTF_8), true, 1000, 1000);
            String xml = "<?xml version='1.0'?><!DOCTYPE r SYSTEM 'http://127.0.0.1:%d/x.dtd'><r/>".formatted(port);
            result = protector.protect(new InputStreamReader(new ByteArrayInputStream(xml.getBytes(UTF_8)), UTF_8));
        } finally {
            router.shutdown();
        }
        assertTrue(result, "XMLProtector should succeed after stripping the DTD");
        assertFalse(new String(baos.toByteArray(), UTF_8).contains("DOCTYPE"), "DTD must be removed from output");
        assertFalse(received.get(), "XMLProtector must not fetch external DTD");
    }

    @Test
    void getHeaderAfterRootName_stripsSimpleRootName() {
        assertEquals(" SYSTEM 'x.dtd'", getHeaderAfterRootName("<!DOCTYPE r SYSTEM 'x.dtd'"));
    }

    @Test
    void getHeaderAfterRootName_stripsRootNameNamedSystem() {
        // The keyword remaining after the root name is skipped must not be "SYSTEM" itself
        assertEquals(" []", getHeaderAfterRootName("<!DOCTYPE SYSTEM []"));
    }

    @Test
    void getHeaderAfterRootName_stripsRootNameNamedPublic() {
        assertEquals(" []", getHeaderAfterRootName("<!DOCTYPE PUBLIC []"));
    }

    @Test
    void getHeaderAfterRootName_stripsRootNameContainingKeywordSubstring() {
        assertEquals(" ", getHeaderAfterRootName("<!DOCTYPE PUBLICATIONS "));
    }

    @Test
    void getHeaderAfterRootName_skipsExtraWhitespaceBeforeRootName() {
        assertEquals("   SYSTEM 'x'", getHeaderAfterRootName("<!DOCTYPE   r   SYSTEM 'x'"));
    }

    @Test
    void getHeaderAfterRootName_handlesRootNameWithNoTrailingContent() {
        assertEquals("", getHeaderAfterRootName("<!DOCTYPE r"));
    }

    @Test
    void getHeaderAfterRootName_handlesMissingDoctypeKeywordDefensively() {
        // Should never happen per the DTD contract, but must not throw — falls back to
        // skipping the header's first whitespace-delimited token.
        assertEquals(" bar baz", getHeaderAfterRootName("foo bar baz"));
    }

    @Test
    void getHeaderAfterRootName_handlesEmptyHeader() {
        assertEquals("", getHeaderAfterRootName(""));
    }
}
