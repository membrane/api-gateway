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

import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;

import static com.predic8.membrane.annot.Constants.SOAP12_NS;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.soap.wsse.SignatureReference.By.TIMESTAMP;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityFaultCode.INVALID_SECURITY;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.WSSE_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.WSU_NS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The lifecycle {@code wsSecurity} itself owns, as distinct from what its parts do: which
 * {@code wsse:Security} header is addressed, that {@code validate} consumes it before
 * {@code secure} builds a new one, and the configuration-time order constraints.
 */
class WsSecurityInterceptorTest extends AbstractWsSecurityTest {

    private static final String GATEWAY_ACTOR = "http://example.com/gateway";

    /** Two Security headers: one for the ultimate receiver, one for a gateway actor. */
    private static final String TWO_SECURITY_HEADERS = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Header>
                    <wsse:Security xmlns:wsse="%s">
                        <wsse:UsernameToken>
                            <wsse:Username>alice</wsse:Username>
                            <wsse:Password>secret</wsse:Password>
                        </wsse:UsernameToken>
                    </wsse:Security>
                    <wsse:Security xmlns:wsse="%s" soap:actor="%s">
                        <wsse:UsernameToken>
                            <wsse:Username>gatewayUser</wsse:Username>
                            <wsse:Password>gatewayPass</wsse:Password>
                        </wsse:UsernameToken>
                    </wsse:Security>
                </soap:Header>
                <soap:Body><foo>bar</foo></soap:Body>
            </soap:Envelope>
            """.formatted(WSSE_NS, WSSE_NS, GATEWAY_ACTOR);

    private static UsernameTokenValidatePart expecting(String username, String password) {
        UsernameTokenValidatePart part = new UsernameTokenValidatePart();
        part.setUsername(username);
        part.setPassword(password);
        return part;
    }

    private static int securityHeaderCount(Document doc) {
        return doc.getElementsByTagNameNS(WSSE_NS, "Security").getLength();
    }

    @Test
    void requiresAtLeastOnePart() {
        assertThrows(ConfigurationException.class, () -> new WsSecurityInterceptor().init(router));
    }

    @Test
    void validateConsumesTheSecurityHeader() throws Exception {
        exchangeWithBody(TWO_SECURITY_HEADERS);
        WsSecurityInterceptor wsSecurity = validating(expecting("alice", "secret"));
        wsSecurity.init(router);

        assertEquals(CONTINUE, wsSecurity.handleRequest(exchange));

        Document result = parseBody();
        // The default-actor header was understood, so it is gone; the gateway actor's is untouched.
        assertEquals(1, securityHeaderCount(result));
        assertEquals(GATEWAY_ACTOR,
                firstByTag(result, WSSE_NS, "Security").getAttributeNS(SOAP_NS, "actor"));
        assertEquals("gatewayUser", firstByTag(result, WSSE_NS, "Username").getTextContent());
    }

    @Test
    void actorSelectsWhichHeaderIsValidated() throws Exception {
        exchangeWithBody(TWO_SECURITY_HEADERS);
        WsSecurityInterceptor wsSecurity = validating(expecting("gatewayUser", "gatewayPass"));
        wsSecurity.setActor(GATEWAY_ACTOR);
        wsSecurity.init(router);

        assertEquals(CONTINUE, wsSecurity.handleRequest(exchange));

        Document result = parseBody();
        assertEquals(1, securityHeaderCount(result));
        assertTrue(firstByTag(result, WSSE_NS, "Security").getAttributeNS(SOAP_NS, "actor").isEmpty());
    }

    @Test
    void missingSecurityHeaderForTheConfiguredActorFaults() throws Exception {
        exchangeWithBody(TWO_SECURITY_HEADERS);
        WsSecurityInterceptor wsSecurity = validating(expecting("alice", "secret"));
        wsSecurity.setActor("http://example.com/other");
        wsSecurity.init(router);

        assertFault(wsSecurity, INVALID_SECURITY);
    }

    @Test
    void messageWithoutAnySecurityHeaderFaults() throws Exception {
        exchangeWithBody(SOAP_BODY);
        WsSecurityInterceptor wsSecurity = validating(expecting("alice", "secret"));
        wsSecurity.init(router);

        assertFault(wsSecurity, INVALID_SECURITY);
    }

    /**
     * The gateway case: validate what the client sent, then re-secure for the backend. The inbound
     * header must be replaced, not extended, so the backend never sees the client's credential.
     */
    @Test
    void validateThenSecureReplacesTheHeaderRatherThanExtendingIt() throws Exception {
        exchangeWithBody(TWO_SECURITY_HEADERS);
        UsernameTokenSecurePart outbound = new UsernameTokenSecurePart();
        outbound.setUsername("backendUser");
        outbound.setPassword("backendPass");
        WsSecurityInterceptor wsSecurity = wsSecurity(
                List.of(expecting("alice", "secret")), List.of(new TimestampSecurePart(), outbound));
        wsSecurity.init(router);

        assertEquals(CONTINUE, wsSecurity.handleRequest(exchange));

        Document result = parseBody();
        // The consumed default-actor header was removed; the untouched gateway one and the fresh
        // one remain.
        assertEquals(2, securityHeaderCount(result));
        Element fresh = (Element) result.getElementsByTagNameNS(WSSE_NS, "Security").item(1);
        assertTrue(fresh.getAttributeNS(SOAP_NS, "actor").isEmpty());
        assertEquals("Timestamp", fresh.getFirstChild().getLocalName());
        assertEquals(2, result.getElementsByTagNameNS(WSSE_NS, "Username").getLength());
        assertEquals("backendUser", ((Element) result.getElementsByTagNameNS(WSSE_NS, "Username").item(1))
                .getTextContent());
    }

    /**
     * The SOAP sniff stops reading at the first body element, so it accepts a message whose tail is
     * malformed - which strict DOM parsing then rejects. That parse failure has to end the exchange
     * like any other rejection rather than escaping the element as an unhandled exception. It
     * answers with Problem Details, not a soap:Fault: an unparseable body is precisely what does
     * not tell us which envelope version a fault would have to use.
     */
    @Test
    void bodyThatPassesTheSoapSniffButNotStrictParsingIsRejected() throws Exception {
        exchangeWithBody("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Body><foo>bar</soap:Body>
                </soap:Envelope>
                """);
        WsSecurityInterceptor wsSecurity = securing(new TimestampSecurePart());
        wsSecurity.init(router);

        assertAborts(wsSecurity, 400);
    }

    @Test
    void secureTargetsTheConfiguredActor() throws Exception {
        exchangeWithBody(SOAP_BODY);
        WsSecurityInterceptor wsSecurity = securing(new TimestampSecurePart());
        wsSecurity.setActor(GATEWAY_ACTOR);
        wsSecurity.init(router);

        assertEquals(CONTINUE, wsSecurity.handleRequest(exchange));

        Element security = firstByTag(parseBody(), WSSE_NS, "Security");
        assertEquals(GATEWAY_ACTOR, security.getAttributeNS(SOAP_NS, "actor"));
        assertEquals("1", security.getAttributeNS(SOAP_NS, "mustUnderstand"));
    }

    @Test
    void mustUnderstandCanBeTurnedOff() throws Exception {
        exchangeWithBody(SOAP_BODY);
        WsSecurityInterceptor wsSecurity = securing(new TimestampSecurePart());
        wsSecurity.setMustUnderstand(false);
        wsSecurity.init(router);

        assertEquals(CONTINUE, wsSecurity.handleRequest(exchange));

        assertTrue(firstByTag(parseBody(), WSSE_NS, "Security").getAttributeNS(SOAP_NS, "mustUnderstand").isEmpty());
    }

    @Test
    void soap12UsesRoleAndBooleanMustUnderstand() throws Exception {
        exchangeWithBody("""
                <env:Envelope xmlns:env="%s">
                    <env:Body><foo>bar</foo></env:Body>
                </env:Envelope>
                """.formatted(SOAP12_NS));
        WsSecurityInterceptor wsSecurity = securing(new TimestampSecurePart());
        wsSecurity.setActor(GATEWAY_ACTOR);
        wsSecurity.init(router);

        assertEquals(CONTINUE, wsSecurity.handleRequest(exchange));

        Element security = firstByTag(parseBody(), WSSE_NS, "Security");
        assertEquals(GATEWAY_ACTOR, security.getAttributeNS(SOAP12_NS, "role"));
        assertEquals("true", security.getAttributeNS(SOAP12_NS, "mustUnderstand"));
        assertTrue(security.getAttributeNS(SOAP12_NS, "actor").isEmpty());
    }

    @Test
    void secureReusesAnExistingHeaderForTheSameActorRatherThanAddingASecond() throws Exception {
        exchangeWithBody(TWO_SECURITY_HEADERS);
        WsSecurityInterceptor wsSecurity = securing(new TimestampSecurePart());
        wsSecurity.init(router);

        assertEquals(CONTINUE, wsSecurity.handleRequest(exchange));

        // WS-Security allows at most one wsse:Security per actor, so the existing one is added to.
        Document result = parseBody();
        assertEquals(2, securityHeaderCount(result));
        assertEquals(1, result.getElementsByTagNameNS(WSU_NS, "Timestamp").getLength());
    }

    @Test
    void signatureCoveringTheTimestampMustBeListedAfterIt() {
        WsSecurityInterceptor wsSecurity = securing(
                signature(bodyReference(), reference(TIMESTAMP)), new TimestampSecurePart());
        wsSecurity.setKeyStore(signingKeyStore(ALIAS_1));

        ConfigurationException e = assertThrows(ConfigurationException.class, () -> wsSecurity.init(router));
        assertTrue(e.getMessage().contains("TIMESTAMP"));
    }

    @Test
    void secureWithoutValidateDiscardsTheTokensThePeerSent() throws Exception {
        // Nothing checked the client's UsernameToken, so forwarding it would present it to the backend
        // alongside the security this element adds - as if the gateway had vouched for it.
        exchangeWithBody(TWO_SECURITY_HEADERS);
        WsSecurityInterceptor wsSecurity = securing(new TimestampSecurePart());
        wsSecurity.init(router);

        assertEquals(CONTINUE, wsSecurity.handleRequest(exchange));

        Document result = parseBody();
        // Only the token in the header addressed to the ultimate receiver goes: the one targeted at
        // GATEWAY_ACTOR belongs to another node and is none of this element's business.
        assertEquals(1, result.getElementsByTagNameNS(WSSE_NS, "UsernameToken").getLength());
        assertEquals("gatewayUser",
                firstByTag(result, WSSE_NS, "Username").getTextContent());
    }

    @Test
    void secureWithoutValidateKeepsATimestampASignatureMayCover() throws Exception {
        // The one thing an unvalidated header may keep: it asserts nothing on its own, and by: TIMESTAMP
        // exists precisely to cover a freshness window the message already carried.
        exchangeWithBody("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Header>
                        <wsse:Security xmlns:wsse="%s">
                            <wsu:Timestamp xmlns:wsu="%s"><wsu:Created>%s</wsu:Created></wsu:Timestamp>
                            <wsse:UsernameToken><wsse:Username>alice</wsse:Username></wsse:UsernameToken>
                        </wsse:Security>
                    </soap:Header>
                    <soap:Body><foo>bar</foo></soap:Body>
                </soap:Envelope>
                """.formatted(WSSE_NS, WSU_NS, java.time.Instant.now()));
        WsSecurityInterceptor wsSecurity = securing(signature(bodyReference(), reference(TIMESTAMP)));
        wsSecurity.setKeyStore(signingKeyStore(ALIAS_1));
        wsSecurity.init(router);

        assertEquals(CONTINUE, wsSecurity.handleRequest(exchange));

        Document result = parseBody();
        assertEquals(1, result.getElementsByTagNameNS(WSU_NS, "Timestamp").getLength());
        assertEquals(0, result.getElementsByTagNameNS(WSSE_NS, "UsernameToken").getLength());
        assertEquals(2, result.getElementsByTagNameNS(DS_NS, "Reference").getLength());
    }

    @Test
    void soapPrefixInAnXPathReferenceResolvesForSoap12() throws Exception {
        // Bound to the envelope namespace of the message, not to SOAP 1.1: bound to a fixed one this
        // would match nothing here and fault, blaming the XPath rather than the envelope version.
        exchangeWithBody("""
                <env:Envelope xmlns:env="%s">
                    <env:Body><foo>bar</foo></env:Body>
                </env:Envelope>
                """.formatted(SOAP12_NS));
        WsSecurityInterceptor wsSecurity = securing(signature(xpathReference("//soap:Body")));
        wsSecurity.setKeyStore(signingKeyStore(ALIAS_1));
        wsSecurity.init(router);

        assertEquals(CONTINUE, wsSecurity.handleRequest(exchange));
        assertEquals(1, parseBody().getElementsByTagNameNS(DS_NS, "Reference").getLength());
    }

    @Test
    void signatureCoveringTheUsernameTokenBeforeItIsAConfigurationError() {
        // Same rule as for the timestamp: the part that creates what a signature covers has to be listed
        // first, or the message goes out silently under-covered.
        WsSecurityInterceptor wsSecurity = securing(
                signature(bodyReference(), reference(SignatureReference.By.USERNAME_TOKEN)), usernameTokenSecuring());
        wsSecurity.setKeyStore(signingKeyStore(ALIAS_1));

        ConfigurationException e = assertThrows(ConfigurationException.class, () -> wsSecurity.init(router));
        assertTrue(e.getMessage().contains("USERNAME_TOKEN"));
    }

    @Test
    void signatureCoveringTheUsernameTokenIsAcceptedAfterIt() {
        WsSecurityInterceptor wsSecurity = securing(
                usernameTokenSecuring(), signature(bodyReference(), reference(SignatureReference.By.USERNAME_TOKEN)));
        wsSecurity.setKeyStore(signingKeyStore(ALIAS_1));

        assertDoesNotThrow(() -> wsSecurity.init(router));
    }

    private static UsernameTokenSecurePart usernameTokenSecuring() {
        UsernameTokenSecurePart usernameToken = new UsernameTokenSecurePart();
        usernameToken.setUsername("alice");
        usernameToken.setPassword("secret");
        return usernameToken;
    }

    @Test
    void signatureCoveringTheTimestampIsAcceptedAfterIt() {
        WsSecurityInterceptor wsSecurity = securing(
                new TimestampSecurePart(), signature(bodyReference(), reference(TIMESTAMP)));
        wsSecurity.setKeyStore(signingKeyStore(ALIAS_1));

        assertDoesNotThrow(() -> wsSecurity.init(router));
    }
}
