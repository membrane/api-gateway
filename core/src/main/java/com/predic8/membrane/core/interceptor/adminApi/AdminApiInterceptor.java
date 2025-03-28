/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.adminApi;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchangestore.ExchangeQueryResult;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.rest.QueryParameter;
import com.predic8.membrane.core.openapi.util.PathDoesNotMatchException;
import com.predic8.membrane.core.proxies.AbstractServiceProxy;
import com.predic8.membrane.core.transport.ws.WebSocketConnectionCollection;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.bornium.http.util.UriUtil.queryToParameters;
import static com.predic8.membrane.core.http.Header.X_FORWARDED_FOR;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.util.UriTemplateMatcher.matchTemplate;
import static com.predic8.membrane.core.transport.http2.Http2ServerHandler.HTTP2;

@MCElement(name = "adminApi")
public class AdminApiInterceptor extends AbstractInterceptor {

    static final DateTimeFormatter isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private static final ObjectMapper om = new ObjectMapper();

    private MemoryWatcher memoryWatcher = new MemoryWatcher();
    private WebSocketConnectionCollection connections = new WebSocketConnectionCollection();

    @Override
    public void init() {
        memoryWatcher.init(router.getTimerManager(), connections);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        try {
            String uri = exc.getRequestURI();
            if (uri.matches(".*/health.*")) {
                return handleHealth(exc);
            } else if (uri.matches(".*/apis.*")) {
                return handleApis(exc);
            } else if (uri.matches(".*/calls.*")) {
                return handleCalls(exc);
            } else if (uri.matches(".*/ws.*")) {
                return new AdminApiObserver().handle(exc, connections);
            } else if (uri.matches(".*/exchange/\\d*")) {
                Map<String, String> params = matchTemplate(".*/exchange/{id}", uri);
                return handleExchangeDetails(exc, params.get("id"));
            }
        } catch (PathDoesNotMatchException e) {
            return ABORT;
        }
        return CONTINUE;
    }

    // TODO Use actual current membrane version, add memory, disk usage, etc.
    private Outcome handleHealth(Exchange exc) {
        exc.setResponse(Response.ok().body("{\"version\": \"6.0.0\", \"status\": \"ok\"}").contentType(APPLICATION_JSON).build());
        return RETURN;
    }

