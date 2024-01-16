package com.predic8.membrane.core.lang.spel.functions;

import com.predic8.membrane.core.lang.spel.ExchangeEvaluationContext;

import java.util.List;

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

    @SuppressWarnings("unchecked")
    public static boolean hasScope(String scope, ExchangeEvaluationContext ctx) {
        return ofNullable((List<String>) ctx.getExchange().getProperties().get("scopes"))
                .map(scopesList -> scopesList.contains(scope))
                .orElse(false);
    }

    @SuppressWarnings("unchecked")
    public static boolean hasScopes(ExchangeEvaluationContext ctx) {
        return ofNullable((List<String>) ctx.getExchange().getProperties().get("scopes"))
                .map(scopes -> !scopes.isEmpty())
                .orElse(false);
    }

    @SuppressWarnings({"SlowListContainsAll", "unchecked"})
    public static boolean hasScopes(List<String> scopes, ExchangeEvaluationContext ctx) {
        return ofNullable((List<String>) ctx.getExchange().getProperties().get("scopes"))
                .map(scopesList -> scopesList.containsAll(scopes))
                .orElse(false);
    }
}
