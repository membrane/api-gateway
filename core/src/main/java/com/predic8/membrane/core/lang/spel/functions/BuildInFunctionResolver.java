/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
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

