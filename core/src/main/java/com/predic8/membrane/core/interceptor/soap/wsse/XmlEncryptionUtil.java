/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.soap.wsse;

import com.predic8.membrane.core.util.xml.XMLUtil;
import com.predic8.membrane.core.util.xml.parser.HardenedXmlParser;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.xml.XMLConstants.*;
import static javax.xml.transform.OutputKeys.*;

/**
 * XML Encryption helpers shared by {@link EncryptSecurePart} and {@link DecryptValidatePart}:
 * namespace fixup, fragment serialization and parsing, the AES-GCM and RSA-OAEP primitives, and the
 * {@code xenc:} DOM structures.
 * <p>
 * Everything here is mechanical and two-directional. The <i>policy</i> - which algorithms are
 * accepted, which fault a failure becomes, whether a certificate may encrypt - lives in the parts,
 * next to the javadoc explaining it.
 */
final class XmlEncryptionUtil {

    private XmlEncryptionUtil() {
    }

    /**
     * The XML Encryption namespace. Every <i>element</i> name lives here - {@code EncryptedData},
     * {@code EncryptedKey}, {@code EncryptionMethod}, {@code CipherData}, {@code CipherValue},
     * {@code ReferenceList}, {@code DataReference} - and it is unchanged in XML Encryption 1.1. Only
     * the newer <i>algorithm</i> URIs moved to {@link #XENC11_NS}; mixing the two up is silent,
     * because both are legal namespaces and nothing validates the combination.
     */
    static final String XENC_NS = "http://www.w3.org/2001/04/xmlenc#";
    /** XML Encryption 1.1, which is where the GCM and negotiable-MGF OAEP algorithm URIs live. */
    static final String XENC11_NS = "http://www.w3.org/2009/xmlenc11#";

    static final String TYPE_CONTENT = XENC_NS + "Content";
    static final String TYPE_ELEMENT = XENC_NS + "Element";

    static final String AES128_GCM = XENC11_NS + "aes128-gcm";
    static final String AES256_GCM = XENC11_NS + "aes256-gcm";

    /**
     * RSA-OAEP with a negotiable mask generation function, as opposed to the 1.0
     * {@code xmlenc#rsa-oaep-mgf1p}, which bakes MGF1-SHA-1 into the URI itself. Pairing that older
     * URI with a SHA-256 {@code ds:DigestMethod} is self-contradictory, and WSS4J reads MGF1-SHA-1
     * out of it whatever child element accompanies it - so only the 1.1 URI can express what this
     * gateway actually computes.
     */
    static final String RSA_OAEP = XENC11_NS + "rsa-oaep";
    static final String MGF1_SHA256 = XENC11_NS + "mgf1sha256";
    static final String SHA256_DIGEST = XENC_NS + "sha256";

    /** 96 bits: the IV length GCM uses natively, and the one XML Encryption 1.1 fixes. */
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    // Thread-safe, and constructing one per message would repeat provider lookup and seeding.
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ---------------------------------------------------------------- namespaces

