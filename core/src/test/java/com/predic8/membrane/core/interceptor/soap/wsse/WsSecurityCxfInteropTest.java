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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider;
import com.predic8.membrane.core.interceptor.flow.RequestInterceptor;
import com.predic8.membrane.core.interceptor.flow.ResponseInterceptor;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.TestRouter;
import jakarta.jws.WebService;
import jakarta.xml.ws.WebServiceException;
import jakarta.xml.ws.soap.SOAPBinding;
import org.apache.cxf.Bus;
import org.apache.cxf.bus.extension.ExtensionManagerBus;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.cxf.ws.security.wss4j.CryptoCoverageChecker;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.wss4j.common.crypto.Merlin;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.action.Action;
import org.apache.wss4j.dom.action.TimestampAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.security.auth.callback.CallbackHandler;
import java.net.ServerSocket;
import java.security.KeyStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.predic8.membrane.core.interceptor.soap.wsse.SignatureReference.By.TIMESTAMP;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.WSSE_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.WSU_NS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.cxf.ws.security.wss4j.CryptoCoverageUtil.CoverageScope.CONTENT;
import static org.apache.cxf.ws.security.wss4j.CryptoCoverageUtil.CoverageScope.ELEMENT;
import static org.apache.cxf.ws.security.wss4j.CryptoCoverageUtil.CoverageType.ENCRYPTED;
import static org.apache.cxf.ws.security.wss4j.CryptoCoverageUtil.CoverageType.SIGNED;
import static org.apache.wss4j.common.ConfigurationConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests WS-Security interoperability between Membrane and Apache CXF/WSS4J over real loopback HTTP,
 * using an embedded CXF echo service and a Membrane proxy; no external service is required.
 * Each secured message is produced and consumed by different stacks.
 *
 * <p>The round-trip matrix covers SOAP 1.1 and SOAP 1.2 in both directions:
 * Membrane secures requests for CXF and validates CXF responses, or CXF secures requests for
 * Membrane and validates Membrane responses. Four profiles give 16 round-trip cases:</p>
 * <ul>
 *     <li>Signature covering the SOAP body and timestamp.</li>
 *     <li>UsernameToken with a text password.</li>
 *     <li>SOAP body encryption.</li>
 *     <li>Signing followed by encryption.</li>
 * </ul>
 * <p>Successful calls must preserve the payload, invoke the service exactly once, and carry the
 * expected security elements on both request and response; encrypted bodies must hide the payload.</p>
 *
 * <p>Another 28 cases cover seven rejected requests in both directions and SOAP versions:
 * a tampered signed body, an untrusted signing certificate, an expired signed timestamp,
 * a wrong UsernameToken password, an unsigned body, a missing security header, and missing
 * required encryption. Password and encryption failures use their respective profiles; the
 * remaining failures use the signature profile. Each case checks that the service is never
 * invoked and that the receiving stack returns a SOAP fault with the expected rejection reason.</p>
 */
@Timeout(30)
class WsSecurityCxfInteropTest extends AbstractWsSecurityTest {
    private static final String SERVICE_NS = "urn:membrane:wsse:interop";
    private static final String SOAP12_NS = "http://www.w3.org/2003/05/soap-envelope";
    private static final String PAYLOAD = "Hello CXF — Grüße & <SOAP>";

    enum Direction { MEMBRANE_TO_CXF, CXF_TO_MEMBRANE }

    enum Profile {
        SIGNATURE, USERNAME_TOKEN, ENCRYPTION, SIGN_THEN_ENCRYPT;

        boolean signed() { return this == SIGNATURE || this == SIGN_THEN_ENCRYPT; }
        boolean encrypted() { return this == ENCRYPTION || this == SIGN_THEN_ENCRYPT; }
    }

    enum Attack {
        NONE, TAMPERED_BODY, UNTRUSTED_CERTIFICATE, EXPIRED_TIMESTAMP, WRONG_PASSWORD,
        UNSIGNED_BODY, MISSING_SECURITY, MISSING_ENCRYPTION
    }

    record Scenario(Direction direction, boolean soap12, Profile profile) {
        String soapNamespace() { return soap12 ? SOAP12_NS : SOAP_NS; }
        String binding() { return soap12 ? SOAPBinding.SOAP12HTTP_BINDING : SOAPBinding.SOAP11HTTP_BINDING; }
    }

    private final AtomicInteger invocations = new AtomicInteger();
    private final AtomicReference<String> receiverFault = new AtomicReference<>();
    private final AtomicReference<String> securedRequest = new AtomicReference<>();
    private final AtomicReference<String> securedResponse = new AtomicReference<>();
    private Bus bus;
    private Server server;
    private Client client;
    private TestRouter proxyRouter;

