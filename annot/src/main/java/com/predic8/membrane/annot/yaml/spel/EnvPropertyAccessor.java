package com.predic8.membrane.annot.yaml.spel;

import org.jetbrains.annotations.NotNull;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;

import static com.predic8.membrane.annot.yaml.spel.EnvPropertyAccessor.EnvVariables.INSTANCE;
import static java.lang.System.getenv;
import static java.util.Locale.ROOT;

public final class EnvPropertyAccessor implements PropertyAccessor {

    @Override
    public Class<?>[] getSpecificTargetClasses() {
        return new Class<?>[] { SpELContext.class, EnvVariables.class };
    }

    @Override
    public boolean canRead(@NotNull EvaluationContext context, Object target, @NotNull String name) {
        if (target instanceof SpELContext) return "env".equals(name);
        return target instanceof EnvVariables;
    }

    @Override
    public @NotNull TypedValue read(@NotNull EvaluationContext context, Object target, @NotNull String name) throws AccessException {
        if (target instanceof SpELContext) {
            return new TypedValue(INSTANCE);
        }
        if (target instanceof EnvVariables) {
            return new TypedValue(getenv(name.toUpperCase(ROOT)));
        }
        throw new AccessException("Unsupported target: " + target);
    }

    @Override
    public boolean canWrite(@NotNull EvaluationContext context, Object target, @NotNull String name) {
        return false;
    }

    @Override
    public void write(@NotNull EvaluationContext context, Object target, @NotNull String name, Object newValue) throws AccessException {
        throw new AccessException("Writing is not supported.");
    }

    public static final class EnvVariables {
        static final EnvVariables INSTANCE = new EnvVariables();
        private EnvVariables() {}
    }
}