    private Outcome handleApis(Exchange exc) {
        List<AbstractServiceProxy> rules = router.getRuleManager().getRules().stream()
                .filter(AbstractServiceProxy.class::isInstance)
                .map(AbstractServiceProxy.class::cast)
                .toList();

        try {
            StringWriter writer = new StringWriter();
            JsonGenerator gen = om.getFactory().createGenerator(writer);
            gen.writeStartArray();
            IntStream.range(0, rules.size()).forEach(i -> {
                AbstractServiceProxy p = rules.get(i);
                try {
                    gen.writeStartObject();

                    gen.writeNumberField("order", i + 1);
                    gen.writeStringField("name", p.toString());
                    gen.writeBooleanField("active", p.isActive());
                    if (!p.isActive())
                        gen.writeStringField("error", p.getErrorState());

                    gen.writeObjectFieldStart("key");
                    gen.writeStringField("method", p.getKey().getMethod());
                    gen.writeStringField("path", p.getKey().getMethod());
                    if (p.getKey().getHost() != null) {
                        gen.writeStringField("host", p.getKey().getHost());
                    } else {
                        gen.writeStringField("address", p.getKey().getIp());
                    }
                    gen.writeStringField("port", String.valueOf(p.getKey().getPort()));
                    gen.writeEndObject();

                    if (p.getTargetHost() != null || p.getTargetURL() != null) {
                        gen.writeObjectFieldStart("target");

                        if (p.getTargetHost() != null) {
                            gen.writeStringField("host", p.getTargetHost());
                            gen.writeNumberField("port", p.getTargetPort());
                        } else {
                            gen.writeStringField("url", p.getTargetURL());
                        }

                        gen.writeEndObject();
                    } else {
                        gen.writeNullField("target");
                    }

                    gen.writeObjectFieldStart("stats");
                    gen.writeNumberField("count", p.getStatisticCollector().getCount());
                    gen.writeEndObject();

                    gen.writeEndObject();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            gen.writeEndArray();
            gen.close();
            exc.setResponse(Response.ok().body(writer.toString()).contentType(APPLICATION_JSON).build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return RETURN;
    }


    private Outcome handleCalls(Exchange exc) {
        ExchangeQueryResult res = null;
        try {
            QueryParameter qp = new QueryParameter(queryToParameters(new URI(exc.getRequestURI()).getQuery()), null);
            res = getRouter().getExchangeStore().getFilteredSortedPaged(qp, false);

            StringWriter writer = new StringWriter();
            JsonGenerator gen = om.getFactory().createGenerator(writer);
            gen.writeStartArray();
            for (AbstractExchange e : res.getExchanges()) {
                writeExchange(e, gen);
            }
            gen.writeEndArray();
            gen.close();

            exc.setResponse(Response.ok().body(writer.toString()).contentType(APPLICATION_JSON).build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return RETURN;
    }

    private Outcome handleExchangeDetails(Exchange exc, String id) {
        try {
            long exchangeId = Long.parseLong(id);
            AbstractExchange exchange = router.getExchangeStore().getExchangeById(exchangeId);

            if (exchange == null) {
                exc.setResponse(Response.notFound().build());
                return RETURN;
            }

            StringWriter writer = new StringWriter();
            JsonGenerator gen = om.getFactory().createGenerator(writer);
            writeExchangeDetailed(exchange, gen);
            gen.close();

            exc.setResponse(Response.ok().body(writer.toString()).contentType(APPLICATION_JSON).build());
            return RETURN;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeExchange(AbstractExchange exc, JsonGenerator gen) throws IOException {
        gen.writeStartObject();

        gen.writeNumberField("id", exc.getId());
        gen.writeStringField("api", exc.getProxy().toString());

        gen.writeObjectFieldStart("request");
        gen.writeNumberField("port", exc.getProxy().getKey().getPort());
        gen.writeStringField("method", exc.getRequest().getMethod());
        gen.writeStringField("path", exc.getRequest().getUri());
        gen.writeStringField("client", getClientAddr(false, exc));
        gen.writeEndObject();

        if (exc.getResponse() != null) {
            gen.writeObjectFieldStart("response");
            gen.writeNumberField("statusCode", exc.getResponse().getStatusCode());
            gen.writeEndObject();
        } else {
            gen.writeNullField("response");
        }

        if (exc.getServer() != null) {
            gen.writeObjectFieldStart("target");
            gen.writeStringField("host", exc.getServer());
            gen.writeNumberField("port",  getServerPort(exc));
            gen.writeEndObject();
        } else {
            gen.writeNullField("target");
        }

        gen.writeObjectFieldStart("stats");
        gen.writeStringField("time", isoFormatter.format(exc.getTime().toInstant().atZone(ZoneId.systemDefault())));
        gen.writeNumberField("duration", exc.getTimeResReceived() - exc.getTimeReqSent());
        gen.writeEndObject();

        gen.writeEndObject();
    }

    private void writeExchangeDetailed(AbstractExchange exc, JsonGenerator gen) throws IOException {
        gen.writeStartObject();
        gen.writeObjectFieldStart("request");
        if (exc.getRequest() != null) {
            Message request = exc.getRequest();
            gen.writeStringField("protocol", exc.getProperty(HTTP2) != null ? "HTTP/2" : request.getVersion());
            gen.writeObjectFieldStart("headers");
            for (HeaderField hf : request.getHeader().getAllHeaderFields()) {
                gen.writeStringField(hf.getHeaderName().toString(), hf.getValue());
            }
            gen.writeEndObject();
            gen.writeStringField("contentType", exc.getRequestContentType());
            if (exc.getRequestContentLength() != -1) {
                gen.writeNumberField("contentLength", exc.getRequestContentLength());
            } else {
                gen.writeNullField("contentLength");
            }
            if (!request.isBodyEmpty()) {
                gen.writeStringField("bodyRaw", request.getBodyAsStringDecoded());
            } else {
                gen.writeNullField("bodyRaw");
            }
        } else {
            gen.writeNullField("protocol");
            gen.writeNullField("headers");
            gen.writeNullField("contentType");
            gen.writeNullField("contentLength");
            gen.writeNullField("bodyRaw");
        }
        gen.writeEndObject();
        gen.writeObjectFieldStart("response");
        if (exc.getResponse() != null) {
            gen.writeStringField("statusMessage", exc.getResponse().getStatusMessage());
            gen.writeObjectFieldStart("headers");
            for (HeaderField hf : exc.getResponse().getHeader().getAllHeaderFields()) {
                gen.writeStringField(hf.getHeaderName().toString(), hf.getValue());
            }
            gen.writeEndObject();
            gen.writeStringField("contentType", exc.getResponseContentType());
            if (exc.getResponseContentLength() != -1) {
                gen.writeNumberField("contentLength", exc.getResponseContentLength());
            } else {
                gen.writeNullField("contentLength");
            }
            if (!exc.getResponse().isBodyEmpty()) {
                gen.writeStringField("bodyRaw", exc.getResponse().getBodyAsStringDecoded());
            } else {
                gen.writeNullField("bodyRaw");
            }
        } else {
            gen.writeNullField("statusMessage");
            gen.writeNullField("headers");
            gen.writeNullField("contentType");
            gen.writeNullField("contentLength");
            gen.writeNullField("bodyRaw");
        }
        gen.writeEndObject();
        gen.writeStringField("state", exc.getStatus().toString());
        gen.writeEndObject();
    }

    public static String getClientAddr(boolean useXForwardedForAsClientAddr, AbstractExchange exc) {
        if (useXForwardedForAsClientAddr) {
            Request request = exc.getRequest();
            if (request != null) {
                Header header = request.getHeader();
                if (header != null) {
                    String value = header.getFirstValue(X_FORWARDED_FOR);
                    if (value != null)
                        return value;
                }
            }
        }
        return exc.getRemoteAddr();
    }

    private int getServerPort(AbstractExchange exc) {
        return exc.getProxy()instanceof AbstractServiceProxy?((AbstractServiceProxy) exc.getProxy()).getTargetPort():-1;
    }

    public MemoryWatcher getMemoryWatcher() {
        return memoryWatcher;
    }
}
