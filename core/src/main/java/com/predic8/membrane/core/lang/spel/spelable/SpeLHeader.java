package com.predic8.membrane.core.lang.spel.spelable;

import com.predic8.membrane.core.http.Header;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.TypedValue;

public class SpeLHeader implements SPeLablePropertyAware {

    private final Header header;

    public SpeLHeader(Header header) {
        this.header = header;
    }

    @Override
    public boolean canRead(EvaluationContext context, Object target, String name) {
        return header.contains(name);
    }

    @Override
    public TypedValue read(EvaluationContext context, Object target, String name) {
        return new TypedValue(header.getFirstValue(name));
    }
}
