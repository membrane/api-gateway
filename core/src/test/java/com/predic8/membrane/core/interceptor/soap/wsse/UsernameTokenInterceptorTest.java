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
import com.predic8.membrane.core.interceptor.soap.wsse.UsernameTokenInterceptor.PasswordType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.security.MessageDigest;
import java.util.Base64;

import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSSE_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSU_NS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UsernameTokenInterceptorTest extends AbstractWsseInterceptorTest {

    private static final String PASSWORD_TEXT_TYPE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText";
    private static final String PASSWORD_DIGEST_TYPE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest";

    private static final String SOAP_BODY_WITH_HEADER = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Header>
                    <existing>keep-me</existing>
                </soap:Header>
                <soap:Body>
                    <foo>bar</foo>
                </soap:Body>
            </soap:Envelope>
            """;

    UsernameTokenInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new UsernameTokenInterceptor();
    }

    private void exchangeWithCredentialProperties(String body) throws Exception {
        exchangeWithBody(body);
        exchange.setProperty("apiUser", "spelUser");
        exchange.setProperty("apiPassword", "spelPass");
    }

    private Document addTokenAndParse(String body) throws Exception {
        exchangeWithCredentialProperties(body);
        interceptor.init(router);
        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));
        return parseBody();
    }

    @Test
    void staticCredentialsPlainTextCreatesHeaderAndSecurity() throws Exception {
        interceptor.setUsername("bob");
        interceptor.setPassword("secret");

        Document result = addTokenAndParse(SOAP_BODY);

        assertEquals("bob", firstByTag(result, WSSE_NS, "Username").getTextContent());
        Element password = firstByTag(result, WSSE_NS, "Password");
        assertEquals("secret", password.getTextContent());
        assertEquals(PASSWORD_TEXT_TYPE, password.getAttribute("Type"));
    }

    @Test
    void spelExpressionsAreEvaluated() throws Exception {
        interceptor.setUsername("${property.apiUser}");
        interceptor.setPassword("${property.apiPassword}");

        Document result = addTokenAndParse(SOAP_BODY);

        assertEquals("spelUser", firstByTag(result, WSSE_NS, "Username").getTextContent());
        assertEquals("spelPass", firstByTag(result, WSSE_NS, "Password").getTextContent());
    }

    @Test
    void digestPasswordTypeIsVerifiable() throws Exception {
        interceptor.setUsername("bob");
        interceptor.setPassword("secret");
        interceptor.setPasswordType(PasswordType.DIGEST);

        Document result = addTokenAndParse(SOAP_BODY);

        Element password = firstByTag(result, WSSE_NS, "Password");
        Element nonce = firstByTag(result, WSSE_NS, "Nonce");
        Element created = firstByTag(result, WSU_NS, "Created");

        assertEquals(PASSWORD_DIGEST_TYPE, password.getAttribute("Type"));
        assertEquals("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary",
                nonce.getAttribute("EncodingType"));

        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update(Base64.getDecoder().decode(nonce.getTextContent()));
        sha1.update(created.getTextContent().getBytes(UTF_8));
        sha1.update("secret".getBytes(UTF_8));

        assertEquals(Base64.getEncoder().encodeToString(sha1.digest()), password.getTextContent());
    }

    @Test
    void existingHeaderContentIsPreserved() throws Exception {
        interceptor.setUsername("bob");
        interceptor.setPassword("secret");

        Document result = addTokenAndParse(SOAP_BODY_WITH_HEADER);

        assertEquals(1, result.getElementsByTagName("existing").getLength());
        assertEquals("keep-me", result.getElementsByTagName("existing").item(0).getTextContent());
        firstByTag(result, WSSE_NS, "UsernameToken");
    }

    @Test
    void malformedBodyAborts() throws Exception {
        assertTokenCreationAborts("not xml at all");
    }

    @Test
    void xmlWithoutSoapBodyAborts() throws Exception {
        assertTokenCreationAborts("<foo>bar</foo>");
    }

    private void assertTokenCreationAborts(String body) throws Exception {
        interceptor.setUsername("bob");
        interceptor.setPassword("secret");
        exchangeWithBody(body);
        interceptor.init(router);

        assertAborts(interceptor, 400);
    }
}
