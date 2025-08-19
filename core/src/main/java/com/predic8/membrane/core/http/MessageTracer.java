package com.predic8.membrane.core.http;

import org.slf4j.*;

/**
 * Writes Request and Response messages to the trace log
 *
 */
public class MessageTracer {

    private static final Logger log = LoggerFactory.getLogger(MessageTracer.class);

    private static final int TRACE_BODY_MAX_LEN = getTraceBodyMaximumLength();
    public static final String MEMBRANE_MESSAGE_TRACER_MAX_BODY_LENGTH = "membrane.message.tracer.maxBodyLength";

    /**
     * Writes Request and Response messages to the trace log
     *
     */
    public static void trace(Message message) {
        if (message instanceof Request request)
            traceRequest(request);
        if (message instanceof Response response)
            traceResponse(response);
    }

    private static void traceRequest(Request req) {
        try {
            StringBuilder sb = new StringBuilder("\n---[ HTTP Request ]----------------------------\n");
            sb.append(req.getStartLine())                         // e.g. ?GET /foo HTTP/1.1?
                    .append('\n')
                    .append(req.getHeader().toString());                // all header fields

            // append body if we actually have one and it is already buffered
            if (!req.isBodyEmpty()) {
                sb.append('\n');
                appendBody(sb, req.getBodyAsStringDecoded());
            }
            log.trace(sb.toString());
        } catch (Exception e) {
            log.trace("Could not trace request.", e);
        }
    }

    /**
     * Dumps the incoming HTTP response in TRACE log level.
     */
    private static void traceResponse(Response res) {
        try {
            StringBuilder sb = new StringBuilder("\n---[ HTTP Response ]---------------------------\n");
            sb.append(res.getStartLine())                         // e.g. ?HTTP/1.1 200 OK?
                    .append('\n')
                    .append(res.getHeader().toString());

            if (!res.isBodyEmpty()) {
                sb.append('\n');
                appendBody(sb, res.getBodyAsStringDecoded());
            }
            log.trace(sb.toString());
        } catch (Exception e) {
            log.trace("Could not trace response.", e);
        }
    }

    private static void appendBody(StringBuilder sb, String body) {
        if (body == null || body.isEmpty()) {
            sb.append("[no body]\n");
            return;
        }
        if (body.length() > TRACE_BODY_MAX_LEN) {
            sb.append(body, 0, TRACE_BODY_MAX_LEN)
                    .append("\n...[body truncated]...\n");
        } else {
            sb.append(body).append('\n');
        }
    }

    private static int getTraceBodyMaximumLength() {
        return Integer.parseInt(System.getProperty(MEMBRANE_MESSAGE_TRACER_MAX_BODY_LENGTH, "10240"));
    }
}
