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
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.soap.wsse.UsernameTokenInterceptor.PasswordType;
import com.predic8.membrane.core.router.DefaultRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.security.MessageDigest;
import java.util.Base64;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSSE_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSU_NS;
import static org.junit.jupiter.api.Assertions.*;

class UsernameTokenInterceptorTest {

    private static final String SOAP_BODY_NO_HEADER = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Body>
                    <foo>bar</foo>
                </soap:Body>
            </soap:Envelope>
            """;

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

    DefaultRouter router;
    Exchange exchange;
    UsernameTokenInterceptor interceptor;

    @BeforeEach
    void setUp() {
        router = new DefaultRouter();
        interceptor = new UsernameTokenInterceptor();
    }

    private void exchangeWithBody(String body) throws Exception {
        exchange = new Exchange(null);
        exchange.setRequest(new Request.Builder()
                .post("/service")
                .contentType(TEXT_XML)
                .body(body)
                .build());
        exchange.setProperty("apiUser", "spelUser");
        exchange.setProperty("apiPassword", "spelPass");
    }

    private Document parseResultBody() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(exchange.getRequest().getBodyAsStream());
    }

    private static Element firstByTag(Document doc, String namespace, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS(namespace, localName);
        assertEquals(1, nodes.getLength(), "Expected exactly one " + localName + " element");
        return (Element) nodes.item(0);
    }

    @Test
    void staticCredentialsPlainTextCreatesHeaderAndSecurity() throws Exception {
        exchangeWithBody(SOAP_BODY_NO_HEADER);
        interceptor.setUsername("bob");
        interceptor.setPassword("secret");
        interceptor.init(router);

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));

        Document result = parseResultBody();
        Element username = firstByTag(result, WSSE_NS, "Username");
        Element password = firstByTag(result, WSSE_NS, "Password");
        assertEquals("bob", username.getTextContent());
        assertEquals("secret", password.getTextContent());
        assertEquals("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText",
                password.getAttribute("Type"));
    }

    @Test
    void spelExpressionsAreEvaluated() throws Exception {
        exchangeWithBody(SOAP_BODY_NO_HEADER);
        interceptor.setUsername("${property.apiUser}");
        interceptor.setPassword("${property.apiPassword}");
        interceptor.init(router);

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));

        Document result = parseResultBody();
        assertEquals("spelUser", firstByTag(result, WSSE_NS, "Username").getTextContent());
        assertEquals("spelPass", firstByTag(result, WSSE_NS, "Password").getTextContent());
    }

    @Test
    void digestPasswordTypeIsVerifiable() throws Exception {
        exchangeWithBody(SOAP_BODY_NO_HEADER);
        interceptor.setUsername("bob");
        interceptor.setPassword("secret");
        interceptor.setPasswordType(PasswordType.DIGEST);
        interceptor.init(router);

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));

        Document result = parseResultBody();
        Element password = firstByTag(result, WSSE_NS, "Password");
        Element nonce = firstByTag(result, WSSE_NS, "Nonce");
        Element created = firstByTag(result, WSU_NS, "Created");

        assertEquals("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest",
                password.getAttribute("Type"));
        assertEquals("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary",
                nonce.getAttribute("EncodingType"));

        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update(Base64.getDecoder().decode(nonce.getTextContent()));
        sha1.update(created.getTextContent().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        sha1.update("secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String expectedDigest = Base64.getEncoder().encodeToString(sha1.digest());

        assertEquals(expectedDigest, password.getTextContent());
    }

    @Test
    void existingHeaderContentIsPreserved() throws Exception {
        exchangeWithBody(SOAP_BODY_WITH_HEADER);
        interceptor.setUsername("bob");
        interceptor.setPassword("secret");
        interceptor.init(router);

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));

        Document result = parseResultBody();
        assertEquals(1, result.getElementsByTagName("existing").getLength());
        assertEquals("keep-me", result.getElementsByTagName("existing").item(0).getTextContent());
        firstByTag(result, WSSE_NS, "UsernameToken");
    }

    @Test
    void malformedBodyAborts() throws Exception {
        exchangeWithBody("not xml at all");
        interceptor.setUsername("bob");
        interceptor.setPassword("secret");
        interceptor.init(router);

        assertEquals(Outcome.ABORT, interceptor.handleRequest(exchange));
        assertEquals(400, exchange.getResponse().getStatusCode());
    }

    @Test
    void xmlWithoutSoapBodyAborts() throws Exception {
        exchangeWithBody("<foo>bar</foo>");
        interceptor.setUsername("bob");
        interceptor.setPassword("secret");
        interceptor.init(router);

        assertEquals(Outcome.ABORT, interceptor.handleRequest(exchange));
        assertEquals(400, exchange.getResponse().getStatusCode());
    }
}
