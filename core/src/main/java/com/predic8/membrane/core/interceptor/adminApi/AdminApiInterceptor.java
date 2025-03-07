package com.predic8.membrane.core.interceptor.adminApi;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.openapi.util.PathDoesNotMatchException;
import com.predic8.membrane.core.proxies.AbstractServiceProxy;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.IntStream;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.util.UriTemplateMatcher.matchTemplate;

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
        exc.setResponse(Response.ok().body("ok").build());
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
            exc.setResponse(Response.ok().body(writer.toString()).build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return RETURN;
    }

    private Outcome handleCalls(Exchange exc) {

        return RETURN;
    }
}
