package com.predic8.membrane.core.transport.http.client;

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

public class RetryHandler {

    private static final Logger log = LoggerFactory.getLogger(RetryHandler.class);

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
    private final int delayBetweenTriesMs = 250;

    private final int maxRetries;
    private final HttpClientConfiguration config;

    public RetryHandler(HttpClientConfiguration config, int maxRetries) {
        this.maxRetries = maxRetries;
        this.config = config;
    }

    public Exchange executeWithRetries(Exchange exc, boolean failOverOn5XX, RetryableCall call) throws Exception {

        Exception exceptionInLastCall = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            String dest = HttpClient.getDestination(exc, attempt);
            log.debug("Attempt #{} from #{} to {}", attempt, maxRetries + 1, dest);

            // exceptionInLastCall = null;

            try {
                if (call.execute(exc, dest, attempt)) {
                    log.debug("StatusCode {}", exc.getResponse().getStatusCode());
                    if (!shouldRetryHttpError(exc, failOverOn5XX)) {
                        HttpClientStatusEventBus.reportSuccess(exc, dest);
                        return exc; // success
                    }
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
            delayBetweenCalls(exc);
        }

        if (exceptionInLastCall != null)
            throw exceptionInLastCall;

        return exc;
    }

    private boolean shouldRetryHttpError(Exchange exc, boolean failOverOn5XX) {
        if (!failOverOn5XX) {
            return false;
        }

        int statusCode = exc.getResponse().getStatusCode();

        if (statusCode > 200 && statusCode < 400) {
            return false;
        }

        if (statusCode >= 500) {
            return true;
        }

        // See <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status/408">408 Request Timeout</a>
        if (statusCode == 408) {
            return true;
        }
        return false;
    }

    private boolean shouldAbortRetries(Exchange exc, Exception e, String dest, int attempt) throws Exception {
        log.debug("Checking if call should abort immediately. Exception {}", e.getMessage());

        // switch with throwable is only possible in Java 21 with preview features
        if (e instanceof MalformedURLException) {
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

    private void delayBetweenCalls(Exchange exc) throws InterruptedException {
        //as documented above, the sleep timeout is only applied between successive calls to the SAME destination.
        if (exc.getDestinations().size() == 1)
            sleep(delayBetweenTriesMs);
    }

    private static boolean trackNodeStatus(Exchange exc) {
        return Boolean.TRUE.equals(exc.getProperty(TRACK_NODE_STATUS));
    }
}

