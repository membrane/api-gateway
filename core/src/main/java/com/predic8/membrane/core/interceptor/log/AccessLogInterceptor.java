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
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private Map<String, String> getAdditionalProvidedPattern(Exchange exchange, Map<String, String> existingPatternMap) {
        if (additionalPatternList.isEmpty()) return Map.of();

        return additionalPatternList.stream().map(additionalPattern -> {
            if (!additionalPattern.isOverride() && existingPatternMap.containsKey(additionalPattern.getCreate())) {
                return null;
            }

            var value = new SpelExpressionParser()
                    .parseExpression(additionalPattern.getWithExchange()).getValue(exchange);

            return new AbstractMap.SimpleEntry<>(additionalPattern.getCreate(), value != null ? value.toString() : additionalPattern.getOrDefaultValue());
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void fillMDCMap(Exchange exchange) {
        var contextMap = normalize(getDefinitelySetProperties(exchange));
        var responseMap = normalize(getMaybeSetResposeProperties(exchange));

        // possible override of key in order of importance
        contextMap.putAll(responseMap);
        contextMap.putAll(getAdditionalProvidedPattern(exchange, contextMap));

        MDC.setContextMap(contextMap);
    }

    private Map<String, String> normalize(Map<String, Object> input) {
        return input.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry ->
                        entry.getValue() != null ? entry.getValue().toString() : defaultValue));
    }

    private Map<String, Object> getDefinitelySetProperties(Exchange exchange) {
        return Map.of(
                "ip", exchange.getRemoteAddrIp(),
                "time", convert(exchange.getTimeReqReceived()),
                "host", exchange.getOriginalHostHeaderHost(),
                "port", exchange.getOriginalHostHeaderPort(),
                "uri", exchange.getRequestURI(),
                "proto", exchange.getRequest().getHeader().getFirstValue("x-forwarded-proto").toUpperCase(),
                "http.version", exchange.getRequest().getVersion(),
                "http.method", exchange.getRequest().getMethod(),
                "statusCode", defaultValue
        );
    }

    private String convert(long timestamp) {
        return dateTimeFormat.format(timestamp);
    }

    private Map<String, Object> getMaybeSetResposeProperties(Exchange exchange) {
        if (exchange.getResponse() == null) return Map.of();

        return Map.of(
                "statusCode", exchange.getResponse().getStatusCode(),
                // TODO this will disable "streaming" as noticed in the method
                // "payload.size", exchange.getResponse().getBody().getLength()
                "payload.size", exchange.getResponse().getHeader().getFirstValue("content-length")
        );
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

    Logger getLog() {
        return log;
    }
}
