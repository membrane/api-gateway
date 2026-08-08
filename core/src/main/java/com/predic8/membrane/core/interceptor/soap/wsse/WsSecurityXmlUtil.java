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

import com.predic8.membrane.core.config.xml.XmlConfig;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.function.Consumer;

import static com.predic8.membrane.annot.Constants.SOAP11_NS;
import static com.predic8.membrane.annot.Constants.SOAP12_NS;
import static javax.xml.XMLConstants.NULL_NS_URI;

/**
 * SOAP/WS-Security XML helpers shared by {@link WsSecurityInterceptor} and its parts: locating,
 * creating and addressing the {@code soap:Header}/{@code wsse:Security} structure, and resolving a
 * {@link SignatureReference} to the element(s) it selects.
 */
final class WsSecurityXmlUtil {

    public static final String WSSE_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    public static final String WSU_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
    /** WS-Security 1.1's own namespace, which is where {@code TokenType} lives. */
    static final String WSSE11_NS = "http://docs.oasis-open.org/wss/oasis-wss-wssecurity-secext-1.1.xsd";
    static final String DS_NS = "http://www.w3.org/2000/09/xmldsig#";

    static final String X509_V3_VALUE_TYPE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3";
    static final String THUMBPRINT_SHA1_VALUE_TYPE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.1#ThumbprintSHA1";
    // Note: this is a distinct namespace from WSSE_NS (the wsse secext schema itself) - not to be
    // confused with "...wssecurity-secext-1.0.xsd#Base64Binary", which is not a valid EncodingType.
    static final String BASE64_BINARY_ENCODING_TYPE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary";

    static final String USERNAME_TOKEN_PROFILE_NS =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0";
    static final String PASSWORD_TEXT_TYPE = USERNAME_TOKEN_PROFILE_NS + "#PasswordText";
    static final String PASSWORD_DIGEST_TYPE = USERNAME_TOKEN_PROFILE_NS + "#PasswordDigest";

    /**
     * The prefixes an XPath reference can always use, whatever {@code xmlConfig} declares.
     * <p>
     * {@code soap} is bound to the envelope namespace of the message being processed, not to a fixed
     * one: bound to SOAP 1.1 unconditionally it would silently match nothing in a SOAP 1.2 message,
     * turning a correct configuration into a fault whose reason points at the XPath instead of at the
     * envelope version. {@code soap11}/{@code soap12} stay available for a configuration that has to
     * address one specific version.
     */
    static NamespaceContext builtInNamespaceContext(String soapNs) {
        return new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return switch (prefix) {
                    case "soap" -> soapNs;
                    case "soap11" -> SOAP11_NS;
                    case "soap12" -> SOAP12_NS;
                    case "wsse" -> WSSE_NS;
                    case "wsse11" -> WSSE11_NS;
                    case "wsu" -> WSU_NS;
                    case "ds" -> DS_NS;
                    default -> null;
                };
            }

