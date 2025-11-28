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
package com.predic8.membrane.core.interceptor.adminApi;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchangestore.ExchangeQueryResult;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.AbortInterceptor;
import com.predic8.membrane.core.interceptor.flow.RequestInterceptor;
import com.predic8.membrane.core.interceptor.flow.ResponseInterceptor;
import com.predic8.membrane.core.interceptor.rest.QueryParameter;
import com.predic8.membrane.core.openapi.util.PathDoesNotMatchException;
import com.predic8.membrane.core.proxies.AbstractServiceProxy;
import com.predic8.membrane.core.proxies.Proxy;
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
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.openapi.util.UriTemplateMatcher.matchTemplate;
import static com.predic8.membrane.core.transport.http2.Http2ServerHandler.HTTP2_SERVER;
import static java.net.URLDecoder.decode;
import static java.nio.charset.StandardCharsets.UTF_8;

@MCElement(name = "adminApi")
public class AdminApiInterceptor extends AbstractInterceptor {

    static final DateTimeFormatter isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    private final MemoryWatcher memoryWatcher = new MemoryWatcher();
    private final DiskWatcher diskWatcher = new DiskWatcher();
    private final WebSocketExchangeWatcher wsExchangeWatcher = new WebSocketExchangeWatcher();
    private final WebSocketConnectionCollection connections = new WebSocketConnectionCollection();

