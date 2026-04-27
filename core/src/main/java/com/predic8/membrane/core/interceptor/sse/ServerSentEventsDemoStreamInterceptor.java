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

package com.predic8.membrane.core.interceptor.sse;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static java.lang.Thread.sleep;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.now;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * Demo interceptor that creates a Server-Sent Events (SSE) stream.
 */
@MCElement(name = "sseDemoStream")
public class ServerSentEventsDemoStreamInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ServerSentEventsDemoStreamInterceptor.class);

    private int intervalSeconds = 1;
    private int totalEvents = 3600; // 1 hour

    @Override
    public Outcome handleRequest(Exchange exc) {
        exc.setResponse(createStreamingResponse());
        return RETURN;
    }

    private @NotNull Response createStreamingResponse() {
        var r = ok().build();
        r.getHeader().setContentType("text/event-stream; charset=utf-8");
        r.getHeader().add(CACHE_CONTROL, "no-cache");
        r.getHeader().add(TRANSFER_ENCODING, "chunked");
        r.getHeader().removeFields(CONTENT_LENGTH);
        r.setBody(new StreamingBody());
        return r;
    }

    /**
     * Streams events periodically.
     */
    private class StreamingBody extends AbstractBody {

        @Override
        protected void readLocal() {
        }

        @Override
        public void write(AbstractBodyTransferer transferer, boolean retainBody) {
            try {

                for (int i = 0; i < totalEvents; i++) {
                    var data = String.format("""
                            {"event": %d, "timestamp": "%s"}""",
                            i, ISO_INSTANT.format(now())
                    );

                    var msg = "id: %d\nevent: tick\ndata: %s\n\n".formatted(i, data);

                    log.info("Sending event: {}", i);

                    transferer.write(new Chunk(msg.getBytes(UTF_8)));

                    //noinspection BusyWait
                    sleep(intervalSeconds * 1000L);
                }
            } catch (InterruptedException e) {
                log.info("Streaming interrupted", e);
                Thread.currentThread().interrupt();
                throw new RuntimeException("Stream interrupted", e);
            } catch (IOException e) {
                log.debug("SSE client disconnected.", e);
            } finally {
                try {
                    // Closing the response terminates the SSE stream.
                    transferer.finish(null);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

        }

        @Override
        public int getLength() {
            return -1; // Unknown length due to streaming
        }

        @Override
        protected byte[] getRawLocal() {
            throw new ReadingBodyException("Streaming body cannot be materialized.");
        }

        @Override
        public InputStream getContentAsStream() throws ReadingBodyException {
            throw new ReadingBodyException("Streaming body does not support getContentAsStream(). Probably there is an interceptor that reads the body. Take it out or do not use 'sseDemoStream'.");
        }

        @Override
        protected void writeAlreadyRead(AbstractBodyTransferer out) {
        }

        @Override
        protected void writeNotRead(AbstractBodyTransferer out) {
        }

        @Override
        protected void writeStreamed(AbstractBodyTransferer out) {

        }
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    @MCAttribute
    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public int getTotalEvents() {
        return totalEvents;
    }

    @MCAttribute
    public void setTotalEvents(int totalEvents) {
        this.totalEvents = totalEvents;
    }

    @Override
    public String getDisplayName() {
        return "SSE Demo Source";
    }
}
