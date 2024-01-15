package com.predic8.membrane.core.lang.spel;

import org.springframework.core.convert.*;
import org.springframework.expression.*;

import java.util.*;

public class BuildinFunctionResolver implements MethodResolver {
    @Override
    public MethodExecutor resolve(EvaluationContext context, Object targetObject, String name, List<TypeDescriptor> argumentTypes) throws AccessException {
        System.out.println("context = " + context + ", targetObject = " + targetObject + ", name = " + name + ", argumentTypes = " + argumentTypes);
        return new BuildinMethodExecutor();
    }

    static class BuildinMethodExecutor implements MethodExecutor {
        @Override
        public TypedValue execute(EvaluationContext context, Object target, Object... arguments) throws AccessException {
            System.out.println("context = " + context + ", target = " + target + ", arguments = " + arguments);
            return new TypedValue(new String());
        }
    }
}

