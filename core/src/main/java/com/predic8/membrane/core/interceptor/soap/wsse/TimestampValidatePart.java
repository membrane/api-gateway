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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.util.ConfigurationException;

import java.time.Duration;
import java.time.format.DateTimeParseException;

/**
 * @description Requires the inbound <code>wsse:Security</code> header to carry a
 * <code>wsu:Timestamp</code> whose window covers the current time, and rejects the message otherwise
 * — with <code>wsse:MessageExpired</code> when the window has passed. Both
 * <code>wsu:Created</code> and <code>wsu:Expires</code> are checked, so a timestamp carrying no
 * <code>Expires</code> at all still goes stale.
 * <p>On its own this bounds how long a captured message stays usable, which is all that is needed when
 * the channel is already authenticated some other way (TLS client certificates, an API key). It does
 * not make the window trustworthy: nothing stops whoever can modify the message from rewriting the
 * timestamp too. Add a <code>signature</code> with a <code>TIMESTAMP</code> required reference for
 * that — it checks the same window, and covering it with a signature is what makes rewriting it
 * impossible. Listing both is not redundant when the signature is optional in your policy but the
 * timestamp is not.</p>
 */
@MCElement(name = "timestamp", component = false, id = "wsSecurity-validate-timestamp")
public class TimestampValidatePart extends ValidatePart {

    private static final Duration DEFAULT_CLOCK_SKEW = Duration.ofMinutes(5);

    private Duration clockSkew = DEFAULT_CLOCK_SKEW;

    @Override
    void process(WsSecurityContext ctx) {
        WsuTimestamps.checkFreshness(WsuTimestamps.requireSingleTimestamp(ctx.security()), clockSkew);
    }

    public String getClockSkew() {
        return clockSkew.toString();
    }

    /**
     * @description Tolerance, as an ISO-8601 duration, applied when checking the timestamp's
     * <code>Created</code>/<code>Expires</code> against the current time. It absorbs the clock
     * difference between the sender and this gateway, so it also widens the window a captured message
     * stays replayable in — keep it as small as the clocks allow.
     * @default PT5M
     */
    @MCAttribute
    public void setClockSkew(String clockSkew) {
        Duration parsed;
        try {
            parsed = Duration.parse(clockSkew);
        } catch (DateTimeParseException e) {
            throw new ConfigurationException("clockSkew \"" + clockSkew +
                    "\" is not a valid ISO-8601 duration. Use time-based units (days, hours, minutes, " +
                    "seconds), e.g. \"PT5M\" for 5 minutes; calendar units like months are not supported.", e);
        }
        if (parsed.isNegative()) {
            throw new ConfigurationException("clockSkew must not be negative.");
        }
        this.clockSkew = parsed;
    }
}
