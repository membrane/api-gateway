/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.annot.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.predic8.membrane.annot.Grammar;
import com.predic8.membrane.annot.beanregistry.BeanRegistryAware;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jetbrains.annotations.NotNull;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static com.networknt.schema.SpecificationVersion.DRAFT_2020_12;
import static com.predic8.membrane.annot.yaml.spel.SpELContextFactory.newContext;
import static com.predic8.membrane.annot.yaml.spel.SpELEngine.eval;
import static org.springframework.util.ReflectionUtils.doWithMethods;

public final class YamlParsingUtils {

    private static final StandardEvaluationContext SPEL_CTX = newContext();

    private YamlParsingUtils() {
    }

    private record SchemaCacheKey(String schemaLocation, ClassLoader classLoader) {
    }

    private static final ConcurrentHashMap<SchemaCacheKey, Schema> SCHEMA_CACHE = new ConcurrentHashMap<>();
    private static final ObjectMapper SCALAR_MAPPER = new ObjectMapper();

    static void validate(Grammar grammar, JsonNode input) throws YamlSchemaValidationException {
        List<Error> errors = loadSchema(grammar).validate(input);
        if (!errors.isEmpty())
            throw new YamlSchemaValidationException("Invalid YAML.", errors);
    }

    private static @NotNull Schema loadSchema(Grammar grammar) {
        // Schema cache: prevents duplicate loads for multi-doc specs (---).
        return SCHEMA_CACHE.computeIfAbsent(new SchemaCacheKey(grammar.getSchemaLocation(), Thread.currentThread().getContextClassLoader()), k -> {
            Schema s = SchemaRegistry.withDefaultDialect(DRAFT_2020_12, b -> {
                    })
                    .getSchema(SchemaLocation.of(k.schemaLocation()));
            s.initializeValidators();
            return s;
        });
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


    static Object resolveSpelValue(String expression, Class<?> targetType, JsonNode node) {
        final Object value;
        try {
            value = eval(expression, SPEL_CTX);
        } catch (RuntimeException e) {
            throw new ConfigurationParsingException("Invalid SpEL expression: %s".formatted(e.getMessage()));
        }

        if (value == null) return null;
        if (targetType == String.class) return String.valueOf(value);

        return targetType.isInstance(value) ? value : SCALAR_MAPPER.convertValue(value, targetType);
    }

}
