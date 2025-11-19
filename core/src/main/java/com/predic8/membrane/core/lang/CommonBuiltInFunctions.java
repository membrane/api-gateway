package com.predic8.membrane.core.lang;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptorWithSession;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.security.BasicHttpSecurityScheme;
import com.predic8.membrane.core.security.SecurityScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import static com.predic8.membrane.core.exchange.Exchange.SECURITY_SCHEMES;
import static com.predic8.membrane.core.http.Header.AUTHORIZATION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

/**
 * Place to share built-in functions between SpEL and Groovy.
 */
public class CommonBuiltInFunctions {

    private static final Logger log = LoggerFactory.getLogger(CommonBuiltInFunctions.class);

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

    public static boolean isBearerAuthorization(Exchange exc) {
        return exc.getRequest().getHeader().contains(AUTHORIZATION)
                && exc.getRequest().getHeader().getFirstValue(AUTHORIZATION).startsWith("Bearer");
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

    public static boolean weight(double weightInPercent) {
        return Math.max(0, Math.min(1, weightInPercent / 100.0)) > ThreadLocalRandom.current().nextDouble();
    }

    public static boolean isLoggedIn(String beanName, Exchange exc) {
        try {
            return ((AbstractInterceptorWithSession) requireNonNull(exc.getHandler().getTransport().getRouter().getBeanFactory()).getBean(beanName))
                    .getSessionManager().getSession(exc).isVerified();
        } catch (Exception e) {
            log.info("Failed to resolve bean with name '{}'", beanName);
            return false;
        }
    }

    public static long getDefaultSessionLifetime(String beanName, Exchange exc) {
        try {
            return ((AbstractInterceptorWithSession) requireNonNull(exc.getHandler().getTransport().getRouter().getBeanFactory()).getBean(beanName))
                    .getSessionManager().getExpiresAfterSeconds();
        } catch (Exception e) {
            log.info("Failed to resolve bean with name '{}'", beanName);
            return -1;
        }
    }

}
