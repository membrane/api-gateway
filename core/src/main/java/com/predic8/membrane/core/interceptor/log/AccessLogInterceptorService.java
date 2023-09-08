package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.core.exchange.Exchange;
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
        fillMDCMap(exchange);
        log.info("");
        MDC.clear();
    }

    private void fillMDCMap(Exchange exchange) {
        var contextMap = new HashMap<>(Map.of(
                "ip", safe(exchange::getRemoteAddrIp),
                "host", safe(exchange::getOriginalHostHeaderHost),
                "port", safe(exchange::getOriginalHostHeaderPort),
                "uri", safe(exchange::getOriginalRequestUri),
                "proto", safe(() -> exchange.getRequest().getHeader().getFirstValue("x-forwarded-proto").toUpperCase()),
                "http.version", safe(() -> exchange.getRequest().getVersion()),
                "http.method", safe(() -> exchange.getRequest().getMethod()),
                "statusCode", safe(() -> getResponseStatusCode(exchange))
        ));

        contextMap.putAll(Map.of(
                "time.req.received.raw", safe(exchange::getTimeReqReceived),
                "time.req.received.format", convert(safe(exchange::getTimeReqReceived)),
                "time.req.sent.raw", safe(exchange::getTimeReqSent),
                "time.req.sent.format", convert(safe(exchange::getTimeReqSent)),

                "time.res.received.raw", safe(exchange::getTimeResReceived),
                "time.res.received.format", convert(safe(exchange::getTimeResReceived)),
                "time.res.sent.raw", safe(exchange::getTimeResSent),
                "time.res.sent.format", convert(safe(exchange::getTimeResSent))
        ));

        contextMap.putAll(Map.of(
                "time.diff.received.raw", safe(() -> exchange.getTimeResReceived() - exchange.getTimeReqReceived()),
                "time.diff.received.format", convert(safe(() -> exchange.getTimeResReceived() - exchange.getTimeReqReceived())),

                "time.diff.sent.raw", safe(() -> exchange.getTimeResSent() - exchange.getTimeReqSent()),
                "time.diff.sent.format", convert(safe(() -> exchange.getTimeResSent() - exchange.getTimeReqSent()))
        ));

        if (!excludePayloadSize) {
            contextMap.put("res.payload.size", safe(() -> {
                try {
                    return exchange.getResponse().getBody().getLength();
                } catch (IOException e) {
                    return defaultValue;
                }
            }));
            contextMap.put("req.payload.size", safe(() -> {
                try {
                    return exchange.getRequest().getBody().getLength();
                } catch (IOException e) {
                    return defaultValue;
                }
            }));
        }

        contextMap.putAll(getAdditionalProvidedPattern(exchange, contextMap));

        MDC.setContextMap(contextMap);
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
        return additionalPattern -> existingPatternMap.containsKey(additionalPattern.getCreate()) && !additionalPattern.isOverride();
    }

    private String convert(String timestamp) {
        return dateTimeFormat.format(Long.parseLong(timestamp));
    }

}
