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

import com.predic8.membrane.core.config.xml.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.lang.spel.*;
import com.predic8.membrane.core.router.*;

import java.lang.reflect.*;
import java.util.*;

import static java.lang.reflect.Modifier.*;

/**
 * This class's public methods are automatically registered in the SpEL context by the BuiltInFunctionResolver.
 * These methods must adhere to the following constraints:
 * 1. They must be static.
 * 2. They should operate in a non-destructive manner on their parameters.
 * 3. An ExchangeEvaluationContext object must be included as the last parameter in every method, even if it's the sole parameter.
 * The ExchangeEvaluationContext provides a specialized Membrane SpEL context, enabling access to the Exchange and other relevant data.
 */
@SuppressWarnings("unused")
public class SpELBuiltInFunctions {

    private final Router router;
    private XmlConfig xmlConfig;

    public SpELBuiltInFunctions(Router router) {
        this.router = router;
        if (router != null && router.getRegistry() != null) {
            this.xmlConfig = router.getRegistry().getBean(XmlConfig.class).orElse(null);
        }
    }

    public Object jsonPath(String jsonPath, SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.jsonPath(jsonPath, ctx.getMessage());
    }

    public Object toJSON(Object obj, SpELExchangeEvaluationContext ignored) {
        return CommonBuiltInFunctions.toJSON(obj);
    }

    public Object xpath(String expression, SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.xpath(expression, ctx.getMessage(), xmlConfig);
    }

    public Object xpath(String expression, Object context, SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.xpath(expression,  context, xmlConfig);
    }

    public boolean weight(double weightInPercent, SpELExchangeEvaluationContext ignored) {
        return CommonBuiltInFunctions.weight(weightInPercent);
    }

    public boolean isLoggedIn(String beanName, SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.isLoggedIn(beanName, ctx.getExchange());
    }

    public long getDefaultSessionLifetime(String beanName, SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.getDefaultSessionLifetime(beanName, ctx.getExchange());
    }

    public boolean isBearerAuthorization(SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.isBearerAuthorization(ctx.getExchange());
    }

    public List<String> scopes(SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.scopes(ctx.getExchange());
    }

    public List<String> scopes(String securityScheme, SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.scopes(securityScheme, ctx.getExchange());
    }

    public boolean hasScope(String scope, SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.hasScope(scope, ctx.getExchange());
    }

    public boolean hasScope(SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.hasScope(ctx.getExchange());
    }

    public boolean hasScope(List<String> scopes, SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.hasScope(scopes, ctx.getExchange());
    }

    public String user(SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.user(ctx.getExchange());
    }

    public boolean isXML(SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.isXML(ctx.getExchange(), ctx.getFlow());
    }

    public boolean isJSON(SpELExchangeEvaluationContext ctx) {
        return CommonBuiltInFunctions.isJSON(ctx.getExchange(), ctx.getFlow());
    }

    public String base64Encode(String s, SpELExchangeEvaluationContext ignored) {
        return CommonBuiltInFunctions.base64Encode(s);
    }

    public String env(String s, SpELExchangeEvaluationContext ignored) {
        return CommonBuiltInFunctions.env(s);
    }

    public static List<String> getBuiltInFunctionNames() {
        return Arrays.stream(SpELBuiltInFunctions.class.getDeclaredMethods())
                .filter(m -> isPublic(m.getModifiers()))
                .filter(SpELBuiltInFunctions::lastParamIsSpELExchangeEvaluationContext)
                .map(Method::getName)
                .distinct()
                .sorted()
                .toList();
    }

    private static boolean lastParamIsSpELExchangeEvaluationContext(Method m) {
        Class<?>[] params = m.getParameterTypes();
        return params.length > 0 && params[params.length - 1] == SpELExchangeEvaluationContext.class;
    }
}
