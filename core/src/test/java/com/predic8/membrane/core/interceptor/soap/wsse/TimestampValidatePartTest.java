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
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityFaultCode.*;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.WSSE_NS;
import static com.predic8.membrane.core.interceptor.soap.wsse.WsSecurityXmlUtil.WSU_NS;
import static org.junit.jupiter.api.Assertions.*;

class TimestampValidatePartTest extends AbstractWsSecurityTest {

    private WsSecurityInterceptor wsSecurity;

    private void exchangeWithTimestamp(String timestampChildren) throws Exception {
        exchangeWithBody("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Header>
                        <wsse:Security xmlns:wsse="%s" xmlns:wsu="%s">
                            %s
                        </wsse:Security>
                    </soap:Header>
                    <soap:Body><foo>bar</foo></soap:Body>
                </soap:Envelope>
                """.formatted(WSSE_NS, WSU_NS, timestampChildren));
        wsSecurity = validating(new TimestampValidatePart());
        wsSecurity.init(router);
    }

    private static String timestamp(Instant created) {
        return "<wsu:Timestamp><wsu:Created>%s</wsu:Created></wsu:Timestamp>".formatted(created);
    }

    private static String timestamp(Instant created, Instant expires) {
        return "<wsu:Timestamp><wsu:Created>%s</wsu:Created><wsu:Expires>%s</wsu:Expires></wsu:Timestamp>"
                .formatted(created, expires);
    }

    @Test
    void freshTimestampIsAccepted() throws Exception {
        Instant now = Instant.now();
        exchangeWithTimestamp(timestamp(now, now.plus(Duration.ofMinutes(5))));

        assertEquals(Outcome.CONTINUE, wsSecurity.handleRequest(exchange));
    }

    @Test
    void expiredTimestampIsRejectedAsMessageExpired() throws Exception {
        Instant created = Instant.now().minus(Duration.ofHours(1));
        exchangeWithTimestamp(timestamp(created, created.plus(Duration.ofMinutes(5))));

        assertFault(wsSecurity, MESSAGE_EXPIRED);
    }

    @Test
    void staleTimestampWithoutExpiresIsRejected() throws Exception {
        // Expires is optional, so Created is what has to keep an old message from being accepted -
        // otherwise a timestamp with no Expires would never go out of date.
        exchangeWithTimestamp(timestamp(Instant.now().minus(Duration.ofHours(1))));

        assertFault(wsSecurity, MESSAGE_EXPIRED);
    }

    @Test
    void timestampFromTheFutureIsRejected() throws Exception {
        // Not expired - a clock that is ahead, or an invented value. Either way not a replay, so it is
        // a failed check rather than MessageExpired.
        exchangeWithTimestamp(timestamp(Instant.now().plus(Duration.ofHours(1))));

        assertFault(wsSecurity, FAILED_CHECK);
    }

    @Test
    void missingTimestampIsRejected() throws Exception {
        exchangeWithTimestamp("");

        assertFault(wsSecurity, INVALID_SECURITY);
    }

    @Test
    void moreThanOneTimestampIsRejected() throws Exception {
        // WS-Security allows one per header; which of two a receiver honoured would otherwise decide
        // whether the message counts as fresh.
        Instant now = Instant.now();
        exchangeWithTimestamp(timestamp(now) + timestamp(now.minus(Duration.ofHours(1))));

        assertFault(wsSecurity, INVALID_SECURITY);
    }

    @Test
    void timestampWithoutCreatedIsRejected() throws Exception {
        exchangeWithTimestamp("<wsu:Timestamp><wsu:Expires>%s</wsu:Expires></wsu:Timestamp>"
                .formatted(Instant.now().plus(Duration.ofMinutes(5))));

        assertFault(wsSecurity, FAILED_CHECK);
    }

    @Test
    void malformedCreatedIsRejected() throws Exception {
        exchangeWithTimestamp("<wsu:Timestamp><wsu:Created>yesterday</wsu:Created></wsu:Timestamp>");

        assertFault(wsSecurity, FAILED_CHECK);
    }

    @Test
    void createdWithANonZuluOffsetIsAccepted() throws Exception {
        // xs:dateTime permits any offset, not only "Z".
        exchangeWithTimestamp(
                "<wsu:Timestamp><wsu:Created>%s</wsu:Created></wsu:Timestamp>"
                        .formatted(Instant.now().atOffset(ZoneOffset.ofHours(2))));

        assertEquals(Outcome.CONTINUE, wsSecurity.handleRequest(exchange));
    }

    @Test
    void clockSkewWidensTheAcceptedWindow() throws Exception {
        Instant created = Instant.now().minus(Duration.ofMinutes(30));
        exchangeWithBody("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Header>
                        <wsse:Security xmlns:wsse="%s" xmlns:wsu="%s">%s</wsse:Security>
                    </soap:Header>
                    <soap:Body><foo>bar</foo></soap:Body>
                </soap:Envelope>
                """.formatted(WSSE_NS, WSU_NS, timestamp(created)));
        TimestampValidatePart timestamp = new TimestampValidatePart();
        timestamp.setClockSkew("PT1H");
        WsSecurityInterceptor lenient = validating(timestamp);
        lenient.init(router);

        assertEquals(Outcome.CONTINUE, lenient.handleRequest(exchange));
    }

    @Test
    void rejectsNegativeClockSkew() {
        assertThrows(ConfigurationException.class, () -> new TimestampValidatePart().setClockSkew("PT-5M"));
    }

    @Test
    void rejectsMalformedClockSkew() {
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> new TimestampValidatePart().setClockSkew("not-a-duration"));
        assertTrue(e.getMessage().contains("not-a-duration"));
    }
}
