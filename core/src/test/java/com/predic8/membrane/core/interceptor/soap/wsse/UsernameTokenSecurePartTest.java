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

import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.soap.wsse.UsernameTokenSecurePart.PasswordType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.security.MessageDigest;
import java.util.Base64;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.WSSE_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.WSU_NS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UsernameTokenSecurePartTest extends AbstractWsSecurityTest {

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

    UsernameTokenSecurePart usernameToken;
    WsSecurityInterceptor wsSecurity;

    @BeforeEach
    void setUp() {
        usernameToken = new UsernameTokenSecurePart();
        wsSecurity = securing(usernameToken);
    }

    private Document addTokenAndParse(String body) throws Exception {
        exchangeWithBody(body);
        exchange.setProperty("apiUser", "spelUser");
        exchange.setProperty("apiPassword", "spelPass");
        wsSecurity.init(router);
        assertEquals(Outcome.CONTINUE, wsSecurity.handleRequest(exchange));
        return parseBody();
    }

    @Test
    void staticCredentialsPlainTextCreatesHeaderAndSecurity() throws Exception {
        usernameToken.setUsername("bob");
        usernameToken.setPassword("secret");

        Document result = addTokenAndParse(SOAP_BODY);

        assertEquals("bob", firstByTag(result, WSSE_NS, "Username").getTextContent());
        Element password = firstByTag(result, WSSE_NS, "Password");
        assertEquals("secret", password.getTextContent());
        assertEquals(PASSWORD_TEXT_TYPE, password.getAttribute("Type"));
    }

    @Test
    void spelExpressionsAreEvaluated() throws Exception {
        usernameToken.setUsername("${property.apiUser}");
        usernameToken.setPassword("${property.apiPassword}");

        Document result = addTokenAndParse(SOAP_BODY);

        assertEquals("spelUser", firstByTag(result, WSSE_NS, "Username").getTextContent());
        assertEquals("spelPass", firstByTag(result, WSSE_NS, "Password").getTextContent());
    }

    /**
     * In the response flow the element secures the response, not the request - a request body that
     * isn't even SOAP has to stay untouched rather than being rejected.
     */
    @Test
    void securesTheResponseWhenRunInTheResponseFlow() throws Exception {
        usernameToken.setUsername("bob");
        usernameToken.setPassword("secret");

        exchangeWithBody("not xml at all, and must stay untouched");
        exchange.setResponse(Response.ok().contentType(TEXT_XML).body(SOAP_BODY).build());
        wsSecurity.init(router);

        assertEquals(Outcome.CONTINUE, wsSecurity.handleResponse(exchange),
                () -> exchange.getResponse().toString());

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document result = factory.newDocumentBuilder().parse(exchange.getResponse().getBodyAsStream());

        assertEquals("bob", firstByTag(result, WSSE_NS, "Username").getTextContent());
        assertEquals("not xml at all, and must stay untouched", rawBody());
    }

    @Test
    void digestPasswordTypeIsVerifiable() throws Exception {
        usernameToken.setUsername("bob");
        usernameToken.setPassword("secret");
        usernameToken.setPasswordType(PasswordType.DIGEST);

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
        usernameToken.setUsername("bob");
        usernameToken.setPassword("secret");

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
        usernameToken.setUsername("bob");
        usernameToken.setPassword("secret");
        exchangeWithBody(body);
        wsSecurity.init(router);

        assertAborts(wsSecurity, 400);
    }
}
