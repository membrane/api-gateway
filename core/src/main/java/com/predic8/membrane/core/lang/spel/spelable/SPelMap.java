package com.predic8.membrane.core.lang.spel.spelable;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.TypedValue;

import java.util.Map;

public class SPelMap<K, V> implements SPeLablePropertyAware {
    protected final Map<K, V> data;

    public SPelMap(Map<K, V> data) {
        this.data = data;
    }

    @Override
    public boolean canRead(EvaluationContext context, Object target, String name) {
        return data.containsKey(name);
    }

    @Override
    public TypedValue read(EvaluationContext context, Object target, String name) {
        return new TypedValue(data.get(name));
    }
}
