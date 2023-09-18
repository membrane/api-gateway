/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */


package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.lang.spel.ExchangeEvaluationContext;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AccessLogInterceptorService {

    private static final Logger log = LoggerFactory.getLogger(AccessLogInterceptor.class);

    private final SimpleDateFormat dateTimeFormat;
    private final String defaultValue;
    private final List<AdditionalVariable> additionalVariables;
    private final boolean excludePayloadSize;

    public AccessLogInterceptorService(
            String dateTimePattern,
            String defaultValue,
            List<AdditionalVariable> additionalVariables,
            boolean excludePayloadSize
    ) {
        this.dateTimeFormat = new SimpleDateFormat(dateTimePattern);
        this.defaultValue = defaultValue;
        this.additionalVariables = additionalVariables;
        this.excludePayloadSize = excludePayloadSize;
    }

    /**
     * Handles access logging with custom MDC parameters
     * MDC often uses ThreadLocals to archive thread safety.
     * Using thread pools and reusing borrowed threads could lead to leftover MDC data
     * to be thread pool safe, we simply clear the MDC after usage
     *
     * @param exc - The HTTP exchange
     */
    public void handleAccessLogging(Exchange exc) {
        MDC.setContextMap(generateMDCMap(exc));
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

        ctx.putAll(getAdditionalProvidedPattern(exc));

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
                "statusCode", safe(() -> exc.getResponse().getStatusCode())
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

    private Map<String, String> getAdditionalProvidedPattern(Exchange exc) {
        if (additionalVariables.isEmpty()) return Map.of();

        return additionalVariables.stream()
                .map(additionalPatternToMapEntry(exc))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Function<AdditionalVariable, AbstractMap.SimpleEntry<String, String>> additionalPatternToMapEntry(Exchange exc) {
        return additionalPattern -> new AbstractMap.SimpleEntry<>(
                additionalPattern.getName(),
                safe(() -> new SpelExpressionParser()
                        .parseExpression(additionalPattern.getExpression())
                        .getValue(new ExchangeEvaluationContext(exc)),
                    additionalPattern.getDefaultValue()
                )
        );
    }

    private String convert(String timestamp) {
        return dateTimeFormat.format(Long.parseLong(timestamp));
    }
}
