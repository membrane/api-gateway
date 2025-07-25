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

import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.http.Request.*;
import static java.lang.Thread.*;
import static java.nio.charset.StandardCharsets.*;

@MCElement(name = "retries")
public class RetryHandler {

    private static final Logger log = LoggerFactory.getLogger(RetryHandler.class);

    private int retries = 5; // Is old maxRetries TODO Duplicated with HttpClientConfiguration, Overwrite?

    /**
     * How long to wait between calls to the same destination, in milliseconds.
     * To prevent hammering one target.
     * Between calls to different targets (think servers) this waiting time is not applied.
     * Note: for reasons of code simplicity, this sleeping time is only applied between direct successive calls
     * to the same target. If there are multiple targets like one, two, one and it all goes very fast, then
     * it's possible that the same server gets hit with less time in between.
     * <p>
     * TODO Make configurable - where?
     */
    private int delay = 10;

    /**
     *
     */
    private double backoffMultiplier = 2;

    /**
     * Needed for MC configuration
     */
    public RetryHandler() {
    }

    public Exchange executeWithRetries(Exchange exc, boolean failOverOn5XX, RetryableCall call) throws Exception {

        Exception exceptionInLastCall = null;
        double delay = this.delay;
        for (int attempt = 0; attempt <= retries; attempt++) {
            String dest = HttpClient.getDestination(exc, attempt);
            log.debug("Attempt #{} from #{} to {} delay {}", attempt, retries + 1, dest, delay);

            try {
                if (call.execute(exc, dest, attempt)) {
                    return exc;
                }
                int statusCode = exc.getResponse() == null ? 0 : exc.getResponse().getStatusCode();
                if (!shouldRetry(statusCode, failOverOn5XX)) {
                    HttpClientStatusEventBus.reportSuccess(exc, dest);
                    return exc; // success
                }
            } catch (Exception e) {
                HttpClientStatusEventBus.reportException(exc, e, dest);
                log.debug("Exception in retry #{}", attempt, e);
                exceptionInLastCall = e;

                if (shouldAbortRetries(exc, e, dest, attempt)) {
                    throw e;
                }

                log.debug("Retryable failure on attempt #{} to {}: {}", attempt, dest, e.getMessage());
                exc.setNodeException(attempt, e);

            } finally {

                if (trackNodeStatus(exc)) {
                    if (exceptionInLastCall != null) {
                        exc.setNodeException(attempt, exceptionInLastCall);
                    }
                }

            }
            delay *= backoffMultiplier;
            delayBetweenCalls(exc, (int)delay);
        }

        if (exceptionInLastCall != null)
            throw exceptionInLastCall;

        return exc;
    }

    private boolean shouldRetry(int statusCode, boolean failOverOn5XX) {

        // TODO Handle 100?

        if (statusCode > 200 && statusCode < 400) {
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
            log.info("Connection to {} refused.", dest);
            return !hasMultipleNodes(exc);
        }
        // The socket read or connection took too long and exceeded the configured timeout.
        // No data was received from the server in time.
        // Causes: Server is overloaded, network latency or drop, TLS handshake took too long
        if (e instanceof SocketTimeoutException) {
            return mayChangeServerStatus(exc) || !hasMultipleNodes(exc);
        }
        // Low-level TCP error, e.g., during write or read.
        if (e instanceof SocketException) {
            if (e.getMessage().contains("abort")) {
                log.info("Connection to {} was aborted externally.", dest);
            } else if (e.getMessage().contains("reset")) {
                log.info("Connection to {} was reset externally.", dest);
            } else {
                logException(exc, attempt, e);
            }
            return mayChangeServerStatus(exc);
        }
        if (e instanceof UnknownHostException) {
            log.warn("Unknown host: {}", dest);
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
        return mayChangeServerStatus(exc); // If not sure, do not retry
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

    private void delayBetweenCalls(Exchange exc, int delay) throws InterruptedException {
        //as documented above, the sleep timeout is only applied between successive calls to the SAME destination.
        if (exc.getDestinations().size() == 1)
            sleep(delay);
    }

    private static boolean trackNodeStatus(Exchange exc) {
        return Boolean.TRUE.equals(exc.getProperty(TRACK_NODE_STATUS));
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
     * Initial delay. Gets with each attempt longer by backoffMultiplier
     *
     * @param delay
     * @default 10 millisecound
     * @description Initial delay in millisecounds
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
     * Factor by with the delay between attempts to call a backend is made longer
     *
     * @param backoffMultiplier factor
     * @default 5 times
     * @description Factor by with the delay is multiplied
     * @example 2
     */
    @MCAttribute
    public void setBackoffMultiplier(double backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
    }
}

