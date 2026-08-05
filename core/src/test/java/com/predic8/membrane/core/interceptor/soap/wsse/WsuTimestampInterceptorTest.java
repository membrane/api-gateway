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

import java.time.Duration;
import java.time.Instant;

import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSSE_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXml.WSU_NS;
import static org.junit.jupiter.api.Assertions.*;

class WsuTimestampInterceptorTest extends AbstractWsseInterceptorTest {

    WsuTimestampInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WsuTimestampInterceptor();
        interceptor.init(router);
    }

    private static String envelopeWithTimestamps(String... timestamps) {
        return """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Header>
                        <wsse:Security xmlns:wsse="%s">%s
                        </wsse:Security>
                    </soap:Header>
                    <soap:Body>
                        <foo>bar</foo>
                    </soap:Body>
                </soap:Envelope>
                """.formatted(WSSE_NS, String.join("", timestamps));
    }

    private static String timestamp(String created, String expires) {
        return """

                            <wsu:Timestamp xmlns:wsu="%s">
                                <wsu:Created>%s</wsu:Created>
                                <wsu:Expires>%s</wsu:Expires>
                            </wsu:Timestamp>""".formatted(WSU_NS, created, expires);
    }

    private Document addTimestampAndParse(String body) throws Exception {
        exchangeWithBody(body);
        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));
        return parseBody();
    }

    private static Instant instantAt(Document doc, String localName) {
        return Instant.parse(firstByTag(doc, WSU_NS, localName).getTextContent());
    }

    @Test
    void addsTimestampWithDefaultTtl() throws Exception {
        Document result = addTimestampAndParse(SOAP_BODY);

        firstByTag(result, WSSE_NS, "Security");
        firstByTag(result, WSU_NS, "Timestamp");

        Instant created = instantAt(result, "Created");
        assertEquals(Duration.ofMinutes(5), Duration.between(created, instantAt(result, "Expires")));

        Instant now = Instant.now();
        assertTrue(created.isBefore(now.plusSeconds(5)) && created.isAfter(now.minusSeconds(30)));
    }

    @Test
    void ttlIsConfigurable() throws Exception {
        interceptor.setTtl("PT1M");

        Document result = addTimestampAndParse(SOAP_BODY);

        assertEquals(Duration.ofMinutes(1),
                Duration.between(instantAt(result, "Created"), instantAt(result, "Expires")));
    }

    @Test
    void replacesExistingTimestampInsteadOfDuplicatingIt() throws Exception {
        exchangeWithBody(envelopeWithTimestamps(timestamp("2000-01-01T00:00:00Z", "2000-01-01T00:05:00Z")));

        Instant before = Instant.now();
        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exchange));
        Instant after = Instant.now();

        Document result = parseBody();
        // firstByTag (via instantAt) already asserts exactly one wsu:Created remains.
        Instant created = instantAt(result, "Created");
        assertFalse(created.isBefore(before.minusSeconds(1)));
        assertFalse(created.isAfter(after.plusSeconds(1)));
        assertEquals(Duration.ofMinutes(5), Duration.between(created, instantAt(result, "Expires")));
    }

    @Test
    void replacesMultipleExistingTimestampsInsteadOfLeavingOneBehind() throws Exception {
        Document result = addTimestampAndParse(envelopeWithTimestamps(
                timestamp("2000-01-01T00:00:00Z", "2000-01-01T00:05:00Z"),
                timestamp("2000-01-01T00:01:00Z", "2000-01-01T00:06:00Z")));

        // firstByTag already asserts exactly one wsu:Timestamp/Created remains.
        firstByTag(result, WSU_NS, "Created");
    }

    @Test
    void nonSoapMessageAborts() throws Exception {
        exchangeWithBody("<foo>bar</foo>");

        assertAborts(interceptor, 400);
    }
}
