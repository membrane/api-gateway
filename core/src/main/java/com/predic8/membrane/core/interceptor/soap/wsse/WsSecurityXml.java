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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import static javax.xml.XMLConstants.NULL_NS_URI;

/**
 * SOAP/WS-Security XML helpers shared by {@link UsernameTokenInterceptor},
 * {@link DigitalSignatureInterceptor}, and {@link DigitalSignatureVerifierInterceptor}: locating
 * or creating the {@code soap:Header}/{@code wsse:Security} structure, and resolving a
 * {@link SignatureReference} to the element(s) it selects.
 */
final class WsSecurityXml {

    public static final String WSSE_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    public static final String WSU_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";

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

    static final NamespaceContext SOAP_WSSE_NAMESPACE_CONTEXT = new NamespaceContext() {
        @Override
        public String getNamespaceURI(String prefix) {
            return switch (prefix) {
                case "soap" -> "http://schemas.xmlsoap.org/soap/envelope/";
                case "wsse" -> WSSE_NS;
                case "wsu" -> WSU_NS;
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

    private WsSecurityXml() {
    }

    /**
     * The {@code soap}/{@code wsse}/{@code wsu} prefixes always resolve, even when {@code xmlConfig}
     * declares its own namespaces - the interceptor's built-in structure still needs to be
     * addressable from an XPath reference.
     */
    private static NamespaceContext mergedNamespaceContext(XmlConfig xmlConfig) {
        if (xmlConfig == null || xmlConfig.getNamespaces() == null) {
            return SOAP_WSSE_NAMESPACE_CONTEXT;
        }
        NamespaceContext configured = xmlConfig.getNamespaces().getNamespaceContext();
        return new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                String uri = configured.getNamespaceURI(prefix);
                return NULL_NS_URI.equals(uri) ? SOAP_WSSE_NAMESPACE_CONTEXT.getNamespaceURI(prefix) : uri;
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

    static Element getOrCreateSecurity(Document doc, Element header) {
        Element security = getFirstChildByName(header, WSSE_NS, "Security");
        if (security != null) {
            return security;
        }
        security = doc.createElementNS(WSSE_NS, "wsse:Security");
        header.insertBefore(security, header.getFirstChild());
        return security;
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
            case BST -> List.of(requireElement(getFirstChildByName(security, WSSE_NS, "BinarySecurityToken"),
                    "No wsse:BinarySecurityToken found inside wsse:Security."));
            case XPATH -> resolveByXPath(doc, reference.getXpath(), xmlConfig);
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

    private static List<Element> resolveByXPath(Document doc, String xpath, XmlConfig xmlConfig) {
        if (xpath == null || xpath.isBlank()) {
            throw new ReferenceResolutionException("reference by=\"XPATH\" requires an xpath attribute.");
        }
        try {
            XPath xPath = XPATH_FACTORY.newXPath();
            xPath.setNamespaceContext(mergedNamespaceContext(xmlConfig));
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
     */
    static void forEachDescendantElement(Element element, Consumer<Element> action) {
        action.accept(element);
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) {
                forEachDescendantElement(child, action);
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
     * nothing. Callers decide the resulting HTTP status: a signer treats this as a bad request
     * (400), a verifier treats it as a failed verification (403).
     */
    static class ReferenceResolutionException extends RuntimeException {
        ReferenceResolutionException(String message) {
            super(message);
        }
    }
}
