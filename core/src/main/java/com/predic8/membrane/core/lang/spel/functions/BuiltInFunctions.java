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

import com.predic8.membrane.core.lang.spel.ExchangeEvaluationContext;

import java.util.List;
import java.util.function.Predicate;

import static com.predic8.membrane.core.interceptor.apikey.ApiKeysInterceptor.SCOPES;
import static java.util.Optional.ofNullable;

/**
 * This class's public methods are automatically registered in the SpEL context by the BuildInFunctionResolver.
 * These methods must adhere to the following constraints:
 * 1. They must be static.
 * 2. They should operate in a non-destructive manner on their parameters.
 * 3. An ExchangeEvaluationContext object must be included as the last parameter in every method, even if it's the sole parameter.
 * The ExchangeEvaluationContext provides a specialized Membrane SpEL context, enabling access to the Exchange and other relevant data.
 */
public class BuiltInFunctions {

    public static boolean hasScope(String scope, ExchangeEvaluationContext ctx) {
        return scopesContainsByPredicate(ctx, it -> it.contains(scope));
    }

    public static boolean hasScopes(ExchangeEvaluationContext ctx) {
        return scopesContainsByPredicate(ctx, it -> !it.isEmpty());
    }

    @SuppressWarnings({"SlowListContainsAll"})
    public static boolean hasScopes(List<String> scopes, ExchangeEvaluationContext ctx) {
        return scopesContainsByPredicate(ctx, it -> it.containsAll(scopes));
    }

    @SuppressWarnings("unchecked")
    private static Boolean scopesContainsByPredicate(ExchangeEvaluationContext ctx, Predicate<List<String>> predicate) {
        return ofNullable((List<String>) ctx.getExchange().getProperties().get(SCOPES))
                .map(predicate::test)
                .orElse(false);
    }
}