    /**
     * Every namespace declaration in scope at {@code from}, as prefix (empty string for the default
     * {@code xmlns}) to URI, with the nearest declaration winning.
     * <p>
     * This is what makes an encrypted fragment self-contained. The plaintext of an
     * {@code xenc:EncryptedData} is parsed on the other side with no surrounding document, so a
     * prefix its content uses - the SOAP prefix, a business namespace, anything declared on an
     * ancestor - is unbound unless the declaration travels with it.
     */
    static Map<String, String> inScopeNamespaceDeclarations(Element from) {
        Map<String, String> declarations = new LinkedHashMap<>();
        for (Node node = from; node instanceof Element element; node = element.getParentNode()) {
            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attribute = (Attr) attributes.item(i);
                if (!XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) {
                    continue;
                }
                String prefix = XMLNS_ATTRIBUTE.equals(attribute.getName()) ? "" : attribute.getLocalName();
                // Both are bound by the XML specification and may not be redeclared; a parser
                // rejects an attempt to, so they must not travel with the fragment.
                if (XML_NS_PREFIX.equals(prefix) || XMLNS_ATTRIBUTE.equals(prefix)) {
                    continue;
                }
                // Walking outwards, so the first declaration seen for a prefix is the innermost one.
                declarations.putIfAbsent(prefix, attribute.getValue());
            }
        }
        return declarations;
    }

    /**
     * Adds {@code declarations} to {@code target}, without overwriting one it already makes itself.
     * <p>
     * All of them are added, not only those the fragment appears to use: whether a prefix is used
     * cannot be determined reliably, since it may appear inside an attribute value or in character
     * content ({@code xsi:type="tns:Foo"}), not just on element and attribute names. A surplus
     * declaration is inert, a missing one makes the fragment unparseable.
     */
    static void injectNamespaceDeclarations(Element target, Map<String, String> declarations) {
        for (Map.Entry<String, String> declaration : declarations.entrySet()) {
            String prefix = declaration.getKey();
            String localName = prefix.isEmpty() ? XMLNS_ATTRIBUTE : prefix;
            if (target.getAttributeNodeNS(XMLNS_ATTRIBUTE_NS_URI, localName) != null) {
                // The fragment declares this prefix itself; that declaration is the authoritative one.
                continue;
            }
            String qualifiedName = prefix.isEmpty() ? XMLNS_ATTRIBUTE : XMLNS_ATTRIBUTE + ":" + prefix;
            target.setAttributeNS(XMLNS_ATTRIBUTE_NS_URI, qualifiedName, declaration.getValue());
        }
    }

    // ---------------------------------------------------------------- serialization

    /**
     * {@code element} as standalone UTF-8 bytes, with no XML declaration and - importantly - no
     * indentation.
     * <p>
     * Deliberately not {@link XMLUtil#xmlNode2String(Node)}, which sets {@code INDENT=yes}: injected
     * whitespace survives the encrypt/decrypt round trip, so the receiver would not get back the
     * bytes that were encrypted. Under sign-then-encrypt that silently invalidates the digest of
     * every {@code ds:Reference} covering the affected subtree.
     */
    private static byte[] serializeElement(Element element) throws TransformerException {
        Transformer transformer = XMLUtil.newHardenedBestEffortTransformerFactory().newTransformer();
        transformer.setOutputProperty(OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(METHOD, "xml");
        transformer.setOutputProperty(ENCODING, UTF_8.name());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(element), new StreamResult(out));
        return out.toByteArray();
    }

    /**
     * The serialized form of {@code target} itself, for {@code Type="...#Element"} encryption.
     * <p>
     * Serialized from a deep clone so that the live document is left untouched if anything later
     * fails, and the declarations come from the target's parent, since the target keeps its own.
     */
    static byte[] serializeForElementEncryption(Element target) throws TransformerException {
        Element clone = (Element) target.cloneNode(true);
        if (target.getParentNode() instanceof Element parent) {
            injectNamespaceDeclarations(clone, inScopeNamespaceDeclarations(parent));
        }
        return serializeElement(clone);
    }

    /**
     * The serialized child nodes of {@code target}, for {@code Type="...#Content"} encryption.
     * <p>
     * The declarations come from {@code target} itself: they are in scope for its children, but the
     * element carrying them is not part of what gets encrypted.
     */
    static byte[] serializeForContentEncryption(Element target) throws TransformerException {
        Map<String, String> declarations = inScopeNamespaceDeclarations(target);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Node child = target.getFirstChild(); child != null; child = child.getNextSibling()) {
            switch (child.getNodeType()) {
                case Node.ELEMENT_NODE -> {
                    Element clone = (Element) child.cloneNode(true);
                    injectNamespaceDeclarations(clone, declarations);
                    out.writeBytes(serializeElement(clone));
                }
                case Node.TEXT_NODE, Node.CDATA_SECTION_NODE ->
                        out.writeBytes(escapeText(child.getNodeValue()).getBytes(UTF_8));
                case Node.COMMENT_NODE ->
                        out.writeBytes(("<!--" + child.getNodeValue() + "-->").getBytes(UTF_8));
                case Node.PROCESSING_INSTRUCTION_NODE ->
                        out.writeBytes(("<?" + child.getNodeName() + " " + child.getNodeValue() + "?>").getBytes(UTF_8));
                default -> throw new WsSecurityFaultException(WsSecurityFaultCode.INVALID_SECURITY,
                        "Cannot encrypt a " + child.getNodeName() + " node.");
            }
        }
        return out.toByteArray();
    }

    /**
     * Escapes character data.
     * <p>
     * A CDATA section is escaped rather than reproduced as one: the two are the same information, and
     * a receiver's parser reports character data either way. {@code \r} must be escaped numerically
     * because a literal carriage return in character data is normalized to {@code \n} by every
     * conforming parser, which would silently alter the payload across the round trip.
     */
    private static String escapeText(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\r", "&#13;");
    }

    private static String escapeAttributeValue(String value) {
        return escapeText(value).replace("\"", "&quot;")
                .replace("\n", "&#10;")
                .replace("\t", "&#9;");
    }

    /**
     * Parses decrypted plaintext back into nodes of {@code target}, as if it still sat where
     * {@code contextParent} is.
     * <p>
     * The plaintext is an XML <i>fragment</i> - possibly several elements, possibly text - which no
     * DOM parser accepts on its own, so it is parsed inside a synthetic wrapper that re-declares
     * every namespace in scope at its destination. The wrapper is discarded; only its children are
     * imported. It carries no prefix of its own, so it cannot collide with one the plaintext
     * declares, and a declaration inside the plaintext shadows the wrapper's by ordinary XML
     * scoping. (It does land in whatever default namespace was in scope at the destination, since
     * that declaration is copied onto it like any other - which is immaterial, as the wrapper never
     * leaves this method.)
     * <p>
     * The parser is the hardened one because this content is attacker-supplied: it is whatever the
     * ciphertext happened to decrypt to, and it is parsed before anything has authenticated it
     * beyond the AEAD tag.
     */
    static List<Node> parsePlaintextFragment(byte[] plaintext, Element contextParent, Document target) {
        StringBuilder wrapper = new StringBuilder("<mem-frag");
        for (Map.Entry<String, String> declaration : inScopeNamespaceDeclarations(contextParent).entrySet()) {
            String prefix = declaration.getKey();
            wrapper.append(' ')
                    .append(prefix.isEmpty() ? XMLNS_ATTRIBUTE : XMLNS_ATTRIBUTE + ":" + prefix)
                    .append("=\"").append(escapeAttributeValue(declaration.getValue())).append('"');
        }
        wrapper.append('>');

        ByteArrayOutputStream document = new ByteArrayOutputStream();
        document.writeBytes(wrapper.toString().getBytes(UTF_8));
        // Appended raw, so plaintext that begins with an XML declaration is no longer at offset 0 and
        // the parse fails - which is the right outcome, since that is not a fragment we produced.
        document.writeBytes(plaintext);
        document.writeBytes("</mem-frag>".getBytes(UTF_8));

        InputSource source = new InputSource(new ByteArrayInputStream(document.toByteArray()));
        source.setEncoding(UTF_8.name());
        Document parsed = HardenedXmlParser.getInstance().parse(source);

        // Snapshotted into a list rather than returned as the live NodeList: the caller re-parents
        // these nodes, which would shrink the list under it as it iterated.
        List<Node> imported = new ArrayList<>();
        for (Node child = parsed.getDocumentElement().getFirstChild(); child != null; child = child.getNextSibling()) {
            imported.add(target.importNode(child, true));
        }
        return imported;
    }

    // ---------------------------------------------------------------- primitives

    /** The content encryption key length, in bytes, that {@code dataAlgorithm} requires. */
    static int cekLengthFor(String dataAlgorithm) {
        return AES256_GCM.equals(dataAlgorithm) ? 32 : 16;
    }

    static SecretKey generateContentEncryptionKey(String dataAlgorithm) throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(cekLengthFor(dataAlgorithm) * 8, SECURE_RANDOM);
        return generator.generateKey();
    }

    /**
     * AES-GCM encryption, returning {@code IV || ciphertext || tag} - the layout XML Encryption 1.1
     * prescribes for the {@code xenc:CipherValue} of a GCM algorithm.
     * <p>
     * The IV is generated here rather than taken as a parameter so that a caller cannot reuse one.
     * A single {@code encrypt} part uses one content encryption key for all of its targets, and
     * repeating an IV under one GCM key is not a weakening but a break: it leaks the authentication
     * subkey and with it the ability to forge tags.
     * <p>
     * Nothing is spliced by hand: JCA's {@code doFinal} already appends the authentication tag.
     */
    static byte[] encryptGcm(SecretKey contentEncryptionKey, byte[] plaintext) throws GeneralSecurityException {
        byte[] iv = new byte[GCM_IV_BYTES];
        SECURE_RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, contentEncryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);

        byte[] out = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
        return out;
    }

    /**
     * The inverse of {@link #encryptGcm}: the first 12 bytes are the IV, and the whole remainder -
     * trailing authentication tag included - goes to {@code doFinal}, which verifies it.
     */
    static byte[] decryptGcm(SecretKey contentEncryptionKey, byte[] ivAndCiphertext) throws GeneralSecurityException {
        if (ivAndCiphertext.length < GCM_IV_BYTES + GCM_TAG_BITS / 8) {
            throw new GeneralSecurityException("Ciphertext is too short to hold an IV and an authentication tag.");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, contentEncryptionKey,
                new GCMParameterSpec(GCM_TAG_BITS, ivAndCiphertext, 0, GCM_IV_BYTES));
        return cipher.doFinal(ivAndCiphertext, GCM_IV_BYTES, ivAndCiphertext.length - GCM_IV_BYTES);
    }

    /**
     * An RSA-OAEP cipher with SHA-256 as both the label digest and the MGF1 digest.
     * <p>
     * The {@link OAEPParameterSpec} is not optional decoration. The convenience transformation name
     * {@code RSA/ECB/OAEPWithSHA-256AndMGF1Padding} fixes only the label digest, and leaves MGF1 on
     * the provider default - historically SHA-1. A cipher built that way computes something other
     * than the {@code xenc11:MGF Algorithm="...#mgf1sha256"} this gateway advertises, and interop
     * against Santuario/WSS4J, which honour that element, fails. Naming both digests explicitly is
     * what CXF and WSS4J do, and it is independent of provider defaults.
     */
    static Cipher rsaOaepCipher(int mode, Key key) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        cipher.init(mode, key, new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        return cipher;
    }

    // ---------------------------------------------------------------- xenc structures

    /**
     * Creates {@code xenc:<localName>} with an explicit {@code xmlns:xenc} declaration.
     * <p>
     * Explicit because DOM only materializes namespace declarations when the document is serialized:
     * a {@code signature} listed after an {@code encrypt} canonicalizes this subtree before that
     * happens, and would digest a form without the declaration while the receiver validates one with
     * it.
     */
    static Element createXencElement(Document doc, String localName) {
        Element element = doc.createElementNS(XENC_NS, "xenc:" + localName);
        element.setAttributeNS(XMLNS_ATTRIBUTE_NS_URI, "xmlns:xenc", XENC_NS);
        return element;
    }

    static Element createCipherData(Document doc, byte[] cipherValue) {
        Element cipherData = createXencElement(doc, "CipherData");
        Element value = createXencElement(doc, "CipherValue");
        value.setTextContent(Base64.getEncoder().encodeToString(cipherValue));
        cipherData.appendChild(value);
        return cipherData;
    }

    /**
     * The {@code xenc:EncryptedData} that replaces an encrypted target: its algorithm, and the
     * ciphertext. It carries no {@code ds:KeyInfo} of its own - the {@code xenc:EncryptedKey}'s
     * {@code xenc:ReferenceList} points forward at it, which is the WSS4J/CXF convention.
     */
    static Element createEncryptedData(Document doc, String id, String type, String dataAlgorithm,
                                       byte[] cipherValue) {
        Element encryptedData = createXencElement(doc, "EncryptedData");
        // An unqualified Id, which is what the xenc schema declares as type ID and what WSS4J emits;
        // WsSecurityXmlUtil's idOf/markIdAttribute already accept it alongside wsu:Id.
        encryptedData.setAttribute("Id", id);
        encryptedData.setAttribute("Type", type);
        encryptedData.appendChild(encryptionMethod(doc, dataAlgorithm));
        encryptedData.appendChild(createCipherData(doc, cipherValue));
        return encryptedData;
    }

    private static Element encryptionMethod(Document doc, String algorithm) {
        Element method = createXencElement(doc, "EncryptionMethod");
        method.setAttribute("Algorithm", algorithm);
        return method;
    }

    /**
     * The {@code xenc:EncryptionMethod} of an {@code xenc:EncryptedKey}: the RSA-OAEP algorithm plus
     * the two children that pin its digests.
     * <p>
     * {@code ds:DigestMethod} precedes {@code xenc11:MGF}, matching what WSS4J emits and the
     * {@code EncryptionMethodType} content model.
     */
    static Element createKeyTransportEncryptionMethod(Document doc, String keyTransportAlgorithm) {
        Element method = encryptionMethod(doc, keyTransportAlgorithm);

        Element digestMethod = doc.createElementNS(WsSecurityXmlUtil.DS_NS, "ds:DigestMethod");
        digestMethod.setAttributeNS(XMLNS_ATTRIBUTE_NS_URI, "xmlns:ds", WsSecurityXmlUtil.DS_NS);
        digestMethod.setAttribute("Algorithm", SHA256_DIGEST);
        method.appendChild(digestMethod);

        Element mgf = doc.createElementNS(XENC11_NS, "xenc11:MGF");
        mgf.setAttributeNS(XMLNS_ATTRIBUTE_NS_URI, "xmlns:xenc11", XENC11_NS);
        mgf.setAttribute("Algorithm", MGF1_SHA256);
        method.appendChild(mgf);

        return method;
    }
}
