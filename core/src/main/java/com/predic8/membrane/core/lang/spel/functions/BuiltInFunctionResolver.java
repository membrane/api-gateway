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

import com.predic8.membrane.core.router.*;
import org.springframework.core.convert.*;
import org.springframework.expression.*;

import java.lang.reflect.*;
import java.util.*;

public class BuiltInFunctionResolver implements MethodResolver {

    private final ReflectiveMethodHandler functions;

    public BuiltInFunctionResolver(Router router) {
        super();
        functions = new ReflectiveMethodHandler(new SpELBuiltInFunctions(router));
    }

    /**
     * @return Returns MethodExecutor or null in case no method can be found by this resolver
     */
    @Override
    public MethodExecutor resolve(EvaluationContext context, Object targetObject, String name, List<TypeDescriptor> argumentTypes) throws AccessException {
        try {
            Method m = functions.retrieveFunction(name, argumentTypes);
            return (ctx, target, arguments) -> {
                try {
                    return functions.invokeFunction(m, ctx, arguments);
                } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
                    throw new BuiltInFunctionException("Cannot invoke built-in function " + name, name, e);
                }
            };
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}