            @Override
            public String getPrefix(String namespaceURI) {
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String namespaceURI) {
                return null;
            }
        };
    }

    private WsSecurityXmlUtil() {
    }

    /**
     * The built-in prefixes always resolve, even when {@code xmlConfig} declares its own namespaces -
     * the interceptor's built-in structure still needs to be addressable from an XPath reference.
     */
    private static NamespaceContext mergedNamespaceContext(XmlConfig xmlConfig, String soapNs) {
        NamespaceContext builtIn = builtInNamespaceContext(soapNs);
        if (xmlConfig == null || xmlConfig.getNamespaces() == null) {
            return builtIn;
        }
        NamespaceContext configured = xmlConfig.getNamespaces().getNamespaceContext();
        return new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                String uri = configured.getNamespaceURI(prefix);
                return NULL_NS_URI.equals(uri) ? builtIn.getNamespaceURI(prefix) : uri;
            }

            @Override
            public String getPrefix(String namespaceURI) {
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String namespaceURI) {
                return null;
            }
        };
    }

    static Element getOrCreateHeader(Document doc, Element envelope, String soapNs) {
        Element header = getFirstChildByName(envelope, soapNs, "Header");
        if (header != null) {
            return header;
        }
        header = doc.createElementNS(soapNs, "soap:Header");
        Element body = getFirstChildByName(envelope, soapNs, "Body");
        envelope.insertBefore(header, body);
        return header;
    }

    /**
     * The {@code wsse:Security} header targeted at {@code actor}, or null if the message carries
     * none. A null {@code actor} selects the header with no {@code actor}/{@code role} attribute,
     * i.e. the one addressed to the ultimate receiver; headers targeted at any other actor are
     * invisible here and therefore pass through untouched.
     */
    static Element findSecurity(Element envelope, String soapNs, String actor) {
        Element header = getFirstChildByName(envelope, soapNs, "Header");
        if (header == null) {
            return null;
        }
        for (Element security : getChildrenByName(header, WSSE_NS, "Security")) {
            if (Objects.equals(actor, actorOf(security, soapNs))) {
                return security;
            }
        }
        return null;
    }

    /**
     * @return the header's {@code actor} (SOAP 1.1) or {@code role} (SOAP 1.2) attribute, or null
     * when it carries neither
     */
    private static String actorOf(Element security, String soapNs) {
        String actor = security.getAttributeNS(soapNs, actorAttributeName(soapNs));
        return actor.isEmpty() ? null : actor;
    }

    /**
     * SOAP 1.2 renamed {@code actor} to {@code role}; both mean the node a header block is targeted
     * at.
     */
    private static String actorAttributeName(String soapNs) {
        return SOAP12_NS.equals(soapNs) ? "role" : "actor";
    }

    /**
     * The {@code wsse:Security} header for {@code actor} that {@code secure} parts add to, created
     * if the message does not already carry one. In practice it is always created: the enclosing
     * element removes the header targeted at its actor before any {@code secure} part runs, whether or
     * not it had a {@code validate} list. The reuse branch is what keeps that a single
     * {@code wsse:Security} block per actor even so, which is all WS-Security allows.
     * <p>
     * Appended rather than inserted first so header blocks the message already carried - including
     * ones targeted at other actors - keep their relative order.
     */
    static Element getOrCreateSecurity(Document doc, Element envelope, String soapNs, String actor, boolean mustUnderstand) {
        Element security = findSecurity(envelope, soapNs, actor);
        if (security == null) {
            security = doc.createElementNS(WSSE_NS, "wsse:Security");
            getOrCreateHeader(doc, envelope, soapNs).appendChild(security);
            if (actor != null) {
                security.setAttributeNS(soapNs,
                        soapPrefixOrDeclare(security, soapNs) + ":" + actorAttributeName(soapNs), actor);
            }
        }
        if (mustUnderstand && security.getAttributeNS(soapNs, "mustUnderstand").isEmpty()) {
            // SOAP 1.1 spells the boolean "1"/"0", SOAP 1.2 "true"/"false".
            security.setAttributeNS(soapNs, soapPrefixOrDeclare(security, soapNs) + ":mustUnderstand",
                    SOAP12_NS.equals(soapNs) ? "true" : "1");
        }
        return security;
    }

    private static String soapPrefixOrDeclare(Element element, String soapNs) {
        String soapPrefix = element.lookupPrefix(soapNs);
        if (soapPrefix != null) {
            return soapPrefix;
        }
        element.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:soapenv", soapNs);
        return "soapenv";
    }

    /**
     * Sets {@code wsu:Id} on {@code element}, declaring the {@code wsu} prefix if it is not already
     * in scope. The explicit declaration matters for signing: left to the serializer's namespace
     * fixup it would only appear <i>after</i> the digests were computed, so canonicalization at
     * signing time has to see it here.
     */
    static void declareWsuId(Element element, String id) {
        element.setAttributeNS(WSU_NS, "wsu:Id", id);
        if (element.lookupNamespaceURI("wsu") == null) {
            element.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:wsu", WSU_NS);
        }
    }

    /**
     * Declares every {@code wsu:Id} in the tree to be an XML ID attribute. A freshly (DTD-less)
     * parsed document knows of no IDs at all, so without this same-document {@code "#id"}
     * dereferencing - JSR-105 signature validation and {@code getElementById}-style lookups alike -
     * resolves nothing.
     * <p>
     * An unqualified {@code Id} counts too, matching what {@link #idOf(Element)} accepts: other
     * WS-Security stacks put one on {@code ds:Signature}, {@code ds:Object} and token elements, and
     * leaving it unregistered would fail an inbound signature whose {@code ds:SignedInfo} is sound.
     * {@code wsu:Id} wins where an element carries both.
     */
    static void markWsuIdAttributes(Element root) {
        forEachDescendantElement(root, WsSecurityXmlUtil::markIdAttribute);
    }

    /**
     * Declares the element's {@code wsu:Id} - or its unqualified {@code Id} - to be an XML ID
     * attribute, so that {@code "#id"} dereferences to it. Does nothing when it carries neither.
     *
     * @see #markWsuIdAttributes(Element)
     */
    static void markIdAttribute(Element element) {
        if (!element.getAttributeNS(WSU_NS, "Id").isEmpty()) {
            element.setIdAttributeNS(WSU_NS, "Id", true);
        } else if (!element.getAttribute("Id").isEmpty()) {
            element.setIdAttribute("Id", true);
        }
    }

    /**
     * @return the element's {@code wsu:Id}, falling back to an unqualified {@code Id}, or the empty
     * string when it has neither
     */
    static String idOf(Element element) {
        String wsuId = element.getAttributeNS(WSU_NS, "Id");
        return !wsuId.isEmpty() ? wsuId : element.getAttribute("Id");
    }

    /**
     * Resolves a {@link SignatureReference} to the element(s) it selects (the SOAP body/header, an
     * existing {@code wsu:Timestamp}, or the XPath's matches), for both signing and verification.
     * {@code BODY}/{@code HEADER}/{@code TIMESTAMP} always resolve to exactly one element;
     * {@code XPATH} resolves to every matched element, one or more.
     *
     * @throws ReferenceResolutionException if the reference cannot be resolved to at least one element
     */
    static List<Element> resolveReference(Document doc, Element envelope, Element security, String soapNs, SignatureReference reference, XmlConfig xmlConfig) {
        return switch (reference.getBy()) {
            case BODY -> List.of(requireElement(getFirstChildByName(envelope, soapNs, "Body"), "soap:Body is missing."));
            case HEADER -> List.of(requireElement(getFirstChildByName(envelope, soapNs, "Header"), "soap:Header is missing."));
            case TIMESTAMP -> List.of(requireElement(getFirstChildByName(security, WSU_NS, "Timestamp"),
                    "No wsu:Timestamp found inside wsse:Security."));
            case USERNAME_TOKEN -> List.of(requireElement(getFirstChildByName(security, WSSE_NS, "UsernameToken"),
                    "No wsse:UsernameToken found inside wsse:Security."));
            case BST -> List.of(requireElement(getFirstChildByName(security, WSSE_NS, "BinarySecurityToken"),
                    "No wsse:BinarySecurityToken found inside wsse:Security."));
            case XPATH -> resolveByXPath(doc, reference.getXpath(), xmlConfig, soapNs);
        };
    }

    private static Element requireElement(Element element, String message) {
        if (element == null) {
            throw new ReferenceResolutionException(message);
        }
        return element;
    }

    private static final XPathFactory XPATH_FACTORY = createXPathFactory();

    private static XPathFactory createXPathFactory() {
        XPathFactory factory = XPathFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (XPathFactoryConfigurationException e) {
            throw new IllegalStateException("Could not enable secure processing on XPathFactory.", e);
        }
        return factory;
    }

    private static List<Element> resolveByXPath(Document doc, String xpath, XmlConfig xmlConfig, String soapNs) {
        if (xpath == null || xpath.isBlank()) {
            throw new ReferenceResolutionException("reference by=\"XPATH\" requires an xpath attribute.");
        }
        try {
            XPath xPath = XPATH_FACTORY.newXPath();
            xPath.setNamespaceContext(mergedNamespaceContext(xmlConfig, soapNs));
            NodeList nodes = (NodeList) xPath.evaluate(xpath, doc, XPathConstants.NODESET);
            if (nodes.getLength() == 0) {
                throw new ReferenceResolutionException("XPath \"" + xpath + "\" matched no elements.");
            }
            List<Element> elements = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (!(node instanceof Element element)) {
                    throw new ReferenceResolutionException(
                            "XPath \"" + xpath + "\" matched a " + node.getNodeName() + " node, expected an element.");
                }
                elements.add(element);
            }
            return elements;
        } catch (XPathExpressionException e) {
            throw new ReferenceResolutionException("Invalid XPath expression: " + xpath);
        }
    }

    static Element getFirstChildByName(Element parent, String namespace, String localName) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el
                    && localName.equals(el.getLocalName())
                    && namespace.equals(el.getNamespaceURI())) {
                return el;
            }
        }
        return null;
    }

    /**
     * The element children of {@code parent}, as a snapshot - so a caller may remove them while
     * iterating, which a live {@code NodeList} would not survive.
     */
    static List<Element> childElementsOf(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el) {
                result.add(el);
            }
        }
        return result;
    }

    static List<Element> getChildrenByName(Element parent, String namespace, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el
                    && localName.equals(el.getLocalName())
                    && namespace.equals(el.getNamespaceURI())) {
                result.add(el);
            }
        }
        return result;
    }

    /**
     * Applies {@code action} to {@code element} and, depth-first, to every descendant element.
     * <p>
     * Iterative rather than recursive because the document is attacker-supplied and nothing caps its
     * nesting depth - neither the hardened parser nor secure processing sets
     * {@code jdk.xml.maxElementDepth}, and per ADR-007 Membrane deliberately applies no depth limit
     * of its own. A recursive walk over a deeply nested body would raise {@link StackOverflowError},
     * which is an {@link Error} and so escapes the {@code catch (Exception)} that is supposed to turn
     * a bad message into a fault response.
     */
    static void forEachDescendantElement(Element element, Consumer<Element> action) {
        Deque<Element> pending = new ArrayDeque<>();
        pending.push(element);
        while (!pending.isEmpty()) {
            Element current = pending.pop();
            action.accept(current);
            NodeList children = current.getChildNodes();
            // Pushed back to front so siblings come off the stack in document order, keeping the
            // pre-order traversal the recursive version had.
            for (int i = children.getLength() - 1; i >= 0; i--) {
                if (children.item(i) instanceof Element child) {
                    pending.push(child);
                }
            }
        }
    }

    /**
     * The WS-Security UsernameToken password digest: Base64(SHA-1(nonce + created + password)).
     */
    static String usernameTokenDigest(byte[] nonce, String created, String password) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update(nonce);
        sha1.update(created.getBytes(StandardCharsets.UTF_8));
        sha1.update(password.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sha1.digest());
    }

    static byte[] sha1Thumbprint(X509Certificate certificate) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-1").digest(certificate.getEncoded());
    }

    /**
     * Thrown when a {@link SignatureReference} cannot be resolved to at least one element - either
     * because the referenced element (body/header/timestamp) is absent, or its XPath matched
     * nothing. Callers decide which fault this becomes: a {@code secure} signature reports
     * {@code wsse:InvalidSecurity}, a {@code validate} one {@code wsse:FailedCheck}.
     */
    static class ReferenceResolutionException extends RuntimeException {
        ReferenceResolutionException(String message) {
            super(message);
        }
    }
}
