package com.predic8.membrane.core.interceptor.httpcache;

import java.util.LinkedList;

import static com.predic8.membrane.core.interceptor.httpcache.CacheControlHeader.httpElementToList;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toCollection;

public class DirectiveArgument<T> {
    private T value;

    public DirectiveArgument(T value) {
        this.value = value;
    }

    public static DirectiveArgument<?> parse(String argument) {
        String arg = argument.trim();

        if (arg.startsWith("\"") && arg.endsWith("\"")) {
            String inner = arg.substring(1, arg.length() - 1);
            LinkedList<String> listValue = httpElementToList(inner);
            return new DirectiveArgument<>(listValue);
        } else {
            try {
                int intArg = Integer.parseInt(arg);
                if (intArg >= 0)
                    return new DirectiveArgument<>(intArg);
            } catch (NumberFormatException ignored) {}
        }

        return new DirectiveArgument<>(0);
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null)
            return false;
        if (getClass() != o.getClass())
            return false;
        DirectiveArgument<?> dir = (DirectiveArgument<?>) o;

        return this.value.equals(dir.getValue());
    }
}
