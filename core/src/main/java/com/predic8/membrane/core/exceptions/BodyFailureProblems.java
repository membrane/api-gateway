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

package com.predic8.membrane.core.exceptions;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.DecodingException;
import com.predic8.membrane.core.http.InvalidContentTypeException;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.multipart.XopReassemblyException;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.util.ExceptionUtil.getRootCause;
import static com.predic8.membrane.core.util.text.TextUtil.escapeControlCharsForLog;
import static org.apache.commons.lang3.exception.ExceptionUtils.throwableOfType;

/**
 * Problem details for a {@link ReadingBodyException}: which end of the exchange a body failure is
 * charged to, and what the sender learns about it.
 */
public final class BodyFailureProblems {

    private BodyFailureProblems() {
    }

    /**
     * Describes a failure to read a message body, attributed to the end of the exchange it belongs
     * to: a request body the sender could not deliver in readable form is the sender's error, while
     * anything else - a backend response body above all - is the gateway's. Every place that answers
     * a {@link ReadingBodyException} with a response should describe it here instead of deciding a
     * status of its own, so the same broken body is not a 400 in one plugin and a 500 in the next.
     *
     * <p>A request body failure with a known cause gets a subtype of its own, which production
     * keeps:</p>
     * <ul>
     *     <li>{@link DecodingException}: names the Content-Encoding that failed in a field; the
     *     detail carries the decoder's wording, so production withholds it.</li>
     *     <li>{@link InvalidContentTypeException}: the detail names the header the sender sent; the
     *     parser's reason stays internal.</li>
     *     <li>{@link XopReassemblyException}: the detail is written for the sender and says it all.
     *     The exception is not attached, since the reassembly already logged it and production
     *     has nothing to hide behind a log key.</li>
     * </ul>
     * <p>Any other request body failure gets the plain user type, explained by the message of its
     * root cause.</p>
     *
     * @param exchange the exchange the failure happened on, to tell whose body it was
     * @implNote sets the {@code see} suffix itself. {@link ProblemDetails#addSubSee} concatenates without a
     * separator, so a caller adding one of its own would run the two together into a single
     * malformed segment.
     */
    public static ProblemDetails bodyFailure(boolean production, String component, Exchange exchange,
                                             ReadingBodyException failure) {
        if (!failure.isRequestBodyFailure(exchange))
            return internal(production, component)
                    .addSubSee("reading-body")
                    .detail(failure.belongsTo(exchange.getResponse())
                            ? "Could not read the response body from the backend."
                            : "Could not read the message body.")
                    .exception(failure)
                    // An I/O error is explained by its message and the place named in the detail.
                    .stacktrace(!failure.exceptionMessageIsSufficient());

        final var problem = user(production, component).flow(REQUEST).addSubSee("reading-body");

        final var xop = throwableOfType(failure, XopReassemblyException.class);
        if (xop != null)
            return problem.addSubType("xop")
                    .title("XOP package could not be reassembled")
                    .detail(xop.getMessage(), true);

        // The exception makes production log what it withholds, under the key it gives the sender
        problem.exception(failure).stacktrace(!failure.exceptionMessageIsSufficient());

        final var decoding = throwableOfType(failure, DecodingException.class);
        if (decoding != null)
            return problem.addSubType("body-decoding")
                    .title("Request body could not be decoded")
                    .topLevel("contentEncoding", decoding.getContentEncoding())
                    .detail(decoding.getMessage());

        final var contentType = throwableOfType(failure, InvalidContentTypeException.class);
        if (contentType != null)
            // Production logs the detail and the internal fields at info, so both escape the header value
            return problem.addSubType("content-type")
                    .title("Content-Type header could not be parsed")
                    .detail("The Content-Type header \"%s\" could not be parsed.".formatted(
                            escapeControlCharsForLog(contentType.getContentType())), true)
                    .internal("reason", escapeControlCharsForLog(contentType.getCause().getMessage()));

        return problem.detail(getRootCause(failure).getMessage());
    }
}
