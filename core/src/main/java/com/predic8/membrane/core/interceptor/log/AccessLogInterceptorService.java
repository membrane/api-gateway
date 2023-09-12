package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AccessLogInterceptorService {

    private static final Logger log = LoggerFactory.getLogger(AccessLogInterceptor.class);

    private final SimpleDateFormat dateTimeFormat;
    private final String defaultValue;
    private final List<AdditionalPattern> additionalPatternList;
    private final boolean excludePayloadSize;

    public AccessLogInterceptorService(
            String dateTimePattern,
            String defaultValue,
            List<AdditionalPattern> additionalPatternList,
            boolean excludePayloadSize
    ) {
        this.dateTimeFormat = new SimpleDateFormat(dateTimePattern);
        this.defaultValue = defaultValue;
        this.additionalPatternList = additionalPatternList;
        this.excludePayloadSize = excludePayloadSize;
    }

    /**
     * Handles access logging with custom MDC parameters
     * MDC often uses ThreadLocals to archive thread safety.
     * Using thread pools and reusing borrowed threads could lead to leftover MDC data
     * to be thread pool safe, we simply clear the MDC after usage
     *
     * @param exchange - The HTTP exchange
     */
    public void handleAccessLogging(Exchange exchange) {
        MDC.setContextMap(generateMDCMap(exchange));
        log.info("");
        MDC.clear();
    }

    private Map<String,String> generateMDCMap(Exchange exc) {
        var ctx = new HashMap<>(getBaseProperties(exc));

        ctx.putAll(getTimeProperties(exc));
        ctx.putAll(getTimeProperties2(exc));

        if (!excludePayloadSize) {
            ctx.put("req.payload.size", safe(getPayLoadSize(exc.getRequest())));
            ctx.put("res.payload.size", safe(getPayLoadSize(exc.getResponse())));
        }

        ctx.putAll(getAdditionalProvidedPattern(exc, ctx));
        return ctx;
    }

    private Map<String, String> getTimeProperties2(Exchange exc) {
        return Map.of(
                "time.diff.received.raw", safe(() -> exc.getTimeResReceived() - exc.getTimeReqReceived()),
                "time.diff.received.format", convert(safe(() -> exc.getTimeResReceived() - exc.getTimeReqReceived())),

                "time.diff.sent.raw", safe(() -> exc.getTimeResSent() - exc.getTimeReqSent()),
                "time.diff.sent.format", convert(safe(() -> exc.getTimeResSent() - exc.getTimeReqSent()))
        );
    }

    private Map<String, String> getTimeProperties(Exchange exc) {
        return Map.of(
                "time.req.received.raw", safe(exc::getTimeReqReceived),
                "time.req.received.format", convert(safe(exc::getTimeReqReceived)),
                "time.req.sent.raw", safe(exc::getTimeReqSent),
                "time.req.sent.format", convert(safe(exc::getTimeReqSent)),

                "time.res.received.raw", safe(exc::getTimeResReceived),
                "time.res.received.format", convert(safe(exc::getTimeResReceived)),
                "time.res.sent.raw", safe(exc::getTimeResSent),
                "time.res.sent.format", convert(safe(exc::getTimeResSent))
        );
    }

    private Map<String, String> getBaseProperties(Exchange exc) {
        return Map.of(
                "ip", safe(exc::getRemoteAddrIp),
                "host", safe(exc::getOriginalHostHeaderHost),
                "port", safe(exc::getOriginalHostHeaderPort),
                "uri", safe(exc::getOriginalRequestUri),
                "proto", safe(() -> exc.getRequest().getHeader().getFirstValue("x-forwarded-proto").toUpperCase()),
                "http.version", safe(() -> exc.getRequest().getVersion()),
                "http.method", safe(() -> exc.getRequest().getMethod()),
                "statusCode", safe(() -> getResponseStatusCode(exc))
        );
    }

    private Supplier<Object> getPayLoadSize(Message msg) {
        return () -> {
            try {
                return msg.getBody().getLength();
            } catch (IOException e) {
                return defaultValue;
            }
        };
    }

    private String safe(Supplier<Object> access) {
        return safe(access, defaultValue);
    }

    private String safe(Supplier<Object> access, String defaultValue) {
        try {
            return String.valueOf(access.get());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String getResponseStatusCode(Exchange exchange) {
        if (exchange.getResponse() == null) {
            return defaultValue;
        }

        return String.valueOf(exchange.getResponse().getStatusCode());
    }

    private Map<String, String> getAdditionalProvidedPattern(Exchange exchange, Map<String, String> existingPatternMap) {
        if (additionalPatternList.isEmpty()) return Map.of();

        return additionalPatternList.stream()
                .filter(existingAndNotOverridablePattern(existingPatternMap))
                .map(additionalPatternToMapEntry(exchange))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Function<AdditionalPattern, AbstractMap.SimpleEntry<String, String>> additionalPatternToMapEntry(Exchange exchange) {
        return additionalPattern -> {
            var value = new SpelExpressionParser()
                    .parseExpression(additionalPattern.getWithExchange()).getValue(exchange);
            return new AbstractMap.SimpleEntry<>(additionalPattern.getCreate(), safe(() -> value, additionalPattern.getOrDefaultValue()));
        };
    }

    private static Predicate<AdditionalPattern> existingAndNotOverridablePattern(Map<String, String> existingPatternMap) {
        return additionalPattern ->
            additionalPattern.isOverride() || !existingPatternMap.containsKey(additionalPattern.getCreate());
    }

    private String convert(String timestamp) {
        return dateTimeFormat.format(Long.parseLong(timestamp));
    }

}
