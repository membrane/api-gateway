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

import com.predic8.membrane.core.lang.CommonBuiltInFunctions;
import com.predic8.membrane.core.lang.spel.SpELExchangeEvaluationContext;

import java.util.List;

/**
 * This class's public methods are automatically registered in the SpEL context by the BuiltInFunctionResolver.
 * These methods must adhere to the following constraints:
 * 1. They must be static.
 * 2. They should operate in a non-destructive manner on their parameters.
 * 3. An ExchangeEvaluationContext object must be included as the last parameter in every method, even if it's the sole parameter.
 * The ExchangeEvaluationContext provides a specialized Membrane SpEL context, enabling access to the Exchange and other relevant data.
 */
public class BuiltInFunctions {

    public static Object jsonPath(String jsonPath, SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.jsonPath(jsonPath, ctx.getMessage());
    }

    public static boolean weight(double weightInPercent, SpELExchangeEvaluationContext ignored) {
        return CommonBuiltInFunctions.weight(weightInPercent);
    }

    public static boolean isLoggedIn(String beanName, SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.isLoggedIn(beanName, ctx.getExchange());
    }

    public static long getDefaultSessionLifetime(String beanName, SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.getDefaultSessionLifetime(beanName, ctx.getExchange());
    }

    public static boolean isBearerAuthorization(SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.isBearerAuthorization(ctx.getExchange());
    }

    public static List<String> scopes(SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.scopes(ctx.getExchange());
    }

    public static List<String> scopes(String securityScheme, SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.scopes(securityScheme, ctx.getExchange());
    }

    public static boolean hasScope(String scope, SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.hasScope(scope, ctx.getExchange());
    }

    public static boolean hasScope(SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.hasScope(ctx.getExchange());
    }

    public static boolean hasScope(List<String> scopes, SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.hasScope(scopes, ctx.getExchange());
    }

    public static String user(SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.user(ctx.getExchange());
    }

    public static boolean isXML(SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.isXML(ctx.getExchange(), ctx.getFlow());
    }

    public static boolean isJSON(SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.isJSON(ctx.getExchange(), ctx.getFlow());
    }

    public static String base64Encode(String s, SpELExchangeEvaluationContext ignored) {
        return CommonBuiltInFunctions.base64Encode(s);
    }
}
