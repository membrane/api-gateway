/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.lang;

import com.fasterxml.jackson.databind.*;
import com.jayway.jsonpath.*;
import com.predic8.membrane.core.config.xml.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.security.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.util.xml.*;
import com.predic8.membrane.core.util.xml.parser.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;
import org.slf4j.Logger;

import javax.xml.namespace.*;
import javax.xml.xpath.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.http.Header.*;
import static java.lang.System.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;
import static java.util.Objects.*;
import static javax.xml.xpath.XPathConstants.*;

/**
 * Place to share built-in functions between SpEL and Groovy.
 */
public class CommonBuiltInFunctions {

    private static final Logger log = LoggerFactory.getLogger(CommonBuiltInFunctions.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final XmlParser parser = HardenedXmlParser.getInstance();

    public static Object jsonPath(String jsonPath, Message msg) {
        try {
            return JsonPath.read(objectMapper.readValue(msg.getBodyAsStringDecoded(), Map.class), jsonPath);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String toJSON(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            log.info("Failed to convert object to JSON", e);
            return null;
        }
    }

    /**
     * <p>
     * The message body is parsed into a DOM {@link org.w3c.dom.Document} and the
     * XPath expression is evaluated against that document as the root context.
     * </p>
     * <p>
     * This variant is intended for full-document XPath expressions such as
     * {@code //fruit}, {@code string(//name)}, or {@code count(//item)}.
     * </p>
     * <p>
     * Namespace support is currently not configured. The commented code below is
     * intentionally kept as a reminder to re-enable namespace handling once the
     * XML configuration can be obtained from the new registry.
     * </p>
     *
     * @param expression the XPath expression to evaluate
     * @param message    the {@link Message} containing the XML body used as XPath context
     * @return the string value of the XPath evaluation result, or {@code null}
     * if the expression is invalid or cannot be evaluated
     */
    public static Object xpath(String expression, Message message, XmlConfig cfg) {
        try {
            return XPathUtil.newXPath(cfg).evaluate(
                    expression,
                    parser.parse(XMLUtil.getInputSource(message)),
                    guessReturnType(expression)
            );
        } catch (XPathExpressionException ignored) {
            return null;
        }
    }

    /**
     * Evaluates an XPath expression against a given XPath context object.
     * <p>
     * The context object may be any type supported by JAXP XPath evaluation,
     * including:
     * </p>
     * <ul>
     *   <li>{@link org.w3c.dom.Document}</li>
     *   <li>{@link org.w3c.dom.Node}</li>
     *   <li>{@link org.w3c.dom.NodeList}</li>
     *   <li>{@link javax.xml.transform.Source}</li>
     * </ul>
     * <p>
     * This method enables relative XPath expressions such as {@code ./name}
     * when the context is a {@link org.w3c.dom.Node}, and absolute expressions
     * such as {@code //fruit} when the context is a document or node list.
     * </p>
     *
     * @param expression the XPath expression to evaluate
     * @param ctx        the XPath context object used as the evaluation root
     * @return the string value of the XPath evaluation result, or {@code null}
     * if the expression is invalid or cannot be evaluated
     */
    public static Object xpath(String expression, Object ctx, XmlConfig cfg) {
        try {
            return XPathUtil.newXPath(cfg).evaluate(expression, ctx, guessReturnType(expression));

        } catch (XPathExpressionException ignored) {
            return null;
        }
    }

    /**
     * Not perfect but for most applications good enough
     *
     * @param expr
     * @return
     */
    static @NotNull QName guessReturnType(String expr) {
        expr = expr.trim();
        return expr.startsWith("string(") || expr.startsWith("normalize-space(") ? STRING
                : expr.startsWith("count(") || expr.startsWith("number(") ? NUMBER
                : expr.startsWith("boolean(") ? BOOLEAN
                : expr.startsWith(".") && !expr.contains("//") ? NODE
                : NODESET;
    }

    public static String user(Exchange exchange) {
        List<SecurityScheme> schemes = exchange.getProperty(SECURITY_SCHEMES, List.class);
        for (SecurityScheme scheme : schemes) {
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

    public static String env(String name) {
        if (name == null || name.isBlank())
            return null;
        return getenv(name);
    }

    public static String urlEncode(String s) {
        if (s == null) return "";
        return URLEncoder.encode(s, UTF_8);
    }

    /**
     * Encodes the given string value as a URI-safe path segment.
     * This method performs percent-encoding according to RFC 3986, ensuring that the encoded string
     * is safe to use as a single path segment in URIs. Characters outside the unreserved set
     * {@code A-Z, a-z, 0-9, -, ., _, ~} are encoded as {@code %HH} sequences.
     *
     * @param segment the string value to encode
     * @return a percent-encoded string safe for use as a single URI path segment
     */
    public static String pathSeg(String segment) {
        return URLUtil.pathSeg(segment);
    }
}
