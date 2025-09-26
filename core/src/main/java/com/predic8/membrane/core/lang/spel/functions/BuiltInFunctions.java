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

import com.fasterxml.jackson.databind.*;
import com.jayway.jsonpath.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.spel.*;
import com.predic8.membrane.core.security.*;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.http.Header.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;
import static java.util.Objects.*;

/**
 * This class's public methods are automatically registered in the SpEL context by the BuiltInFunctionResolver.
 * These methods must adhere to the following constraints:
 * 1. They must be static.
 * 2. They should operate in a non-destructive manner on their parameters.
 * 3. An ExchangeEvaluationContext object must be included as the last parameter in every method, even if it's the sole parameter.
 * The ExchangeEvaluationContext provides a specialized Membrane SpEL context, enabling access to the Exchange and other relevant data.
 */
public class BuiltInFunctions {
    private static final Logger log = LoggerFactory.getLogger(BuiltInFunctions.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Object jsonPath(String jsonPath, SpELExchangeEvaluationContext ctx) {
        try {
            return JsonPath.read(objectMapper.readValue(ctx.getMessage().getBodyAsStringDecoded(), Map.class), jsonPath);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean weight(double weightInPercent, SpELExchangeEvaluationContext ignored) {
        return Math.max(0, Math.min(1, weightInPercent / 100.0)) > ThreadLocalRandom.current().nextDouble();
    }

    public static boolean isLoggedIn(String beanName, SpELExchangeEvaluationContext ctx) {
        try {
            return ((AbstractInterceptorWithSession) requireNonNull(ctx.getExchange().getHandler().getTransport().getRouter().getBeanFactory()).getBean(beanName))
                    .getSessionManager().getSession(ctx.getExchange()).isVerified();
        } catch (Exception e) {
            log.info("Failed to resolve bean with name '{}'", beanName);
            return false;
        }
    }

    public static long getDefaultSessionLifetime(String beanName, SpELExchangeEvaluationContext ctx) {
        try {
            return ((AbstractInterceptorWithSession) requireNonNull(ctx.getExchange().getHandler().getTransport().getRouter().getBeanFactory()).getBean(beanName))
                    .getSessionManager().getExpiresAfterSeconds();
        } catch (Exception e) {
            log.info("Failed to resolve bean with name '{}'", beanName);
            return -1;
        }
    }

    public static boolean isBearerAuthorization(SpELExchangeEvaluationContext ctx) {
        return ctx.getExchange().getRequest().getHeader().contains(AUTHORIZATION)
                && ctx.getExchange().getRequest().getHeader().getFirstValue(AUTHORIZATION).startsWith("Bearer");
    }

    public static List<String> scopes(SpELExchangeEvaluationContext ctx) {
        return getSchemeScopes(all(), ctx);
    }

    /**
     * @param securityScheme Name of the scheme like http, apiKey, oauth2. See: SecurityScheme.getName()
     * @param ctx
     * @return
     */
    public static List<String> scopes(String securityScheme, SpELExchangeEvaluationContext ctx) {
        return getSchemeScopes(name(securityScheme), ctx);
    }

    public static boolean hasScope(String scope, SpELExchangeEvaluationContext ctx) {
        return scopesContainsByPredicate(ctx, it -> it.contains(scope));
    }

    public static boolean hasScope(SpELExchangeEvaluationContext ctx) {
        return scopesContainsByPredicate(ctx, it -> !it.isEmpty());
    }

    @SuppressWarnings({"SlowListContainsAll"})
    public static boolean hasScope(List<String> scopes, SpELExchangeEvaluationContext ctx) {
        return scopesContainsByPredicate(ctx, it -> it.containsAll(scopes));
    }

    private static Boolean scopesContainsByPredicate(SpELExchangeEvaluationContext ctx, Predicate<List<String>> predicate) {
        return predicate.test(getSchemeScopes(all(), ctx));
    }

    @SuppressWarnings("unchecked")
    private static List<String> getSchemeScopes(Predicate<SecurityScheme> predicate, SpELExchangeEvaluationContext ctx) {
        return Optional.ofNullable((List<SecurityScheme>) ctx.getExchange().getProperty(SECURITY_SCHEMES))
                .map(list -> list.stream()
                        .filter(predicate)
                        .map(SecurityScheme::getScopes)
                        .flatMap(Collection::stream)
                        .toList())
                .orElse(emptyList());
    }

    private static Predicate<SecurityScheme> all() {
        return ignored -> true;
    }

    private static Predicate<SecurityScheme> name(String name) {
        return scheme -> scheme.getName().equalsIgnoreCase(name);
    }

    public static boolean isXML(SpELExchangeEvaluationContext ctx) {
        return ctx.getMessage().isXML();
    }

    public static boolean isJSON(SpELExchangeEvaluationContext ctx) {
        return ctx.getMessage().isJSON();
    }

    public static String base64Encode(String s, SpELExchangeEvaluationContext ctx) {
        return java.util.Base64.getEncoder().encodeToString(s.getBytes(UTF_8));
    }
}
