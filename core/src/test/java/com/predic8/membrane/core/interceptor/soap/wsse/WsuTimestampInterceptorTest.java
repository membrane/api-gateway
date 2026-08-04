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
import com.predic8.membrane.core.router.DefaultRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.time.Duration;
import java.time.Instant;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSSE_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSU_NS;
import static org.junit.jupiter.api.Assertions.*;

class WsuTimestampInterceptorTest {

    private static final String SOAP_BODY = """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Body>
                    <foo>bar</foo>
                </soap:Body>
            </soap:Envelope>
            """;

    DefaultRouter router;
    Exchange exchange;
    WsuTimestampInterceptor interceptor;

    @BeforeEach
    void setUp() {
        router = new DefaultRouter();
        interceptor = new WsuTimestampInterceptor();
        interceptor.init(router);
    }

    private void exchangeWithBody(String body) throws Exception {
        exchange = new Exchange(null);
        exchange.setRequest(new Request.Builder()
                .post("/service")
                .contentType(TEXT_XML)
                .body(body)
                .build());
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
    void addsTimestampWithDefaultTtl() throws Exception {
        exchangeWithBody(SOAP_BODY);

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));

        Document result = parseResultBody();
        assertNotNull(firstByTag(result, WSSE_NS, "Security"));
        firstByTag(result, WSU_NS, "Timestamp");

        Instant created = Instant.parse(firstByTag(result, WSU_NS, "Created").getTextContent());
        Instant expires = Instant.parse(firstByTag(result, WSU_NS, "Expires").getTextContent());
        assertEquals(Duration.ofMinutes(5), Duration.between(created, expires));

        Instant now = Instant.now();
        assertTrue(created.isBefore(now.plusSeconds(5)) && created.isAfter(now.minusSeconds(30)));
    }

    @Test
    void ttlIsConfigurable() throws Exception {
        interceptor.setTtl("PT1M");
        exchangeWithBody(SOAP_BODY);

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));

        Document result = parseResultBody();
        Instant created = Instant.parse(firstByTag(result, WSU_NS, "Created").getTextContent());
        Instant expires = Instant.parse(firstByTag(result, WSU_NS, "Expires").getTextContent());
        assertEquals(Duration.ofMinutes(1), Duration.between(created, expires));
    }

    @Test
    void replacesExistingTimestampInsteadOfDuplicatingIt() throws Exception {
        exchangeWithBody("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Header>
                        <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                            <wsu:Timestamp xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
                                <wsu:Created>2000-01-01T00:00:00Z</wsu:Created>
                                <wsu:Expires>2000-01-01T00:05:00Z</wsu:Expires>
                            </wsu:Timestamp>
                        </wsse:Security>
                    </soap:Header>
                    <soap:Body>
                        <foo>bar</foo>
                    </soap:Body>
                </soap:Envelope>
                """);

        Instant before = Instant.now();
        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));
        Instant after = Instant.now();

        Document result = parseResultBody();
        // firstByTag already asserts exactly one wsu:Timestamp/Created remains.
        Instant created = Instant.parse(firstByTag(result, WSU_NS, "Created").getTextContent());
        Instant expires = Instant.parse(firstByTag(result, WSU_NS, "Expires").getTextContent());
        assertFalse(created.isBefore(before.minusSeconds(1)));
        assertFalse(created.isAfter(after.plusSeconds(1)));
        assertEquals(Duration.ofMinutes(5), Duration.between(created, expires));
    }

    @Test
    void replacesMultipleExistingTimestampsInsteadOfLeavingOneBehind() throws Exception {
        exchangeWithBody("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Header>
                        <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                            <wsu:Timestamp xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
                                <wsu:Created>2000-01-01T00:00:00Z</wsu:Created>
                                <wsu:Expires>2000-01-01T00:05:00Z</wsu:Expires>
                            </wsu:Timestamp>
                            <wsu:Timestamp xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
                                <wsu:Created>2000-01-01T00:01:00Z</wsu:Created>
                                <wsu:Expires>2000-01-01T00:06:00Z</wsu:Expires>
                            </wsu:Timestamp>
                        </wsse:Security>
                    </soap:Header>
                    <soap:Body>
                        <foo>bar</foo>
                    </soap:Body>
                </soap:Envelope>
                """);

        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));

        Document result = parseResultBody();
        // firstByTag already asserts exactly one wsu:Timestamp/Created remains.
        firstByTag(result, WSU_NS, "Created");
    }

    @Test
    void nonSoapMessageAborts() throws Exception {
        exchangeWithBody("<foo>bar</foo>");

        assertEquals(Outcome.ABORT, interceptor.handleRequest(exchange));
        assertEquals(400, exchange.getResponse().getStatusCode());
    }
}
