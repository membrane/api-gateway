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

package com.predic8.membrane.core.interceptor.as2;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;

import java.io.IOException;

/**
 * Receives AS2 messages (RFC 4130) and creates their MDNs. The Membrane AS2 Extension provides the
 * implementation; without it the as2Receiver interceptor fails on startup with a ConfigurationException.
 * <p>
 * The interceptor loads keys and certificates and checks the configuration, the extension does the AS2 processing.
 * It decides when an MDN reports success: only after the backend answered with 2xx.
 * <p>
 * One instance serves all request threads, so the methods are called concurrently. State that
 * {@link #respond(Exchange, boolean)} needs from {@link #receive(Exchange)} has to be kept on the exchange.
 */
public interface As2ReceiverExtension {

    /**
     * Called once before any other method.
     */
    void init(As2ReceiverSettings settings);

    /**
     * Decrypts, decompresses and verifies the AS2 message in the request.
     * <p>
     * Only synchronous MDNs, returned in the HTTP response, are supported. A request for an asynchronous MDN is
     * rejected like a message that failed verification.
     *
     * @param exchange Exchange with the raw AS2 request
     * @return <ul>
     * <li>CONTINUE: the message was processed. The request body is replaced with the payload, Content-Type and
     * Content-Disposition are taken from the payload, Content-Transfer-Encoding is removed, the other headers are
     * kept.
     * {@link #respond(Exchange, boolean)} is called later for this exchange.</li>
     * <li>RETURN: the message was rejected and the sender requested an MDN. The response is set to the error MDN,
     * sent with status 200.</li>
     * <li>ABORT: the message was rejected and the sender did not request an MDN, or the request is not an AS2
     * message. The response is set to an error with status 403 if the sender could not be authenticated,
     * 413 if the message is too large, 400 otherwise.</li>
     * </ul>
     */
    Outcome receive(Exchange exchange) throws IOException;

    /**
     * Called exactly once for each exchange for which {@link #receive(Exchange)} returned CONTINUE: from the response
     * flow, or from the abort flow when the backend could not be reached or a later interceptor aborted.
     * <p>
     * If the sender requested an MDN, the response is replaced by it, sent with status 200. Otherwise the
     * response is left unchanged, so its status tells the sender whether the message was accepted.
     *
     * @param exchange  Exchange whose request was processed by {@link #receive(Exchange)}
     * @param processed true if the backend accepted the payload (status 2xx): the MDN reports <code>processed</code>.
     *                  false: the MDN reports <code>processed/error: unexpected-processing-error</code>.
     */
    void respond(Exchange exchange, boolean processed) throws IOException;
}
