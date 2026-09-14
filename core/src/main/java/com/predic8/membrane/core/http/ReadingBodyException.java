/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.http;

import com.predic8.membrane.core.exchange.Exchange;

import static com.predic8.membrane.core.util.ExceptionUtil.getRootCause;
import static com.predic8.membrane.core.util.ExceptionUtil.hasCauseMatching;

/**
 * Indicates that an error occurred while reading the body of a message.
 * The 'message' should already be enough to indicate the error.
 * (No need to use {@link com.predic8.membrane.core.util.ExceptionUtil#concatMessageAndCauseMessages(Throwable)}.)
 */
public class ReadingBodyException extends RuntimeException {
    private final Message source;

    /** Associates decoding failures without firing transport body lifecycle events. */
    public ReadingBodyException(Exception e, Message source) {
        super(e);
        this.source = source;
    }

    public ReadingBodyException(Exception e) {
        this(e, null);
    }

    public ReadingBodyException(String message) {
        super(message);
        source = null;
    }

    /**
     * @return whether this exception belongs to the given message through an explicit decoding
     * association or a failure recorded on its body. Useful
     * to tell which end of the exchange the failure belongs to: the client (request body) or the target
     * server (response body).
     * <p>
     * Matches either the recorded exception itself or one wrapping the same root cause. The latter is
     * needed because {@link ChunkedBody#getContentAsStream()} has to honour the {@link java.io.InputStream}
     * contract: it records the failure but throws the raw {@link java.io.IOException}, which a caller
     * may then wrap in a second {@link ReadingBodyException}. That wrapper is not the recorded instance,
     * yet it carries the very same cause object and reports the very same failure.
     */
    public boolean belongsTo(Message message) {
        if (message == null)
            return false;
        // Preserve attribution even when callers wrap the decoding exception again.
        if (hasCauseMatching(this, cause -> cause instanceof ReadingBodyException failure && failure.source == message))
            return true;
        ReadingBodyException recorded = message.getBody().getObservedException();
        return recorded == this
                || (recorded != null && getRootCause(recorded) == getRootCause(this));
    }

    /**
     * @return whether this failure is the sender's to answer for: it belongs to the request body and
     * not to the response body. A body can be read in either flow, so the flow the exception surfaced
     * in does not decide this - only which body it belongs to does. Anything else, a backend response
     * body above all, is the gateway's problem rather than the sender's.
     */
    public boolean isRequestBodyFailure(Exchange exchange) {
        return belongsTo(exchange.getRequest()) && !belongsTo(exchange.getResponse());
    }
}
