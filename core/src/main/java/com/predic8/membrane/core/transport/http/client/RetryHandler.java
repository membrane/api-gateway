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

package com.predic8.membrane.core.transport.http.client;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.transport.http.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;

import static com.predic8.membrane.core.transport.http.HttpClientStatusEventBus.*;
import static com.predic8.membrane.core.util.HttpUtil.*;
import static java.lang.Thread.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * <p>Retries a backend request when network-level failures or selected HTTP status codes occur.</p>
 *
 * <p>The handler performs the initial call and, on failure, up to {@link #retries} additional attempts.
 * Waiting time before hitting the <em>same</em> node grows exponentially by
 * {@code delay backoffMultiplier}. If several backend nodes are configured, the next retry is
 * immediately directed to the next node (fail-over)  - the sleep is only applied between consecutive
 * attempts to the <strong>same</strong> destination.</p>
 *
 * <p>A retry is triggered for:</p>
 * <ul>
 *   <li>Connection/IO exceptions (timeout, refused, reset...)</li>
 *   <li>HTTP 408 Request Timeout</li>
 *   <li>HTTP 500, 502, 503, 504, 507 (when {@code failOverOn5XX=true})</li>
 * </ul>
 * <p>
 * Non-idempotent methods (POST, PATCH) are <em>not</em> repeated if the request might already have
 * reached the server.</p>
 */
@MCElement(name = "retries")
public class RetryHandler {

    private static final Logger log = LoggerFactory.getLogger(RetryHandler.class);

    private int retries = 2;

    /**
     * Initial delay before the 1st retry (ms).  Multiplied by {@link #backoffMultiplier} for each
     * further attempt to the same backend.
     */
    private int delay = 100;

    /**
     * Factor applied to {@link #delay} after every retry attempt.
     */
    private double backoffMultiplier = 2;

    /**
     * Retry on HTTP 5xx (only 500, 502, 503, 504, 507) when <code>true</code>,
     * but only for idempotent methods. POST, PATCH and CONNECT will not be retried.
     */
    private boolean failOverOn5XX = false;

    private static final Set<Integer> RETRYABLE_5XX = Set.of(500, 502, 503, 504, 507);

    /**
     * Execute the given {@link RetryableCall} applying the retry logic configured in this handler.
     *
     * @param exc  current exchange
     * @param call operation to run (lambda/functional interface)
     * @throws Exception last encountered exception if all attempts fail
     */
    public void executeWithRetries(Exchange exc, RetryableCall call) throws Exception {
        Exception exceptionInLastCall = null;
        double currentDelay = delay;
        for (int attempt = 0; attempt <= retries; attempt++) {
            String dest = getDestination(exc, attempt);
            log.debug("Attempt #{} from #{} to {}", attempt, retries + 1, dest);
            try {
                if (call.execute(exc, dest, attempt)) {
                    reportStatusCode(exc, dest, exc.getResponse().getStatusCode());
                    return;
                }
                int statusCode = getStatusCode(exc);
                String method = exc.getRequest().getMethod();
                if (!shouldRetry(statusCode) || !isIdempotent(method)) {
                    log.debug("{} with status code {}. No retry.",method, statusCode);
                    reportStatusCode(exc, dest, statusCode);
                    return;
                }
            } catch (Exception e) {
                reportException(exc, e, dest);
                log.debug("Exception in retry #{}", attempt, e);
                exceptionInLastCall = e;

                if (shouldAbortRetries(exc, e, dest, attempt)) {
                    log.debug("Aborting retry #{} due to {}", attempt, e.getMessage());
                    throw e;
                }

                log.debug("Retryable failure on attempt #{} to {}: {}", attempt, dest, e.getMessage());
                exc.trackNodeException(attempt, e);
            }
            if (attempt < retries) {
                delayBetweenCalls(exc, currentDelay);
                currentDelay *= backoffMultiplier;
            }
        }

        if (exceptionInLastCall != null)
            throw exceptionInLastCall;
    }

    private static int getStatusCode(Exchange exc) {
        return exc.getResponse() == null ? 0 : exc.getResponse().getStatusCode();
    }

    /**
      * Decides if an HTTP status warrants a retry under current settings.
      * Rules:
      * - 408: always retry.
      * - 5xx: retry if {@link #failOverOn5XX} is true and status ∈ {500, 502, 503, 504, 507}.
      *
      * @param statusCode HTTP response status code
      * @return true if a retry should be attempted, false otherwise
      */
    private boolean shouldRetry(int statusCode) {
        // Used to timeout preconnections. The client can try again and hope for a new  connection
        // See <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status/408">408 Request Timeout</a>
        if (statusCode == 408) {
            return true;
        }

        if (statusCode < 500 || !failOverOn5XX) {
            return false;
        }

        // Retry, maybe the next node can serve the request
        // 501 Not Implemented: pointless to repeat
        return RETRYABLE_5XX.contains(statusCode);
    }

    private boolean shouldAbortRetries(Exchange exc, Exception e, String dest, int attempt) {
        log.debug("Checking if call should abort immediately. Exception {}", e.getMessage());

        // switch with throwable is only possible in Java 21 with preview features
        if (e instanceof MalformedURLException || e instanceof URISyntaxException) {
            log.debug("URI {} caused: {}", dest, e);
            return true;
        }
        if (e instanceof ConnectException) {
            // Connection was not established, so no state was changed on server
            log.debug("Connection to {} refused.", dest);
            return !hasMultipleNodes(exc);
        }
        // The socket read or connection took too long and exceeded the configured timeout.
        // No data was received from the server in time.
        // Causes: Server is overloaded, network latency or drop, TLS handshake took too long
        if (e instanceof SocketTimeoutException) {
            log.debug("Connection to {} timed out.", dest);
            return !isIdempotent(exc.getRequest().getMethod()) || !hasMultipleNodes(exc);
        }
        // Low-level TCP error, e.g., during write or read.
        if (e instanceof SocketException) {
            if (e.getMessage().contains("abort")) {
                log.debug("Connection to {} was aborted externally.", dest);
            } else if (e.getMessage().contains("reset")) {
                log.debug("Connection to {} was reset externally.", dest);
            } else {
                logException(exc, attempt, e);
                log.info("", e); // Unknown condition => log stacktrace
            }
            return !isIdempotent(exc.getRequest().getMethod());
        }
        if (e instanceof UnknownHostException) {
            log.warn("Unknown host: {}", dest); // Could be a configuration error => WARN
            return !hasMultipleNodes(exc);
        }
        if (e instanceof EOFWhileReadingFirstLineException eofE) {
            log.debug("Server connection to {} terminated before line was read. Line so far: {}", dest, eofE.getLineSoFar());
            return true;
        }
        if (e instanceof NoResponseException) {
            log.debug("Server didn't respond to the request.");
            return !isIdempotent(exc.getRequest().getMethod());
        }
        log.info("Error while attempting to forward request to {}. Reason: {}", dest, e.getMessage());
        logException(exc, attempt, e);
        log.info("", e); // Unknown condition => log stacktrace
        return !isIdempotent(exc.getRequest().getMethod()); // If not sure, do not retry for non idempotent methods
    }

    private static boolean hasMultipleNodes(Exchange exc) {
        return exc.getDestinations().size() > 1;
    }

    private void logException(Exchange exc, int attempt, Exception e) {
        if (!log.isDebugEnabled())
            return;

        StringBuilder msg = new StringBuilder();
        msg.append("try # ");
        msg.append(attempt);
        msg.append(" failed\n");

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exc.getRequest().writeStartLine(baos);
            exc.getRequest().getHeader().write(baos);
            msg.append(ISO_8859_1.decode(ByteBuffer.wrap(baos.toByteArray())));
        } catch (IOException ioe) {
            log.debug("Writing the request into the log caused an exception: ", ioe);
        }

        if (e != null)
            log.debug("{}", msg, e);
        else
            log.debug("{}", msg);
    }

    private void delayBetweenCalls(Exchange exc, double delay) throws InterruptedException {
        //as documented above, the sleep timeout is only applied between successive calls to the SAME destination.
        if (exc.getDestinations().size() == 1) {
            log.debug("Waiting {} ms before next try", delay);
            sleep((long) delay);
        }
    }

    private static String getDestination(Exchange exc, int counter) {
        return exc.getDestinations().get(counter % exc.getDestinations().size());
    }

    @SuppressWarnings("unused")
    public int getRetries() {
        return retries;
    }

    /**
     * @description Number of <em>additional</em> retry attempts after the initial call.
     * @default 2
     * @example 5
     */
    @MCAttribute
    public void setRetries(int retries) {
        this.retries = retries;
    }

    /**
     * @description Initial delay in milliseconds before retrying the same node.
     * @default 100
     * @example 1000
     */
    @MCAttribute
    public void setDelay(int delay) {
        this.delay = delay;
    }

    public int getDelay() {
        return delay;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    /**
     * @description Multiplier applied to the delay after each retry (exponential back-off).
     * @default 2
     * @example 1.5
     */
    @MCAttribute
    public void setBackoffMultiplier(double backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
    }

    public boolean isFailOverOn5XX() {
        return failOverOn5XX;
    }


    /**
     * @description If <code>true</code> retry on HTTP 500, 502, 503, 504 and 507 responses (fail-over).
     * @default false
     */
    @MCAttribute
    public void setFailOverOn5XX(boolean failOverOn5XX) {
        this.failOverOn5XX = failOverOn5XX;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        RetryHandler that = (RetryHandler) o;
        return retries == that.retries &&
               delay == that.delay &&
               Double.compare(backoffMultiplier, that.backoffMultiplier) == 0 &&
               Objects.equals(failOverOn5XX, that.failOverOn5XX);
    }

    @Override
    public int hashCode() {
        int result = retries;
        result = 31 * result + delay;
        result = 31 * result + Double.hashCode(backoffMultiplier);
        result = 31 * result + Boolean.hashCode(failOverOn5XX);
        return result;
    }
}