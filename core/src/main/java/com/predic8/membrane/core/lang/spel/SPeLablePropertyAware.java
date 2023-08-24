package com.predic8.membrane.core.lang.spel;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.TypedValue;

import java.util.Map;

public interface SPeLablePropertyAware {
    boolean canRead(EvaluationContext context, Object target, String name);
    TypedValue read(EvaluationContext context, Object target, String name);
}
