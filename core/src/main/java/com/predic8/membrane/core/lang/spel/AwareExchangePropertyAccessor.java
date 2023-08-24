package com.predic8.membrane.core.lang.spel;

import com.predic8.membrane.core.lang.spel.spelable.SPeLablePropertyAware;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;

public class AwareExchangePropertyAccessor implements PropertyAccessor {
    @Override
    public Class<?>[] getSpecificTargetClasses() {
        return new Class[] {
                SPeLablePropertyAware.class
        };
    }

    @Override
    public boolean canRead(EvaluationContext context, Object target, String name) throws AccessException {
        return ((SPeLablePropertyAware) target).canRead(context, target, name);
    }

    @Override
    public TypedValue read(EvaluationContext context, Object target, String name) throws AccessException {
        return ((SPeLablePropertyAware) target).read(context, target, name);
    }

    @Override
    public boolean canWrite(EvaluationContext context, Object target, String name) throws AccessException {
        throw new AccessException("Not implemented");
    }

    @Override
    public void write(EvaluationContext context, Object target, String name, Object newValue) throws AccessException {
        throw new AccessException("Not implemented");
    }
}
