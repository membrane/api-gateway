package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @description Writes exchange metrics into a Log4j appender
 * @explanation Defaults to Apache Common Log pattern
 */
@MCElement(name = "accessLog")
public class AccessLogInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AccessLogInterceptor.class);

    private List<AdditionalPattern> additionalPatternList;
    private String defaultValue = "-";
    private String dateTimePattern = "dd/MM/yyyy:HH:mm:ss Z";
    private SimpleDateFormat dateTimeFormat = new SimpleDateFormat(dateTimePattern);

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        handleAccessLogging(exc);
        return super.handleResponse(exc);
    }

    /**
     * Handles access logging with custom MDC parameters
     * MDC often uses ThreadLocals to archive thread safety.
     * Using thread pools and reusing borrowed threads could lead to leftover MDC data
     * to be thread pool safe, we simply clear the MDC after usage
     *
     * @param exchange - The HTTP exchange
     */
    private void handleAccessLogging(Exchange exchange) {
        fillMDCMap(exchange);
        log.info("");
        MDC.clear();
    }

    private void fillMDCMap(Exchange exchange) {

        var contextMap = new HashMap<>(Map.of(
                "ip", exchange.getRemoteAddrIp(),
                "host", exchange.getOriginalHostHeaderHost(),
                "port", exchange.getOriginalHostHeaderPort(),
                "uri", exchange.getOriginalRequestUri(),
                "proto", exchange.getRequest().getHeader().getFirstValue("x-forwarded-proto").toUpperCase(),
                "http.version", exchange.getRequest().getVersion(),
                "http.method", exchange.getRequest().getMethod(),
                "statusCode", getResponseStatusCode(exchange)
        ));

        contextMap.putAll(Map.of(
                "time.req.received.raw", String.valueOf(exchange.getTimeReqReceived()),
                "time.req.received.format", convert(exchange.getTimeReqReceived()),
                "time.req.sent.raw", String.valueOf(exchange.getTimeReqSent()),
                "time.req.sent.format", convert(exchange.getTimeReqSent()),

                "time.res.received.raw", String.valueOf(exchange.getTimeResReceived()),
                "time.res.received.format", convert(exchange.getTimeResReceived()),
                "time.res.sent.raw", String.valueOf(exchange.getTimeResSent()),
                "time.res.sent.format", convert(exchange.getTimeResSent())
        ));

        contextMap.putAll(Map.of(
                "time.diff.received.raw", String.valueOf(exchange.getTimeResReceived() - exchange.getTimeReqReceived()),
                "time.diff.received.format", convert(exchange.getTimeResReceived() - exchange.getTimeReqReceived()),

                "time.diff.sent.raw", String.valueOf(exchange.getTimeResSent() - exchange.getTimeReqSent()),
                "time.diff.sent.format", convert(exchange.getTimeResSent() - exchange.getTimeReqSent())
        ));

        contextMap.putAll(getAdditionalProvidedPattern(exchange, contextMap));

        MDC.setContextMap(contextMap);
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

    private static Function<AdditionalPattern, AbstractMap.SimpleEntry<String, String>> additionalPatternToMapEntry(Exchange exchange) {
        return additionalPattern -> {
            var value = new SpelExpressionParser()
                    .parseExpression(additionalPattern.getWithExchange()).getValue(exchange);
            return new AbstractMap.SimpleEntry<>(additionalPattern.getCreate(), value != null ? value.toString() : additionalPattern.getOrDefaultValue());
        };
    }

    private static Predicate<AdditionalPattern> existingAndNotOverridablePattern(Map<String, String> existingPatternMap) {
        return additionalPattern -> existingPatternMap.containsKey(additionalPattern.getCreate()) && !additionalPattern.isOverride();
    }

    private String convert(long timestamp) {
        return dateTimeFormat.format(timestamp);
    }

    public List<AdditionalPattern> getAdditionalPatternList() {
        return additionalPatternList;
    }

    @MCChildElement
    public void setAdditionalPatternList(List<AdditionalPattern> additionalPatternList) {
        this.additionalPatternList = additionalPatternList;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * @description - Provide a default value if the exchange property could not be found, defaults to "-"
     */
    @MCAttribute
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getDateTimePattern() {
        return dateTimePattern;
    }

    /**
     * @description - Provide a datetime pattern, defaults to "dd/MM/yyyy:HH:mm:ss Z"
     */
    @MCAttribute
    public void setDateTimePattern(String dateTimePattern) {
        this.dateTimePattern = dateTimePattern;
        this.dateTimeFormat = new SimpleDateFormat(dateTimePattern);
    }
}
