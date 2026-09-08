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

import com.predic8.membrane.core.interceptor.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.WSSE_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.WSU_NS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that a {@code secure} list of {@code usernameToken} and {@code signature} (with
 * {@code securityTokenReference}) reproduces the WS-Security shape a CXF/WSS4J-based service
 * commonly produces: a signed {@code wsse:UsernameToken}, with the signing certificate referenced
 * via a {@code wsse:BinarySecurityToken} + {@code wsse:SecurityTokenReference} rather than inlined,
 * plus the {@code ds:Signature}/{@code ds:KeyInfo} {@code Id} attributes, the
 * {@code ec:InclusiveNamespaces} prefix lists, and {@code mustUnderstand} that such an
 * implementation typically emits. All values here (username, password, certificate) are synthetic.
 */
class SignedUsernameTokenSampleShapeTest extends AbstractWsSecurityTest {

    private static final String X509_V3_VALUE_TYPE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3";
    private static final String USERNAME = "ACME-001";
    private static final String PASSWORD = "Example123!";

    // Mirrors the shape of the pasted sample (soapenv prefix, an Example element in the body)
    // but with entirely synthetic content.
    private static final String SAMPLE_SOAP_BODY = """
            <soapenv:Envelope xmlns:soapenv="%s">
                <soapenv:Body>
                    <Example>ACME</Example>
                </soapenv:Body>
            </soapenv:Envelope>
            """.formatted(SOAP_NS);

    Document result;

    @BeforeEach
    void signUsernameToken() throws Exception {
        UsernameTokenSecurePart usernameToken = new UsernameTokenSecurePart();
        usernameToken.setUsername(USERNAME);
        usernameToken.setPassword(PASSWORD);

        SignatureSecurePart signature = signature(xpathReference("//*[local-name()='UsernameToken']"));
        signature.setSecurityTokenReference(new SecurityTokenReferenceKeyInfo());

        WsSecurityInterceptor wsSecurity = securing(usernameToken, signature);
        wsSecurity.setKeyStore(signingKeyStore(ALIAS_1));
        wsSecurity.init(router);

        exchangeWithBody(SAMPLE_SOAP_BODY);
        assertEquals(Outcome.CONTINUE, wsSecurity.handleRequest(exchange));

        result = parseBody();
    }

    @Test
    void securityHeaderCarriesMustUnderstand() {
        assertEquals("1", firstByTag(result, WSSE_NS, "Security").getAttributeNS(SOAP_NS, "mustUnderstand"));
    }

    @Test
    void usernameTokenIsSignedByReference() {
        Element usernameToken = firstByTag(result, WSSE_NS, "UsernameToken");
        assertEquals(USERNAME, firstByTag(result, WSSE_NS, "Username").getTextContent());
        Element password = firstByTag(result, WSSE_NS, "Password");
        assertEquals(PASSWORD, password.getTextContent());
        assertEquals("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText",
                password.getAttribute("Type"));

        String usernameTokenId = usernameToken.getAttributeNS(WSU_NS, "Id");
        assertFalse(usernameTokenId.isEmpty());

        assertTrue(firstByTag(result, DS_NS, "Signature").getAttribute("Id").startsWith("SIG-"));
        assertEquals("#" + usernameTokenId, firstByTag(result, DS_NS, "Reference").getAttribute("URI"));
    }

    /**
     * SignedInfo: exclusive c14n with InclusiveNamespaces="soapenv", RSA-SHA256, SHA-256 digest, and
     * no InclusiveNamespaces on the per-reference transform.
     */
    @Test
    void signedInfoUsesTheExpectedAlgorithms() {
        Element canonicalizationMethod = firstByTag(result, DS_NS, "CanonicalizationMethod");
        assertEquals(EXC_C14N_NS, canonicalizationMethod.getAttribute("Algorithm"));
        assertEquals("soapenv", inclusiveNamespacesPrefixList(canonicalizationMethod));
        assertNoInclusiveNamespaces(firstByTag(result, DS_NS, "Transform"));

        assertEquals("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256",
                firstByTag(result, DS_NS, "SignatureMethod").getAttribute("Algorithm"));
        assertEquals("http://www.w3.org/2001/04/xmlenc#sha256",
                firstByTag(result, DS_NS, "DigestMethod").getAttribute("Algorithm"));
    }

    /**
     * ds:KeyInfo/wsse:SecurityTokenReference/wsse:Reference points at the BinarySecurityToken,
     * instead of an inline ds:X509Data.
     */
    @Test
    void certificateIsReferencedViaBinarySecurityToken() {
        assertEquals(0, result.getElementsByTagNameNS(DS_NS, "X509Data").getLength());
        assertTrue(firstByTag(result, DS_NS, "KeyInfo").getAttribute("Id").startsWith("KI-"));
        assertTrue(firstByTag(result, WSSE_NS, "SecurityTokenReference")
                .getAttributeNS(WSU_NS, "Id").startsWith("STR-"));

        Element strReference = firstByTag(result, WSSE_NS, "Reference");
        assertEquals(X509_V3_VALUE_TYPE, strReference.getAttribute("ValueType"));

        Element binarySecurityToken = firstByTag(result, WSSE_NS, "BinarySecurityToken");
        String tokenId = binarySecurityToken.getAttributeNS(WSU_NS, "Id");
        assertTrue(tokenId.startsWith("X509-"));
        assertEquals("#" + tokenId, strReference.getAttribute("URI"));
        assertEquals(X509_V3_VALUE_TYPE, binarySecurityToken.getAttribute("ValueType"));
        assertEquals("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary",
                binarySecurityToken.getAttribute("EncodingType"));
        assertFalse(binarySecurityToken.getTextContent().isBlank());
    }

    @Test
    void signatureVerifiesAgainstTheEmbeddedCertificate() throws Exception {
        assertSignatureIsValid(result);
    }
}
