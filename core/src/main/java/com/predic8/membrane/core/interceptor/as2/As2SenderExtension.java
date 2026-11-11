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
 * Sends AS2 messages (RFC 4130) and checks their MDNs. The Membrane AS2 Extension provides the implementation;
 * without it the as2Sender interceptor fails on startup with a ConfigurationException.
 * <p>
 * The interceptor loads keys and certificates and checks the configuration, the extension does the AS2 processing.
 * Membrane's HTTP client sends the AS2 message to the target of the API, the partner.
 * <p>
 * One instance serves all request threads, so the methods are called concurrently. State that
 * {@link #handleResponse(Exchange)} needs from {@link #send(Exchange)} has to be kept on the exchange.
 */
public interface As2SenderExtension {

    /**
     * Called once before any other method.
     */
    void init(As2SenderSettings settings);

    /**
     * Turns the request with the payload into an AS2 message: signed, and encrypted and compressed as configured.
     *
     * @param exchange Exchange whose request body is the payload; its Content-Type becomes the content type of the
     *                 payload, the filename of its Content-Disposition the file name
     * @return <ul>
     * <li>CONTINUE: the request is replaced by the AS2 message, a POST with only the AS2 headers. The other headers
     * of the request are removed, so they do not reach the partner. {@link #handleResponse(Exchange)} is called later
     * if the partner answers.</li>
     * <li>ABORT: the request cannot be sent. The response is set to an error with status 413 if the payload is too
     * large, 400 otherwise, e.g. without payload or Content-Type.</li>
     * </ul>
     */
    Outcome send(Exchange exchange) throws IOException;

    /**
     * Called for each exchange for which {@link #send(Exchange)} returned CONTINUE and a response arrived. If the
     * partner could not be reached, {@link #handleAbort(Exchange)} is called instead.
     * <p>
     * Replaces the response of the partner with the result for the client: status 200 if the partner confirmed the
     * message with a valid MDN, or, without MDN request, answered with 2xx. Status 502 otherwise: an MDN that reports
     * an error or fails verification, a missing MDN, or a non-2xx status of the partner.
     *
     * @param exchange Exchange whose request was sent by {@link #send(Exchange)}
     */
    void handleResponse(Exchange exchange) throws IOException;

    /**
     * Called from the abort flow, e.g. when the partner could not be reached or did not answer in time. The error
     * response of Membrane is kept, but gets the Message-ID if the message was built: after a timeout the partner
     * may have received it, and the client needs the ID to clarify that with the partner.
     *
     * @param exchange Exchange whose request might have been sent by {@link #send(Exchange)}
     */
    void handleAbort(Exchange exchange);
}
