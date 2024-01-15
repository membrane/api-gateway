package com.predic8.membrane.core.lang.spel.functions;

import com.predic8.membrane.core.lang.spel.ExchangeEvaluationContext;

import java.util.List;

import static java.util.Optional.ofNullable;

public class BuiltInFunctions {

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
