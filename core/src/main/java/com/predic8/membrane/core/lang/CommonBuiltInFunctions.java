package com.predic8.membrane.core.lang;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.lang.spel.SpELExchangeEvaluationContext;
import com.predic8.membrane.core.security.*;

import java.util.*;
import java.util.function.Predicate;

import static com.predic8.membrane.core.exchange.Exchange.SECURITY_SCHEMES;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;

/**
 * Place to share built-in functions between SpEL and Groovy.
 *
 * TODO Move function implementations from com.predic8.membrane.core.lang.spel.functions.BuiltInFunctions to here.
 *
 */
public class CommonBuiltInFunctions {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Object jsonPath(String jsonPath, Message msg) {
        try {
            return JsonPath.read(objectMapper.readValue(msg.getBodyAsStringDecoded(), Map.class), jsonPath);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String user(Exchange exchange) {
        List<SecurityScheme> schemes = exchange.getProperty(SECURITY_SCHEMES, List.class );
        for (SecurityScheme scheme :schemes) {
            if (scheme instanceof BasicHttpSecurityScheme basic) {
                return basic.getUsername();
            }
        }
        return null;
    }

    public static List<String> scopes(Exchange exc) {
        return getSchemeScopes(all(), exc);
    }

    public static List<String> scopes(String securityScheme, Exchange exc) {
        return getSchemeScopes(name(securityScheme), exc);
    }

    public static boolean hasScope(String scope, Exchange exc) {
        return scopesContainsByPredicate(exc, it -> it.contains(scope));
    }

    public static boolean hasScope(Exchange exc) {
        return scopesContainsByPredicate(exc, it -> !it.isEmpty());
    }

    @SuppressWarnings({"SlowListContainsAll"})
    public static boolean hasScope(List<String> scopes, Exchange exc) {
        return scopesContainsByPredicate(exc, it -> it.containsAll(scopes));
    }

    public static boolean isXML(Exchange exc, Flow flow) {
        return exc.getMessage(flow).isXML();
    }

    public static boolean isJSON(Exchange exc, Flow flow) {
        return exc.getMessage(flow).isJSON();
    }

    public static String base64Encode(String s) {
        return java.util.Base64.getEncoder().encodeToString(s.getBytes(UTF_8));
    }

    private static List<String> getSchemeScopes(Predicate<SecurityScheme> predicate, Exchange exc) {
        return Optional.ofNullable(getSecuritySchemes(exc))
                .map(list -> list.stream()
                        .filter(predicate)
                        .map(SecurityScheme::getScopes)
                        .flatMap(Collection::stream)
                        .toList())
                .orElse(emptyList());
    }

    private static List<SecurityScheme> getSecuritySchemes(Exchange exc) {
        return exc.getProperty(SECURITY_SCHEMES, List.class);
    }

    private static Boolean scopesContainsByPredicate(Exchange exc, Predicate<List<String>> predicate) {
        return predicate.test(getSchemeScopes(all(), exc));
    }

    private static Predicate<SecurityScheme> name(String name) {
        return scheme -> scheme.getName().equalsIgnoreCase(name);
    }

    private static Predicate<SecurityScheme> all() {
        return ignored -> true;
    }

}
