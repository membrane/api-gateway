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
import com.predic8.membrane.core.interceptor.statistics.util.JDBCUtil;
import com.predic8.membrane.core.openapi.util.PathDoesNotMatchException;
import com.predic8.membrane.core.proxies.AbstractServiceProxy;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

import static com.bornium.http.util.UriUtil.queryToParameters;
import static com.predic8.membrane.core.http.Header.X_FORWARDED_FOR;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.util.UriTemplateMatcher.matchTemplate;
import static com.predic8.membrane.core.transport.http2.Http2ServerHandler.HTTP2;

@MCElement(name = "adminApi")
public class AdminApiInterceptor extends AbstractInterceptor {

    static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final ObjectMapper om = new ObjectMapper();

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
            } else if (uri.matches(".*/exchange/\\d*")) {
                Map<String, String> params = matchTemplate(".*/exchange/{id}", uri);
                return handleExchangeDetails(exc, params.get("id"));
            }
        } catch (PathDoesNotMatchException e) {
            return ABORT;
        }
        return CONTINUE;
    }

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
                    gen.writeStringField("method", p.getKey().getMethod());
                    gen.writeStringField("proxy", generateProxy(p));
                    gen.writeStringField("target", generateTarget(p));
                    gen.writeNumberField("count", p.getStatisticCollector().getCount());
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

    static String generateProxy(AbstractServiceProxy p) {
        return p.getHost() + ":" + p.getPort() + (p.getPath().getValue() != null ? p.getPath().getValue() : "/");
    }

    static String generateTarget(AbstractServiceProxy p) {
        return (p.getTargetHost() == null) ?  p.getTargetURL() : p.getTargetHost() + ":" + p.getTargetPort();
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
        if (exc.getResponse() != null) {
            gen.writeNumberField("statusCode", exc.getResponse().getStatusCode());
        } else {
            gen.writeNullField("statusCode");
            gen.writeNullField("respContentLength");
        }
        gen.writeStringField("time", getTimeOrDate(exc));
        gen.writeStringField("proxy", exc.getProxy().toString());
        gen.writeNumberField("listenPort", exc.getProxy().getKey().getPort());
        if (exc.getRequest() != null) {
            gen.writeStringField("method", exc.getRequest().getMethod());
            gen.writeStringField("path", exc.getRequest().getUri());
        } else {
            gen.writeNullField("method");
            gen.writeNullField("path");
        }
        gen.writeStringField("client", getClientAddr(false, exc));
        gen.writeStringField("server", exc.getServer());
        gen.writeNumberField("serverPort",  getServerPort(exc));
        gen.writeNumberField("duration",
                exc.getTimeResReceived() - exc.getTimeReqSent());
        gen.writeStringField("msgFilePath", JDBCUtil.getFilePath(exc));
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
        gen.writeStringField("msgFilePath", JDBCUtil.getFilePath(exc));
        gen.writeEndObject();
    }

    private String getTimeOrDate(AbstractExchange exc) {
        if (exc.getTime().toInstant().isBefore(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant())) {
            return DATE_FORMATTER.withZone(ZoneId.systemDefault()).format(exc.getTime().toInstant());
        }
        return TIME_FORMATTER.withZone(ZoneId.systemDefault()).format(exc.getTime().toInstant());
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
}