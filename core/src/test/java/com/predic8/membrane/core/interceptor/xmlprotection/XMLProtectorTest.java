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

import com.predic8.membrane.core.interceptor.xmlprotection.XMLProtectionResult.Rejected;
import com.predic8.membrane.core.router.TestRouter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.predic8.membrane.core.interceptor.xmlprotection.XMLProtectionResult.ACCEPTED;
import static com.predic8.membrane.core.interceptor.xmlprotection.XMLProtectionResult.REWRITTEN;
import static com.predic8.membrane.core.util.RecordingServerTestUtil.freePort;
import static com.predic8.membrane.core.util.RecordingServerTestUtil.startRecordingServer;
import static com.predic8.membrane.core.util.xml.parser.HardenedStaxInputFactory.dtdAwareInputFactory;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class XMLProtectorTest {

    private static final int NAME_LENGTH_LIMIT = 1000;
    private static final int ATTRIBUTE_NAME_LENGTH_LIMIT = 1000;
    private static final int ATTRIBUTE_LIMIT = 1000;
    private static final int DEPTH_LIMIT = 1000;

    private byte[] input, output;

    private static XMLLimits limits(boolean removeDTD) {
        return new XMLLimits(NAME_LENGTH_LIMIT, ATTRIBUTE_NAME_LENGTH_LIMIT, ATTRIBUTE_LIMIT, DEPTH_LIMIT, removeDTD);
    }

    private static XMLLimits maxDepth(int maxDepth) {
        return new XMLLimits(NAME_LENGTH_LIMIT, ATTRIBUTE_NAME_LENGTH_LIMIT, ATTRIBUTE_LIMIT, maxDepth, true);
    }

    private static XMLLimits maxElementNameLength(int maxElementNameLength) {
        return new XMLLimits(maxElementNameLength, ATTRIBUTE_NAME_LENGTH_LIMIT, ATTRIBUTE_LIMIT, DEPTH_LIMIT, true);
    }

    private static XMLLimits maxAttributeNameLength(int maxAttributeNameLength) {
        return new XMLLimits(NAME_LENGTH_LIMIT, maxAttributeNameLength, ATTRIBUTE_LIMIT, DEPTH_LIMIT, true);
    }

    private static XMLLimits maxAttributeCount(int maxAttributeCount) {
        return new XMLLimits(NAME_LENGTH_LIMIT, ATTRIBUTE_NAME_LENGTH_LIMIT, maxAttributeCount, DEPTH_LIMIT, true);
    }

    private XMLProtectionResult runOn(String resource) throws Exception {
        return runOn(resource, limits(true));
    }

    private XMLProtectionResult runOn(String resource, XMLLimits limits) throws Exception {
        try (var is = this.getClass().getResourceAsStream(resource)) {
            input = is.readAllBytes();
        }
        if (resource.endsWith(".lmx")) {
            reverse();
        }
        return protect(input, limits);
    }

    private XMLProtectionResult protect(String xml, XMLLimits limits) throws Exception {
        return protect(xml.getBytes(UTF_8), limits);
    }

    private XMLProtectionResult protect(byte[] xml, XMLLimits limits) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos, UTF_8);
        XMLProtectionResult result = new XMLProtector(writer, dtdAwareInputFactory(limits.jaxpNameLimit()), limits, UTF_8)
                .protect(new InputStreamReader(new ByteArrayInputStream(xml), UTF_8));
        writer.flush(); // Flush before calling baos.toByteArray() to avoid truncated output on some JDKs
        output = result instanceof Rejected ? null : baos.toByteArray();
        return result;
    }

    private void reverse() {
        for (int i = 0, j = input.length - 1; i < j; i++, j--) {
            byte tmp = input[i];
            input[i] = input[j];
            input[j] = tmp;
        }
    }

    private static String reasonOf(XMLProtectionResult result) {
        return assertInstanceOf(Rejected.class, result).reason();
    }

    @Test
    void invariant() throws Exception {
        // A document with no DTD to remove is left alone, so the caller may forward it unchanged
        assertEquals(ACCEPTED, runOn("/customer.xml"));
    }

    @Test
    void aDtdThatIsKeptDoesNotCountAsARewrite() throws Exception {
        String xml = "<?xml version='1.0'?><!DOCTYPE foo [<!ELEMENT foo ANY>]><foo/>";
        assertEquals(ACCEPTED, protect(xml, limits(false)));
    }

    @Test
    void notWellformed() throws Exception {
        assertTrue(reasonOf(runOn("/xml/not-wellformed.xml")).contains("Not well-formed XML"));
    }

    @Test
    void DTDRemoval() throws Exception {
        // REWRITTEN, not ACCEPTED: the caller must forward the copy without the DTD
        assertEquals(REWRITTEN, runOn("/xml/entity-expansion.lmx"));
        assertTrue(output.length < input.length / 2);
        assertFalse(new String(output, UTF_8).contains("ENTITY"));
    }

    @Test
    void expandingEntities() throws Exception {
        assertEquals(ACCEPTED, runOn("/xml/entity-expansion.lmx", limits(false)));
        assertTrue(output.length > input.length / 2);
        assertTrue(new String(output, UTF_8).contains("ENTITY"));
    }

    @Test
    void externalEntities() throws Exception {
        assertTrue(reasonOf(runOn("/xml/entity-external.xml", limits(false))).contains("External entity"));
    }

    @Test
    void longElementName() throws Exception {
        assertTrue(reasonOf(runOn("/xml/long-element-name.xml")).contains("Element name"));
    }

    @Test
    void elementNameOverLimitNamesItsOwnReason() throws Exception {
        assertTrue(reasonOf(protect("<%s/>".formatted("a".repeat(1500)), maxElementNameLength(1200)))
                .contains("Element name of 1500 characters"));
    }

    @Test
    void elementNameLimitAboveTheJdkDefaultIsHonoured() throws Exception {
        // The JDK's own jdk.xml.maxXMLNameLimit of 1000 must not silently cap a larger configuration
        assertEquals(ACCEPTED, protect("<%s/>".formatted("a".repeat(1500)), maxElementNameLength(2000)));
    }

    @Test
    void elementPrefixCountsTowardsTheNameLength() throws Exception {
        // The local part is 3 characters; only counting it would let the prefix through unbounded
        String xml = "<%s:foo xmlns:%s='urn:x'/>".formatted("p".repeat(1500), "p".repeat(1500));
        assertTrue(reasonOf(protect(xml, maxElementNameLength(1200))).contains("Element name of 1504 characters"));
    }

    @Test
    void attributeNameOverLimitIsRejected() throws Exception {
        String xml = "<foo %s='1'/>".formatted("a".repeat(1500));
        assertTrue(reasonOf(protect(xml, maxAttributeNameLength(1200)))
                .contains("Attribute name of 1500 characters"));
    }

    @Test
    void attributeNameAtLimitPasses() throws Exception {
        assertEquals(ACCEPTED, protect("<foo %s='1'/>".formatted("a".repeat(1200)), maxAttributeNameLength(1200)));
    }

    @Test
    void attributePrefixCountsTowardsTheNameLength() throws Exception {
        String prefix = "p".repeat(1500);
        String xml = "<foo xmlns:%s='urn:x' %s:id='1'/>".formatted(prefix, prefix);
        assertTrue(reasonOf(protect(xml, maxAttributeNameLength(1200)))
                .contains("Attribute name of 1503 characters"));
    }

    @Test
    void attributeNameLimitAboveTheJdkDefaultIsHonoured() throws Exception {
        // The JAXP backstop is kept above our own limit, so our limit is the one that decides
        assertEquals(ACCEPTED, protect("<foo %s='1'/>".formatted("a".repeat(1500)), maxAttributeNameLength(2000)));
    }

    @Test
    void unlimitedAttributeNameLengthDisablesCheck() throws Exception {
        assertEquals(ACCEPTED, protect("<foo %s='1'/>".formatted("a".repeat(5000)), maxAttributeNameLength(-1)));
    }

    @Test
    void namespaceUriBeyondTheBackstopIsStillRejected() throws Exception {
        // A namespace URI is never inspected by XMLProtector - this is the JAXP name cap doing its
        // backstop job. Derived from the cap, so the test keeps its meaning if the headroom changes.
        XMLLimits limits = maxAttributeNameLength(1200);
        String xml = "<foo xmlns='urn:%s'/>".formatted("u".repeat(limits.jaxpNameLimit() + 1));

        assertTrue(reasonOf(protect(xml, limits)).contains("Not well-formed XML"));
    }

    @Test
    void namespaceUriWithinTheBackstopIsNotInspected() throws Exception {
        // The flip side: XMLProtector's own name limits do not apply to namespace URIs, so a URI
        // far longer than maxAttributeNameLength passes as long as it stays under the cap
        assertEquals(ACCEPTED, protect("<foo xmlns='urn:%s'/>".formatted("u".repeat(5000)),
                maxAttributeNameLength(1200)));
    }

    @Test
    void manyAttributes() throws Exception {
        assertTrue(reasonOf(runOn("/xml/many-attributes.xml")).contains("attributes"));
    }

    @Test
    void attributeCountAtLimitPasses() throws Exception {
        assertEquals(ACCEPTED, protect("<foo a='1' b='2' c='3'/>", maxAttributeCount(3)));
    }

    @Test
    void oneAttributeOverLimitIsRejected() throws Exception {
        assertTrue(reasonOf(protect("<foo a='1' b='2' c='3' d='4'/>", maxAttributeCount(3)))
                .contains("more than the 3 allowed attributes"));
    }

    @Test
    void unlimitedAttributeCountDisablesCheck() throws Exception {
        assertEquals(ACCEPTED, protect("<foo a='1' b='2' c='3' d='4'/>", maxAttributeCount(-1)));
    }

    @Test
    void rejectsExternalDtdSubsetWhenNotRemovingDtd() throws Exception {
        // When removeDTD=false, a bare DOCTYPE with a SYSTEM reference has no entity declarations
        // and would previously pass the entity check undetected, writing the external reference to
        // output. It must be rejected instead.
        String xml = "<?xml version='1.0'?><!DOCTYPE r SYSTEM 'http://127.0.0.1:1/x.dtd'><r/>";
        assertTrue(reasonOf(protect(xml, limits(false))).contains("External DTD subset reference"));
    }

    @Test
    void allowsDoctypeNameContainingKeywordSubstringWhenNotRemovingDtd() throws Exception {
        // PUBLICATIONS contains "PUBLIC" but is not an external ID keyword — must not be rejected
        String xml = "<?xml version='1.0'?><!DOCTYPE PUBLICATIONS [<!ELEMENT PUBLICATIONS ANY>]><PUBLICATIONS/>";
        assertEquals(ACCEPTED, protect(xml, limits(false)));
    }

    @Test
    void allowsDoctypeRootNamedSystemOrPublic() throws Exception {
        // The DOCTYPE root name itself may legally be "SYSTEM" or "PUBLIC" — must not be
        // mistaken for an external identifier keyword.
        String xml = "<?xml version='1.0'?><!DOCTYPE SYSTEM []><SYSTEM/>";
        assertEquals(ACCEPTED, protect(xml, limits(false)));
    }

    @Test
    void doesNotFetchExternalDtd() throws Exception {
        var received = new AtomicBoolean(false);
        int port = freePort();
        TestRouter router = startRecordingServer(port, received);
        XMLProtectionResult result;
        try {
            String xml = "<?xml version='1.0'?><!DOCTYPE r SYSTEM 'http://127.0.0.1:%d/x.dtd'><r/>".formatted(port);
            result = protect(xml, limits(true));
        } finally {
            router.stop();
        }
        assertEquals(REWRITTEN, result, "XMLProtector should report the document as rewritten after stripping the DTD");
        assertFalse(new String(output, UTF_8).contains("DOCTYPE"), "DTD must be removed from output");
        assertFalse(received.get(), "XMLProtector must not fetch external DTD");
    }

    private static String nested(int depth) {
        StringBuilder sb = new StringBuilder("<?xml version='1.0'?>");
        for (int i = 0; i < depth; i++) sb.append("<a>");
        for (int i = 0; i < depth; i++) sb.append("</a>");
        return sb.toString();
    }

    @Test
    void tooDeeplyNested() throws Exception {
        assertTrue(reasonOf(protect(nested(51), maxDepth(50))).contains("nesting depth 51"));
    }

    @Test
    void nestingWithinLimit() throws Exception {
        assertEquals(ACCEPTED, protect(nested(50), maxDepth(50)));
    }

    @Test
    void wideButShallowPasses() throws Exception {
        StringBuilder sb = new StringBuilder("<?xml version='1.0'?><root>");
        for (int i = 0; i < 500; i++) sb.append("<a/>");
        sb.append("</root>");
        assertEquals(ACCEPTED, protect(sb.toString(), maxDepth(50)));
    }

    @Test
    void nestingDepthAboveTheJdkDefaultIsHonoured() throws Exception {
        // JAXP's own maxElementDepth of 100 must not silently cap a larger configuration
        assertEquals(ACCEPTED, protect(nested(150), maxDepth(200)));
    }

    @Test
    void unlimitedDepthDisablesCheck() throws Exception {
        assertEquals(ACCEPTED, protect(nested(2000), maxDepth(-1)));
    }
}
