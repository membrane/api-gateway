package com.predic8.membrane.annot.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.predic8.membrane.annot.Grammar;
import com.predic8.membrane.annot.beanregistry.BeanRegistryAware;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static com.networknt.schema.SpecificationVersion.DRAFT_2020_12;
import static org.springframework.util.ReflectionUtils.doWithMethods;

public final class YamlParsingUtils {

    private YamlParsingUtils() {
    }

    private record SchemaCacheKey(String schemaLocation, ClassLoader classLoader) {
    }

    private static final ConcurrentHashMap<SchemaCacheKey, Schema> SCHEMA_CACHE = new ConcurrentHashMap<>();

    static void validate(Grammar grammar, JsonNode input) throws YamlSchemaValidationException {
        Schema schema = SCHEMA_CACHE.computeIfAbsent(new SchemaCacheKey(grammar.getSchemaLocation(), Thread.currentThread().getContextClassLoader()), k -> {
            Schema s = SchemaRegistry.withDefaultDialect(DRAFT_2020_12, b -> {
                    })
                    .getSchema(SchemaLocation.of(k.schemaLocation()));
            s.initializeValidators();
            return s;
        });

        List<Error> errors = schema.validate(input);
        if (!errors.isEmpty())
            throw new YamlSchemaValidationException("Invalid YAML.", errors);
    }

    /**
     * Calls the @PostConstruct method on the bean and returns it. If there are @PreDestroy methods, they will be
     * registered within the registry.
     */
    static <T> T handlePostConstructAndPreDestroy(ParsingContext<?> ctx, T bean) {
        if (bean instanceof BeanRegistryAware beanRegistryAware) {
            beanRegistryAware.setRegistry(ctx.registry());
        }
        doWithMethods(bean.getClass(), method -> {
            if (method.isAnnotationPresent(PostConstruct.class)) invokeNoArg(bean, method);
            if (method.isAnnotationPresent(PreDestroy.class)) {
                method.setAccessible(true);
                ctx.registry().addPreDestroyCallback(bean, method);
            }
        });
        return bean;
    }

    private static void invokeNoArg(Object bean, Method m) {
        try {
            m.setAccessible(true);
            m.invoke(bean);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getTargetException());
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Searches for a single setter method in the given class that is annotated with the specified annotation.
     */
    static Method findSingleSetterOrNullForAnnotation(Class<?> clazz, Class<? extends java.lang.annotation.Annotation> annotation) {
        List<Method> setters = new ArrayList<>();
        doWithMethods(clazz, m -> {
            if (m.isAnnotationPresent(annotation) && m.getParameterCount() == 1) {
                setters.add(m);
            }
        });

        if (setters.isEmpty()) return null;
        return setters.getFirst();
    }

}