    static Stream<Scenario> scenarios() {
        return Stream.of(Profile.values()).flatMap(WsSecurityCxfInteropTest::scenarios);
    }

    private static Stream<Scenario> scenarios(Profile profile) {
        return Stream.of(Direction.values()).flatMap(direction ->
                Stream.of(false, true).map(soap12 -> new Scenario(direction, soap12, profile)));
    }

    static Stream<Arguments> rejections() {
        return Stream.of(Attack.values()).filter(attack -> attack != Attack.NONE).flatMap(attack -> {
            Profile profile = switch (attack) {
                case WRONG_PASSWORD -> Profile.USERNAME_TOKEN;
                case MISSING_ENCRYPTION -> Profile.ENCRYPTION;
                default -> Profile.SIGNATURE;
            };
            return scenarios(profile).map(scenario -> Arguments.of(scenario, attack));
        });
    }

    @ParameterizedTest(name = "round trip: {0}")
    @MethodSource("scenarios")
    void securedRoundTrip(Scenario scenario) throws Exception {
        Echo service = start(scenario, Attack.NONE);
        assertEquals(PAYLOAD, service.echo(PAYLOAD));
        assertEquals(1, invocations.get());
        assertWireProtection(scenario, securedRequest.get());
        assertWireProtection(scenario, securedResponse.get());
    }

    @ParameterizedTest(name = "reject {1}: {0}")
    @MethodSource("rejections")
    void rejectsBeforeServiceInvocation(Scenario scenario, Attack attack) throws Exception {
        Echo service = start(scenario, attack);
        assertThrows(WebServiceException.class, () -> service.echo(PAYLOAD));
        assertEquals(0, invocations.get(), "Rejected requests must never reach the service");
        assertNotNull(receiverFault.get(), "The receiving stack must return a SOAP fault");
        exchangeWithBody(receiverFault.get());
        assertEquals(1, parseBody().getElementsByTagNameNS(scenario.soapNamespace(), "Fault").getLength(),
                receiverFault.get());
        assertRejectionReason(scenario, attack);
    }

    private void assertRejectionReason(Scenario scenario, Attack attack) throws Exception {
        String body = receiverFault.get();
        if (scenario.direction == Direction.MEMBRANE_TO_CXF && attack == Attack.UNTRUSTED_CERTIFICATE) {
            assertTrue(body.contains("No trusted certs found"), body);
            return;
        }
        if (scenario.direction == Direction.MEMBRANE_TO_CXF && attack == Attack.UNSIGNED_BODY) {
            assertTrue(body.contains("No SIGNED element found matching XPath /s:Envelope/s:Body"), body);
            return;
        }
        String expectedCode = switch (attack) {
            case TAMPERED_BODY, UNTRUSTED_CERTIFICATE, UNSIGNED_BODY -> "FailedCheck";
            case EXPIRED_TIMESTAMP -> "MessageExpired";
            case WRONG_PASSWORD -> "FailedAuthentication";
            case MISSING_SECURITY, MISSING_ENCRYPTION -> "InvalidSecurity";
            default -> throw new IllegalArgumentException("Not a rejection: " + attack);
        };
        var document = parseBody();
        var code = scenario.soap12
                ? document.getElementsByTagNameNS(SOAP12_NS, "Subcode").item(0)
                : document.getElementsByTagName("faultcode").item(0);
        assertNotNull(code, body);
        String[] name = code.getTextContent().trim().split(":", 2);
        assertEquals(2, name.length, body);
        assertEquals(expectedCode, name[1], body);
        // SOAP 1.2 may declare the prefix on the nested Value element.
        var context = scenario.soap12 ? ((org.w3c.dom.Element) code).getElementsByTagNameNS(SOAP12_NS, "Value").item(0) : code;
        assertEquals(WSSE_NS, context.lookupNamespaceURI(name[0]), body);
    }

    private void assertWireProtection(Scenario scenario, String body) throws Exception {
        assertNotNull(body);
        exchangeWithBody(body);
        var document = parseBody();
        assertEquals(1, document.getElementsByTagNameNS(WSSE_NS, "Security").getLength());
        if (scenario.profile.signed()) {
            assertEquals(1, document.getElementsByTagNameNS(DS_NS, "Signature").getLength());
            assertEquals(1, document.getElementsByTagNameNS(WSU_NS, "Timestamp").getLength());
        }
        if (scenario.profile.encrypted()) {
            assertEquals(1, document.getElementsByTagNameNS(XmlEncryptionUtil.XENC_NS, "EncryptedData").getLength());
            assertFalse(body.contains("Hello CXF"), "Encrypted wire message must not expose the payload");
        }
        if (scenario.profile == Profile.USERNAME_TOKEN) {
            assertEquals(1, document.getElementsByTagNameNS(WSSE_NS, "UsernameToken").getLength());
        }
    }

