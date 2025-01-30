package com.predic8.membrane.core.interceptor;

import java.util.*;

import static java.util.Optional.empty;

public class InterceptorUtil {

    public static <T extends Interceptor> Optional<T> getFirstInterceptorOfType(List<Interceptor> interceptors, Class<T> type) {
        for (Interceptor i : interceptors) {
            if (type.isAssignableFrom(i.getClass())) {
                return Optional.of(type.cast( i));
            }
        }
        return empty();
    }

}

