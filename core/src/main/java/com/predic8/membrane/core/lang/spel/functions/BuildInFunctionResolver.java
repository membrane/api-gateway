package com.predic8.membrane.core.lang.spel.functions;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.MethodExecutor;
import org.springframework.expression.MethodResolver;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class BuildInFunctionResolver implements MethodResolver {

    private final ReflectiveMethodHandler functions;

    public BuildInFunctionResolver() {
        super();
        functions = new ReflectiveMethodHandler(BuiltInFunctions.class);
    }

    @Override
    public MethodExecutor resolve(EvaluationContext context, Object targetObject, String name, List<TypeDescriptor> argumentTypes) throws AccessException {
       return (ctx, target, arguments) -> {
           try {
               return functions.invokeFunction(ctx, name, argumentTypes, arguments);
           } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
               throw new RuntimeException(e);
           }
       };
    }
}