    private Echo start(Scenario scenario, Attack attack) throws Exception {
        bus = new ExtensionManagerBus();
        int backendPort = freePort();
        JaxWsServerFactoryBean factory = new JaxWsServerFactoryBean();
        factory.setBus(bus);
        factory.setServiceClass(Echo.class);
        factory.setServiceBean(new EchoService(invocations));
        factory.setBindingId(scenario.binding());
        factory.setAddress("http://127.0.0.1:" + backendPort + "/echo");
        server = factory.create();
        // Expose the local test peer's exact rejection reason so failures cannot pass for another cause.
        server.getEndpoint().put(SecurityConstants.RETURN_SECURITY_ERROR, true);
        if (scenario.direction == Direction.MEMBRANE_TO_CXF) {
            server.getEndpoint().getInInterceptors().add(new WSS4JInInterceptor(cxfProperties(scenario, Attack.NONE)));
            server.getEndpoint().getInInterceptors().add(coverage(scenario));
            server.getEndpoint().getOutInterceptors().add(new WSS4JOutInterceptor(cxfProperties(scenario, Attack.NONE)));
        }

        int proxyPort = freePort();
        proxyRouter = new TestRouter();
        ServiceProxy proxy = new ServiceProxy(new ServiceProxyKey(proxyPort), "127.0.0.1", backendPort);
        RequestInterceptor request = new RequestInterceptor();
        ResponseInterceptor response = new ResponseInterceptor();
        boolean securingRequest = scenario.direction == Direction.MEMBRANE_TO_CXF;
        var security = membrane(scenario.profile, securingRequest, securingRequest ? attack : Attack.NONE);
        var wire = wireObserver(attack);
        request.setFlow(securingRequest ? List.of(security, wire) : List.of(wire, security));
        // Response children run in reverse order: capture the actual secured bytes at the boundary.
        var responseSecurity = membrane(scenario.profile, !securingRequest, Attack.NONE);
        var responseWire = new AbstractInterceptor() {
            @Override
            public Outcome handleResponse(Exchange exc) {
                String body = assertDoesNotThrow(() -> exc.getResponse().getBodyAsStringDecoded());
                securedResponse.set(body);
                if (exc.getResponse().getStatusCode() >= 400) receiverFault.set(body);
                return Outcome.CONTINUE;
            }
        };
        response.setFlow(securingRequest ? List.of(responseSecurity, responseWire) : List.of(responseWire, responseSecurity));
        proxy.getFlow().add(request);
        proxy.getFlow().add(response);
        proxyRouter.add(proxy);
        proxyRouter.start();

        JaxWsProxyFactoryBean clientFactory = new JaxWsProxyFactoryBean();
        clientFactory.setBus(bus);
        clientFactory.setServiceClass(Echo.class);
        clientFactory.setBindingId(scenario.binding());
        clientFactory.setAddress("http://127.0.0.1:" + proxyPort + "/echo");
        Echo echo = (Echo) clientFactory.create();
        client = ClientProxy.getClient(echo);
        var http = ((HTTPConduit) client.getConduit()).getClient();
        http.setConnectionTimeout(5_000);
        http.setReceiveTimeout(10_000);
        if (scenario.direction == Direction.CXF_TO_MEMBRANE) {
            client.getOutInterceptors().add(new WSS4JOutInterceptor(cxfProperties(scenario, attack)));
            client.getInInterceptors().add(new WSS4JInInterceptor(cxfProperties(scenario, Attack.NONE)));
            client.getInInterceptors().add(coverage(scenario));
        }
        return echo;
    }

