package com.predic8.membrane.core.lang.spel.spelable;

import org.junit.jupiter.api.Test;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.TypedValue;

import static org.junit.jupiter.api.Assertions.*;

class SPeLablePropertyAwareTest implements SPeLablePropertyAware {

    @Test
    void convert() {
        assertEquals("content-type", camelToKebab("contentType"));
        assertEquals("content-type-foo", camelToKebab("contentTypeFoo"));
        assertEquals("content-type-f-object", camelToKebab("contentTypeFObject"));
    }

    @Override
    public boolean canRead(EvaluationContext context, Object target, String name) {
        return false;
    }

    @Override
    public TypedValue read(EvaluationContext context, Object target, String name) {
        return null;
    }
}