    @Override
    public void init() {
        memoryWatcher.init(router.getTimerManager(), connections);
        diskWatcher.init(router.getTimerManager(), connections);
        wsExchangeWatcher.init(connections);
        router.getExchangeStore().addExchangesStoreListener(wsExchangeWatcher);
        super.init();
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
            } else if (uri.matches(".*/api/.*")) {
                Map<String, String> params = matchTemplate(".*/api/{name}", uri);
                return handleApiDetails(exc, params.get("name"));
            } else if (uri.matches(".*/exchange/\\d*")) {
                Map<String, String> params = matchTemplate(".*/exchange/{id}", uri);
                return handleExchangeDetails(exc, params.get("id"));
            } else if (uri.matches(".*/suggestions/\\w*")) {
                Map<String, String> params = matchTemplate(".*/suggestions/{field}", uri);
                return handleFilterSuggestions(exc, params.get("field"));
            }
        } catch (PathDoesNotMatchException e) {
            return Outcome.ABORT;
        }
        return CONTINUE;
    }

    private Outcome handleFilterSuggestions(Exchange exc, String field) {
        StringWriter writer = new StringWriter();
        try {
            JsonGenerator gen = JSON_FACTORY.createGenerator(writer);
            gen.writeStartArray();

            getRouter().getExchangeStore().getUniqueValuesOf(field).forEach(i -> {
                try {
                    gen.writeString(i);
                } catch (JacksonException e) {
                    throw new RuntimeException(e);
                }
            });

            gen.writeEndArray();
            gen.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        exc.setResponse(Response.ok().body(writer.toString()).contentType(APPLICATION_JSON).build());
        return RETURN;
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
            try (JsonGenerator gen = JSON_FACTORY.createGenerator(writer)) {
                gen.writeStartArray();
                IntStream.range(0, rules.size()).forEach(i -> {
                    AbstractServiceProxy p = rules.get(i);
                    try {
                        gen.writeStartObject();

                        gen.writeName("order");
                        gen.writeNumber(i + 1);

                        gen.writeName("name");
                        gen.writeString(p.toString());

                        gen.writeName("active");
                        gen.writeBoolean(p.isActive());
                        if (!p.isActive()) {
                            gen.writeName("error");
                            gen.writeString(p.getErrorState());
                        }

                        gen.writeName("key");
                        gen.writeStartObject();
                        gen.writeName("method");
                        gen.writeString(p.getKey().getMethod());
                        gen.writeName("path");
                        gen.writeString(p.getKey().getMethod());
                        if (p.getKey().getHost() != null) {
                            gen.writeName("host");
                            gen.writeString(p.getKey().getHost());
                        } else {
                            gen.writeName("address");
                            gen.writeString(p.getKey().getIp());
                        }
                        gen.writeName("port");
                        gen.writeString(String.valueOf(p.getKey().getPort()));
                        gen.writeEndObject();

                        gen.writeName("target");
                        if (p.getTargetHost() != null || p.getTargetURL() != null) {
                            gen.writeStartObject();
                            if (p.getTargetHost() != null) {
                                gen.writeName("host");
                                gen.writeString(p.getTargetHost());
                                gen.writeName("port");
                                gen.writeNumber(p.getTargetPort());
                            } else {
                                gen.writeName("url");
                                gen.writeString(p.getTargetURL());
                            }
                            gen.writeEndObject();
                        } else {
                            gen.writeNull();
                        }

                        gen.writeName("stats");
                        gen.writeStartObject();
                        gen.writeName("count");
                        gen.writeNumber(p.getStatisticCollector().getCount());
                        gen.writeEndObject();

                        gen.writeEndObject();
                    } catch (JacksonException e2) {
                        throw new RuntimeException(e2); //TODO is this okay?
                    }
                });
                gen.writeEndArray();
            }
            exc.setResponse(Response.ok().body(writer.toString()).contentType(APPLICATION_JSON).build());
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
        return RETURN;
    }

    private Outcome handleApiDetails(Exchange exc, String name) {
        try {
            Proxy proxy = router.getRuleManager().getRules().stream().filter(p ->
                    p.getName().equals(decode(name, UTF_8))
            ).findFirst().orElse(null);

            if (proxy == null) {
                exc.setResponse(Response.notFound().build());
                return RETURN;
            }

            StringWriter writer = new StringWriter();
            JsonGenerator gen = JSON_FACTORY.createGenerator(writer);
            gen.writeStartArray();
            writePluginRow(proxy.getFlow(), null, gen);
            gen.writeEndArray();
            gen.close();

            exc.setResponse(Response.ok().body(writer.toString()).contentType(APPLICATION_JSON).build());
            return RETURN;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writePluginRow(List<Interceptor> plugins, Flow limitedFlow, JsonGenerator gen) {
        for (Interceptor p : plugins) {
            switch (p) {
                case RequestInterceptor rqi -> writePluginRow(rqi.getFlow(), REQUEST, gen);
                case ResponseInterceptor rsi -> writePluginRow(rsi.getFlow(), RESPONSE, gen);
                case AbortInterceptor ai -> writePluginRow(ai.getFlow(), ABORT, gen);
                default -> {
                    gen.writeStartObject();

                    gen.writeName("flow");
                    gen.writeString(String.valueOf(limitedFlow != null ? limitedFlow : p.getAppliedFlow()));

                    gen.writeName("name");
                    gen.writeString(p.getDisplayName());

                    gen.writeName("shortDescription");
                    gen.writeString(p.getShortDescription());

                    gen.writeName("longDescription");
                    gen.writeString(p.getLongDescription());

                    gen.writeEndObject();
                }
            }
        }
    }


    private Outcome handleCalls(Exchange exc) {
        ExchangeQueryResult res;
        try {
            QueryParameter qp = new QueryParameter(queryToParameters(new URI(exc.getRequestURI()).getQuery()), null);
            res = getRouter().getExchangeStore().getFilteredSortedPaged(qp, false);

            StringWriter writer = new StringWriter();
            JsonGenerator gen = JSON_FACTORY.createGenerator(writer);
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
            JsonGenerator gen = JSON_FACTORY.createGenerator(writer);
            writeExchangeDetailed(exchange, gen);
            gen.close();

            exc.setResponse(Response.ok().body(writer.toString()).contentType(APPLICATION_JSON).build());
            return RETURN;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static void writeExchange(AbstractExchange exc, JsonGenerator gen) throws IOException {
        gen.writeStartObject();

        gen.writeName("id");
        gen.writeNumber(exc.getId());

        gen.writeName("api");
        gen.writeString(exc.getProxy().toString());

        gen.writeName("request");
        gen.writeStartObject();
        gen.writeName("port");
        gen.writeNumber(exc.getProxy().getKey().getPort());
        gen.writeName("method");
        gen.writeString(exc.getRequest().getMethod());
        gen.writeName("path");
        gen.writeString(exc.getRequest().getUri());
        gen.writeName("client");
        gen.writeString(getClientAddr(false, exc));
        gen.writeEndObject();

        gen.writeName("response");
        if (exc.getResponse() != null) {
            gen.writeStartObject();
            gen.writeName("statusCode");
            gen.writeNumber(exc.getResponse().getStatusCode());
            gen.writeEndObject();
        } else {
            gen.writeNull();
        }

        gen.writeName("target");
        if (exc.getServer() != null) {
            gen.writeStartObject();
            gen.writeName("host");
            gen.writeString(exc.getServer());
            gen.writeName("port");
            gen.writeNumber(getServerPort(exc));
            gen.writeEndObject();
        } else {
            gen.writeNull();
        }

        gen.writeName("stats");
        gen.writeStartObject();
        gen.writeName("time");
        gen.writeString(isoFormatter.format(exc.getTime().toInstant().atZone(ZoneId.systemDefault())));
        gen.writeName("duration");
        gen.writeNumber(exc.getTimeResReceived() - exc.getTimeReqSent());
        gen.writeEndObject();

        gen.writeEndObject();
    }

    private void writeExchangeDetailed(AbstractExchange exc, JsonGenerator gen) throws IOException {
        gen.writeStartObject();

        gen.writeName("request");
        gen.writeStartObject();
        if (exc.getRequest() != null) {
            Message request = exc.getRequest();

            gen.writeName("protocol");
            gen.writeString(exc.getProperty(HTTP2_SERVER) != null ? "HTTP/2" : request.getVersion());

            gen.writeName("headers");
            gen.writeStartObject();
            for (HeaderField hf : request.getHeader().getAllHeaderFields()) {
                gen.writeName(hf.getHeaderName().toString());
                gen.writeString(hf.getValue());
            }
            gen.writeEndObject();

            gen.writeName("contentType");
            gen.writeString(exc.getRequestContentType());

            gen.writeName("contentLength");
            if (exc.getRequestContentLength() != -1) {
                gen.writeNumber(exc.getRequestContentLength());
            } else {
                gen.writeNull();
            }

            gen.writeName("bodyRaw");
            if (!request.isBodyEmpty()) {
                gen.writeString(request.getBodyAsStringDecoded());
            } else {
                gen.writeNull();
            }
        } else {
            gen.writeName("protocol");      gen.writeNull();
            gen.writeName("headers");       gen.writeNull();
            gen.writeName("contentType");   gen.writeNull();
            gen.writeName("contentLength"); gen.writeNull();
            gen.writeName("bodyRaw");       gen.writeNull();
        }
        gen.writeEndObject(); // request

        gen.writeName("response");
        gen.writeStartObject();
        if (exc.getResponse() != null) {
            gen.writeName("statusMessage");
            gen.writeString(exc.getResponse().getStatusMessage());

            gen.writeName("headers");
            gen.writeStartObject();
            for (HeaderField hf : exc.getResponse().getHeader().getAllHeaderFields()) {
                gen.writeName(hf.getHeaderName().toString());
                gen.writeString(hf.getValue());
            }
            gen.writeEndObject();

            gen.writeName("contentType");
            gen.writeString(exc.getResponseContentType());

            gen.writeName("contentLength");
            if (exc.getResponseContentLength() != -1) {
                gen.writeNumber(exc.getResponseContentLength());
            } else {
                gen.writeNull();
            }

            gen.writeName("bodyRaw");
            if (!exc.getResponse().isBodyEmpty()) {
                gen.writeString(exc.getResponse().getBodyAsStringDecoded());
            } else {
                gen.writeNull();
            }
        } else {
            gen.writeName("statusMessage"); gen.writeNull();
            gen.writeName("headers");       gen.writeNull();
            gen.writeName("contentType");   gen.writeNull();
            gen.writeName("contentLength"); gen.writeNull();
            gen.writeName("bodyRaw");       gen.writeNull();
        }
        gen.writeEndObject(); // response

        gen.writeName("state");
        gen.writeString(exc.getStatus().toString());

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

    private static int getServerPort(AbstractExchange exc) {
        return exc.getProxy()instanceof AbstractServiceProxy?((AbstractServiceProxy) exc.getProxy()).getTargetPort():-1;
    }

    public MemoryWatcher getMemoryWatcher() {
        return memoryWatcher;
    }
}