    private AbstractInterceptor wireObserver(Attack attack) {
        return new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                String body = assertDoesNotThrow(() -> exc.getRequest().getBodyAsStringDecoded());
                if (attack == Attack.TAMPERED_BODY) {
                    assertTrue(body.contains("Hello CXF"));
                    body = body.replace("Hello CXF", "Tampered CXF");
                    exc.getRequest().setBodyContent(body.getBytes(UTF_8));
                } else if (attack == Attack.MISSING_SECURITY) {
                    com.predic8.membrane.core.http.XmlDomBody.modify(exc.getRequest(), doc -> {
                        var header = doc.getElementsByTagNameNS(WSSE_NS, "Security").item(0);
                        assertNotNull(header);
                        header.getParentNode().removeChild(header);
                    });
                    body = assertDoesNotThrow(() -> exc.getRequest().getBodyAsStringDecoded());
                }
                securedRequest.set(body);
                return Outcome.CONTINUE;
            }
        };
    }

    private WsSecurityInterceptor membrane(Profile profile, boolean secure, Attack attack) {
        List<SecurePart> outbound = new ArrayList<>();
        List<ValidatePart> inbound = new ArrayList<>();
        if (profile.encrypted()) inbound.add(decrypt(encryptedBodyReference()));
        if (profile.signed()) {
            TimestampSecurePart timestamp = new TimestampSecurePart();
            if (attack == Attack.EXPIRED_TIMESTAMP) {
                timestamp = new TimestampSecurePart() {
                    @Override
                    void process(WsSecurityContext ctx) {
                        super.process(ctx);
                        ctx.security().getElementsByTagNameNS(WSU_NS, "Created").item(0)
                                .setTextContent(Instant.now().minusSeconds(1200).truncatedTo(ChronoUnit.MILLIS).toString());
                        ctx.security().getElementsByTagNameNS(WSU_NS, "Expires").item(0)
                                .setTextContent(Instant.now().minusSeconds(900).truncatedTo(ChronoUnit.MILLIS).toString());
                    }
                };
            }
            outbound.add(timestamp);
            SignatureSecurePart signature = attack == Attack.UNSIGNED_BODY
                    ? signature(reference(TIMESTAMP)) : signature(bodyReference(), reference(TIMESTAMP));
            signature.setSecurityTokenReference(new SecurityTokenReferenceKeyInfo());
            outbound.add(signature);
            inbound.add(new TimestampValidatePart());
            inbound.add(requiring(bodyReference(), reference(TIMESTAMP)));
        }
        if (profile == Profile.USERNAME_TOKEN) {
            UsernameTokenSecurePart token = new UsernameTokenSecurePart();
            token.setUsername("alice");
            token.setPassword(attack == Attack.WRONG_PASSWORD ? "wrong" : KEYSTORE_PASSWORD);
            token.setPasswordType(UsernameTokenSecurePart.PasswordType.PLAIN_TEXT);
            outbound.add(token);
            var users = new StaticUserDataProvider();
            users.setUsers(List.of(new StaticUserDataProvider.UserConfig("alice", KEYSTORE_PASSWORD)));
            var validation = new UsernameTokenValidatePart();
            validation.setUserDataProvider(users);
            inbound.add(validation);
        }
        if (profile.encrypted()) {
            outbound.add(attack == Attack.MISSING_ENCRYPTION ? new TimestampSecurePart()
                    : encrypt(ALIAS_1, encryptedBodyReference()));
        }
        WsSecurityInterceptor interceptor = new WsSecurityInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                Outcome outcome = super.handleRequest(exc);
                if (!secure && outcome == Outcome.ABORT) {
                    try {
                        receiverFault.set(exc.getResponse().getBodyAsStringDecoded());
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
                return outcome;
            }
        };
        if (secure) {
            var group = new SecureGroup();
            group.setSecureParts(outbound);
            interceptor.setSecure(group);
        } else {
            var group = new ValidateGroup();
            group.setValidateParts(inbound);
            interceptor.setValidate(group);
        }
        interceptor.setKeyStore(signingKeyStore(attack == Attack.UNTRUSTED_CERTIFICATE ? ALIAS_2 : ALIAS_1));
        interceptor.setTrustStore(trustStore(TRUSTSTORE));
        return interceptor;
    }

    private Map<String, Object> cxfProperties(Scenario scenario, Attack attack) throws Exception {
        Merlin crypto = new Merlin();
        crypto.setKeyStore(loadStore("/alias-keystore.p12"));
        crypto.setTrustStore(loadStore("/alias-truststore.p12"));
        // Verification must trust only the configured peer, not every private-key entry.
        Merlin verification = new Merlin();
        verification.setTrustStore(loadStore("/alias-truststore.p12"));
        Map<String, Object> props = new HashMap<>();
        // WSS4J executes Timestamp before signing but appends Signature when this action order
        // is used. Membrane requires the signed timestamp to precede its signature in the header.
        props.put(ACTION, switch (scenario.profile) {
            case SIGNATURE -> "Signature Timestamp";
            case USERNAME_TOKEN -> "UsernameToken";
            case ENCRYPTION -> attack == Attack.MISSING_ENCRYPTION ? "Timestamp" : "Encrypt";
            case SIGN_THEN_ENCRYPT -> "Signature Timestamp Encrypt";
        });
        props.put(USER, scenario.profile == Profile.USERNAME_TOKEN ? "alice" : ALIAS_1);
        props.put(SIGNATURE_USER, attack == Attack.UNTRUSTED_CERTIFICATE ? ALIAS_2 : ALIAS_1);
        props.put(PW_CALLBACK_REF, (CallbackHandler) callbacks -> {
            for (var callback : callbacks) {
                var password = (WSPasswordCallback) callback;
                password.setPassword(attack == Attack.WRONG_PASSWORD ? "wrong" : KEYSTORE_PASSWORD);
            }
        });
        props.put("crypto", crypto);
        props.put(SIG_PROP_REF_ID, "crypto");
        props.put("verification", verification);
        props.put(SIG_VER_PROP_REF_ID, "verification");
        props.put(DEC_PROP_REF_ID, "crypto");
        props.put(ENC_PROP_REF_ID, "verification");
        props.put(ENCRYPTION_USER, ALIAS_1);
        props.put(ENC_KEY_ID, "Thumbprint");
        props.put(ENC_SYM_ALGO, XmlEncryptionUtil.AES256_GCM);
        props.put(ENC_KEY_TRANSPORT, XmlEncryptionUtil.RSA_OAEP);
        props.put(ENC_DIGEST_ALGO, "http://www.w3.org/2001/04/xmlenc#sha256");
        props.put(ENC_MGF_ALGO, "http://www.w3.org/2009/xmlenc11#mgf1sha256");
        props.put(ENCRYPTION_PARTS, "{Content}{" + scenario.soapNamespace() + "}Body");
        props.put(PASSWORD_TYPE, "PasswordText");
        props.put(SIG_ALGO, "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256");
        props.put(SIG_KEY_ID, "DirectReference");
        props.put(SIG_DIGEST_ALGO, "http://www.w3.org/2001/04/xmlenc#sha256");
        props.put(SIGNATURE_PARTS, (attack == Attack.UNSIGNED_BODY ? ""
                : "{Element}{" + scenario.soapNamespace() + "}Body;") + "{Element}{" + WSU_NS + "}Timestamp");
        if (attack == Attack.EXPIRED_TIMESTAMP) {
            Action expiredTimestamp = (handler, token, data) -> {
                data.getWssConfig().setCurrentTime(() -> Instant.now().minusSeconds(1200));
                new TimestampAction().execute(handler, token, data);
            };
            props.put(WSS4JOutInterceptor.WSS4J_ACTION_MAP, Map.of(WSConstants.TS, expiredTimestamp));
        }
        return props;
    }

    private CryptoCoverageChecker coverage(Scenario scenario) {
        List<CryptoCoverageChecker.XPathExpression> required = new ArrayList<>();
        if (scenario.profile.signed()) {
            required.add(new CryptoCoverageChecker.XPathExpression("/s:Envelope/s:Body", SIGNED, ELEMENT));
            required.add(new CryptoCoverageChecker.XPathExpression("/s:Envelope/s:Header/*/u:Timestamp", SIGNED, ELEMENT));
        }
        if (scenario.profile.encrypted()) {
            required.add(new CryptoCoverageChecker.XPathExpression("/s:Envelope/s:Body", ENCRYPTED, CONTENT));
        }
        return new CryptoCoverageChecker(Map.of("s", scenario.soapNamespace(), "u", WSU_NS), required);
    }

    private KeyStore loadStore(String path) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, path);
            store.load(input, KEYSTORE_PASSWORD.toCharArray());
        }
        return store;
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @AfterEach
    void stop() {
        try {
            if (client != null) client.destroy();
        } finally {
            try {
                if (proxyRouter != null) {
                    proxyRouter.stop();
                    proxyRouter.getTimerManager().shutdown();
                }
            } finally {
                try {
                    if (server != null) server.destroy();
                } finally {
                    if (bus != null) bus.shutdown(true);
                    router.getTimerManager().shutdown();
                }
            }
        }
    }

    @WebService(targetNamespace = SERVICE_NS)
    public interface Echo {
        String echo(String value);
    }

    @WebService(endpointInterface = "com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityCxfInteropTest$Echo",
            targetNamespace = SERVICE_NS)
    public static class EchoService implements Echo {
        private final AtomicInteger invocations;

        public EchoService(AtomicInteger invocations) {
            this.invocations = invocations;
        }

        @Override
        public String echo(String value) {
            invocations.incrementAndGet();
            return value;
        }
    }
}
