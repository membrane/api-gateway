package com.predic8.membrane.core.interceptor.opentelemetry;

import com.predic8.membrane.core.http.HeaderField;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import com.predic8.membrane.core.exchange.Exchange;

import java.util.Arrays;

public class HTTPTraceContextUtil {

    public static TextMapGetter<Exchange> remoteContextGetter() {
        return new TextMapGetter<>() {
            @Override
            public String get(Exchange carrier, String key) {
                if (carrier.getRequest().getHeader().contains(key)) {
                    return carrier.getRequest().getHeader().getFirstValue(key);
                } else return null;
            }

            @Override
            public Iterable<String> keys(Exchange carrier) {
                return Arrays.stream(carrier.getRequest().getHeader().getAllHeaderFields()).map(HeaderField::toString).toList();
            }
        };
    }

    public static TextMapSetter<Exchange> remoteContextSetter() {
        return new TextMapSetter<Exchange>() {
            @Override
            public void set(Exchange carrier, String key, String value) {
                carrier.getRequest().getHeader().add(key,value);
            }
        };
    }
}

