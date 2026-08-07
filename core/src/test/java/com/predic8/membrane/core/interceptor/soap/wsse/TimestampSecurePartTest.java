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

import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.WSSE_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.WSU_NS;
import static org.junit.jupiter.api.Assertions.*;

class TimestampSecurePartTest extends AbstractWsSecurityTest {

    TimestampSecurePart timestamp;
    WsSecurityInterceptor wsSecurity;

    @BeforeEach
    void setUp() {
        timestamp = new TimestampSecurePart();
        wsSecurity = securing(timestamp);
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
        wsSecurity.init(router);
        assertEquals(Outcome.CONTINUE, wsSecurity.handleRequest(exchange));
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
        timestamp.setTtl("PT1M");

        Document result = addTimestampAndParse(SOAP_BODY);

        assertEquals(Duration.ofMinutes(1),
                Duration.between(instantAt(result, "Created"), instantAt(result, "Expires")));
    }

    @Test
    void timestampIsTheFirstChildOfSecurity() throws Exception {
        Document result = addTimestampAndParse(SOAP_BODY);

        assertEquals("Timestamp", firstByTag(result, WSSE_NS, "Security").getFirstChild().getLocalName());
    }

    @Test
    void replacesExistingTimestampInsteadOfDuplicatingIt() throws Exception {
        exchangeWithBody(envelopeWithTimestamps(timestamp("2000-01-01T00:00:00Z", "2000-01-01T00:05:00Z")));
        wsSecurity.init(router);

        Instant before = Instant.now();
        assertEquals(Outcome.CONTINUE, wsSecurity.handleRequest(exchange));
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
        wsSecurity.init(router);

        assertAborts(wsSecurity, 400);
    }

    @Test
    void rejectsCalendarBasedTtl() {
        // "P5M" is a valid ISO-8601 *period* (5 months) but not a valid *duration* - Duration only
        // accepts time-based units (days, hours, minutes, seconds).
        RuntimeException e = assertThrows(RuntimeException.class, () -> timestamp.setTtl("P5M"));
        assertTrue(e.getMessage().contains("P5M"));
    }

    @Test
    void rejectsMalformedTtl() {
        assertThrows(RuntimeException.class, () -> timestamp.setTtl("not-a-duration"));
    }
}
