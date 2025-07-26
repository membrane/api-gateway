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

import static com.predic8.membrane.core.http.Request.*;
import static java.lang.Thread.*;
import static java.nio.charset.StandardCharsets.*;

@MCElement(name = "retries")
public class RetryHandler {

    private static final Logger log = LoggerFactory.getLogger(RetryHandler.class);

    private int retries = 5;

    /**
     * How long to wait between calls to the same destination, in milliseconds.
     * To prevent hammering one target.
     * Between calls to different targets (think servers) this waiting time is not applied.
     * Note: for reasons of code simplicity, this sleeping time is only applied between direct successive calls
     * to the same target. If there are multiple targets like one, two, one and it all goes very fast, then
     * it's possible that the same server gets hit with less time in between.
     */
    private int delay = 10;

    /**
     *
     */
    private double backoffMultiplier = 2;

    private boolean failOverOn5XX = false;

    // TODO Make failOverOn5XX an instance Variable

    public void executeWithRetries(Exchange exc, RetryableCall call) throws Exception {

        Exception exceptionInLastCall = null;
        double currentDelay = this.delay;
        for (int attempt = 0; attempt <= retries; attempt++) {
            String dest = getDestination(exc, attempt);
            log.debug("Attempt #{} from #{} to {}", attempt, retries + 1, dest);
            try {
                if (call.execute(exc, dest, attempt)) {
                    return ;
                }
                int statusCode = exc.getResponse() == null ? 0 : exc.getResponse().getStatusCode();
                if (!shouldRetry(statusCode)) {
                    HttpClientStatusEventBus.reportSuccess(exc, dest);
                    return; // success
                }
            } catch (Exception e) {
                HttpClientStatusEventBus.reportException(exc, e, dest);
                log.debug("Exception in retry #{}", attempt, e);
                exceptionInLastCall = e;

                if (shouldAbortRetries(exc, e, dest, attempt)) {
                    throw e;
                }

                log.debug("Retryable failure on attempt #{} to {}: {}", attempt, dest, e.getMessage());
                exc.trackNodeException(attempt, e);

            } finally {
                if (exceptionInLastCall != null) {
                    exc.trackNodeException(attempt, exceptionInLastCall);
                }
            }
            delayBetweenCalls(exc, currentDelay *= backoffMultiplier);
        }

        if (exceptionInLastCall != null)
            throw exceptionInLastCall;
    }

    private boolean shouldRetry(int statusCode) {

        if (statusCode > 100 && statusCode < 400) {
            return false;
        }
        if (statusCode >= 500 && failOverOn5XX) {
            return true;
        }
        // See <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status/408">408 Request Timeout</a>
        if (statusCode == 408) {
            return true;
        }
        return false;
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
            log.debug("Connection to {} refused.", dest);
            return mayChangeServerStatus(exc) || !hasMultipleNodes(exc);
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
            return mayChangeServerStatus(exc);
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
            return mayChangeServerStatus(exc);
        }
        log.info("Error while attempting to forward request to {}. Reason: {}", dest, e.getMessage());
        logException(exc, attempt, e);
        log.info("", e); // Unknown condition => log stacktrace
        return mayChangeServerStatus(exc); // If not sure, do not retry for non idempotent methods
    }

    private static boolean hasMultipleNodes(Exchange exc) {
        return exc.getDestinations().size() > 1;
    }

    private boolean mayChangeServerStatus(Exchange exc) {
        return switch (exc.getRequest().getMethod()) {
            case METHOD_POST, METHOD_PATCH -> true;
            default -> false;
        };
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

    /**
     * Returns the target destination to use for this attempt.
     *
     * @param counter starting at 0 meaning the first.
     */
    private static String getDestination(Exchange exc, int counter) {
        return exc.getDestinations().get(counter % exc.getDestinations().size());
    }

    @SuppressWarnings("unused")
    public int getRetries() {
        return retries;
    }

    @MCAttribute
    public void setRetries(int retries) {
        this.retries = retries;
    }

    /**
     * Initial delay. Increases with each attempt by backoffMultiplier
     *
     * @param delay
     * @default 10
     * @description Initial delay in milliseconds
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
     * Factor by which the delay is increased after each attempt
     *
     * @param backoffMultiplier factor
     * @default 2
     * @description Factor by which the delay is multiplied
     * @example 2
     */
    @MCAttribute
    public void setBackoffMultiplier(double backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
    }

    public boolean isFailOverOn5XX() {
        return failOverOn5XX;
    }

    /**
     * Controls if 5XX from the server are retried or immediately passed to the client.
     *
     * @default false
     * @description If true retry 5XX status codes
     * @param failOverOn5XX
     */
    @MCAttribute
    public void setFailOverOn5XX(boolean failOverOn5XX) {
        this.failOverOn5XX = failOverOn5XX;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        RetryHandler that = (RetryHandler) o;
        return retries == that.retries && delay == that.delay && Double.compare(backoffMultiplier, that.backoffMultiplier) == 0;
    }

    @Override
    public int hashCode() {
        int result = retries;
        result = 31 * result + delay;
        result = 31 * result + Double.hashCode(backoffMultiplier);
        return result;
    }
}

