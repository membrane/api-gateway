package com.predic8.membrane.annot.yaml.spel;

import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.*;
import org.springframework.expression.spel.support.ReflectiveMethodResolver;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.List;

public final class SpELContextFactory {

    private SpELContextFactory() {}

    public static StandardEvaluationContext newContext() {
        StandardEvaluationContext ctx = new StandardEvaluationContext(new SpELContext());

        ctx.setTypeLocator(typeName -> {
            throw new EvaluationException("Type references are not allowed in SpEL.");
        });

        ctx.setPropertyAccessors(List.of(new EnvPropertyAccessor()));

        ctx.setMethodResolvers(List.of(new RootOnlyMethodResolver(SpELContext.class)));

        ctx.setBeanResolver((context, beanName) -> {
            throw new EvaluationException("Bean references are not allowed in SpEL.");
        });

        return ctx;
    }

    static final class RootOnlyMethodResolver implements MethodResolver {
        private final Class<?> allowedRootType;
        private final ReflectiveMethodResolver delegate = new ReflectiveMethodResolver();

        RootOnlyMethodResolver(Class<?> allowedRootType) {
            this.allowedRootType = allowedRootType;
        }

        @Override
        public MethodExecutor resolve(@NotNull EvaluationContext context, @NotNull Object targetObject, @NotNull String name, @NotNull List<TypeDescriptor> argumentTypes) throws AccessException {
            if (!allowedRootType.isInstance(targetObject)) {
                throw new AccessException("Only root functions are allowed in SpEL.");
            }
            return delegate.resolve(context, targetObject, name, argumentTypes);
        }
    }
}
