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

package com.predic8.membrane.core.interceptor.adminapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.model.IExchangesStoreListener;
import com.predic8.membrane.core.proxies.Proxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.adminapi.AdminApiInterceptor.writeExchange;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ServerSentEventsExchangeWatcher implements IExchangesStoreListener {

    private static final Logger log = LoggerFactory.getLogger(ServerSentEventsExchangeWatcher.class);

    private static final ObjectMapper om = new ObjectMapper();

    static final int QUEUE_CAPACITY = 1000;
    static final String CONNECTED_COMMENT = ": connected\n\n";
    private static final String KEEPALIVE_COMMENT = ": keepalive\n\n";

    private final Set<BlockingQueue<String>> subscribers = ConcurrentHashMap.newKeySet();
    private final long keepaliveMillis;

    public ServerSentEventsExchangeWatcher(long keepaliveMillis) {
        this.keepaliveMillis = keepaliveMillis;
    }

    public Response createStreamingResponse() {
        final var response = Response.ok().build();
        response.getHeader().setContentType("text/event-stream; charset=utf-8");
        response.getHeader().add(CACHE_CONTROL, "no-cache");
        response.getHeader().add(TRANSFER_ENCODING, "chunked");
        response.getHeader().removeFields(CONTENT_LENGTH);
        response.setBody(new LiveCallsBody());
        return response;
    }

    int getSubscriberCount() {
        return subscribers.size();
    }

    @Override
    public void addExchange(Proxy proxy, AbstractExchange exc) {
        if (subscribers.isEmpty() || exc.getRequest() == null)
            return;

        final String event;
        try {
            event = "id: %d\nevent: call\ndata: %s\n\n".formatted(exc.getId(), toJson(exc));
        } catch (IOException e) {
            log.error("Could not serialize exchange {} for SSE live update.", exc.getId(), e);
            return;
        }

        for (final var queue : subscribers) {
            if (!queue.offer(event))
                log.debug("SSE subscriber queue full, dropping live update for exchange {}.", exc.getId());
        }
    }

    private static String toJson(AbstractExchange exc) throws IOException {
        final var writer = new StringWriter();
        try (final var gen = om.getFactory().createGenerator(writer)) {
            writeExchange(exc, gen);
        }
        return writer.toString();
    }

    private class LiveCallsBody extends AbstractBody {

        @Override
        protected boolean readChunksWithin(long maxLength) {
            return true;
        }

        @Override
        public void write(AbstractBodyTransferer transferer, boolean retainBody) {
            final var queue = new ArrayBlockingQueue<String>(QUEUE_CAPACITY);
            subscribers.add(queue);
            try {
                send(transferer, CONNECTED_COMMENT);
                while (!Thread.currentThread().isInterrupted()) {
                    final var event = queue.poll(keepaliveMillis, MILLISECONDS);
                    send(transferer, event != null ? event : KEEPALIVE_COMMENT);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                log.debug("SSE live calls client disconnected.", e);
            } finally {
                subscribers.remove(queue);
                try {
                    transferer.finish(null);
                } catch (IOException e) {
                    log.debug("", e);
                }
            }
        }

        private static void send(AbstractBodyTransferer transferer, String text) throws IOException {
            transferer.write(new Chunk(text.getBytes(UTF_8)));
        }

        @Override
        public long getLength() {
            return -1;
        }

        @Override
        protected byte[] getRawLocal() {
            throw new ReadingBodyException("Streaming body cannot be materialized.");
        }

        @Override
        public InputStream getContentAsStream() throws ReadingBodyException {
            throw new ReadingBodyException("The admin API live calls stream cannot be read by an interceptor.");
        }

        @Override
        protected void writeAlreadyRead(AbstractBodyTransferer out) {
        }

        @Override
        protected void writeStreamed(AbstractBodyTransferer out) {
        }
    }

    @Override
    public void removeExchange(AbstractExchange exchange) {
    }

    @Override
    public void removeExchanges(Proxy parent, AbstractExchange[] exchanges) {
    }

    @Override
    public void removeExchanges(AbstractExchange[] exchanges) {
    }

    @Override
    public void setExchangeFinished(AbstractExchange exchange) {
    }

    @Override
    public void setExchangeStopped(AbstractExchange exchange) {
    }

    @Override
    public void refresh() {
    }
}
