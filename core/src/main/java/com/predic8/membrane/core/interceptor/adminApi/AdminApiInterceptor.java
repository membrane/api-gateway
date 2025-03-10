package com.predic8.membrane.core.interceptor.adminApi;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.ExchangeState;
import com.predic8.membrane.core.exchange.ExchangesUtil;
import com.predic8.membrane.core.exchangestore.ExchangeQueryResult;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.rest.QueryParameter;
import com.predic8.membrane.core.interceptor.statistics.util.JDBCUtil;
import com.predic8.membrane.core.openapi.util.PathDoesNotMatchException;
import com.predic8.membrane.core.proxies.AbstractServiceProxy;
import com.predic8.membrane.core.proxies.Proxy;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.IntStream;

import static com.bornium.http.util.UriUtil.queryToParameters;
import static com.predic8.membrane.core.http.Header.X_FORWARDED_FOR;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.util.UriTemplateMatcher.matchTemplate;
import static com.predic8.membrane.core.transport.http2.Http2ServerHandler.HTTP2;

@MCElement(name = "adminApi")
public class AdminApiInterceptor extends AbstractInterceptor {

    private static final ObjectMapper om = new ObjectMapper();

    static {
        om.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        try {
            return switch (matchTemplate(".*/{action}", exc.getRequestURI()).get("action")) {
                case "health" -> handleHealth(exc);
                case "apis" -> handleApis(exc);
                case "calls" -> handleCalls(exc);
                default -> CONTINUE;
            };
        } catch (PathDoesNotMatchException e) {
            // ProblemDetails
            return ABORT;
        }
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
                    gen.writeNumberField("listenPort", p.getKey().getPort());
                    gen.writeStringField("virtualHost", p.getKey().getHost());
                    gen.writeStringField("method", p.getKey().getMethod());
                    gen.writeStringField("path", p.getKey().getPath());
                    gen.writeStringField("targetHost", p.getTargetHost());
                    gen.writeNumberField("targetPort", p.getTargetPort());
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

    private void writeExchange(AbstractExchange exc, JsonGenerator gen) throws IOException {
        gen.writeStartObject();
        gen.writeNumberField("id", exc.getId());
        if (exc.getResponse() != null) {
            gen.writeNumberField("statusCode", exc.getResponse().getStatusCode());
            if (exc.getResponseContentLength()!=-1) {
                gen.writeNumberField("respContentLength", exc.getResponseContentLength());
            } else {
                gen.writeNullField("respContentLength");
            }
        } else {
            gen.writeNullField("statusCode");
            gen.writeNullField("respContentLength");
        }
        gen.writeStringField("time", ExchangesUtil.getTime(exc));
        gen.writeStringField("proxy", exc.getProxy().toString());
        gen.writeNumberField("listenPort", exc.getProxy().getKey().getPort());
        if (exc.getRequest() != null) {
            gen.writeStringField("method", exc.getRequest().getMethod());
            gen.writeStringField("path", exc.getRequest().getUri());
            gen.writeStringField("reqContentType", exc.getRequestContentType());
            gen.writeStringField("protocol", exc.getProperty(HTTP2) != null? "2" : exc.getRequest().getVersion());
        } else {
            gen.writeNullField("method");
            gen.writeNullField("path");
            gen.writeNullField("reqContentType");
            if (exc.getProperty(HTTP2) != null)
                gen.writeStringField("protocol", "2");
            else
                gen.writeNullField("protocol");
        }
        gen.writeStringField("client", getClientAddr(false, exc));
        gen.writeStringField("server", exc.getServer());
        gen.writeNumberField("serverPort",  getServerPort(exc));
        if (exc.getRequest() != null && exc.getRequestContentLength()!=-1) {
            gen.writeNumberField("reqContentLength", exc.getRequestContentLength());
        } else {
            gen.writeNullField("reqContentLength");
        }
        gen.writeStringField("respContentType", exc.getResponseContentType());
        if(exc.getStatus() == ExchangeState.RECEIVED || exc.getStatus() == ExchangeState.COMPLETED)
            if (exc.getResponse() != null && exc.getResponseContentLength()!=-1) {
                gen.writeNumberField("respContentLength", exc.getResponseContentLength());
            } else {
                gen.writeNullField("respContentLength");
            }
        else
            gen.writeStringField("respContentLength", "Not finished");

        gen.writeNumberField("duration",
                exc.getTimeResReceived() - exc.getTimeReqSent());
        gen.writeStringField("msgFilePath", JDBCUtil.getFilePath(exc));
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

    private List<AbstractServiceProxy> getServiceProxies() {
        List<AbstractServiceProxy> rules = new LinkedList<>();
        for (Proxy r : router.getRuleManager().getRules()) {
            if (!(r instanceof AbstractServiceProxy)) continue;
            rules.add((AbstractServiceProxy) r);
        }
        return rules;
    }
}
