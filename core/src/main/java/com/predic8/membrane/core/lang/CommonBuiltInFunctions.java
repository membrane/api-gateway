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
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.lang.spel.*;
import com.predic8.membrane.core.security.*;
import com.predic8.membrane.core.util.xml.*;
import com.predic8.membrane.core.util.xml.parser.*;
import org.slf4j.*;

import javax.xml.xpath.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.http.Header.*;
import static java.lang.System.getenv;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;
import static java.util.Objects.*;

/**
 * Place to share built-in functions between SpEL and Groovy.
 */
public class CommonBuiltInFunctions {

    private static final Logger log = LoggerFactory.getLogger(CommonBuiltInFunctions.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final XPathFactory xPathFactory = XPathFactory.newInstance();
    private static final XmlParser parser = HardenedXmlParser.getInstance();

    public static Object jsonPath(String jsonPath, Message msg) {
        try {
            return JsonPath.read(objectMapper.readValue(msg.getBodyAsStringDecoded(), Map.class), jsonPath);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String xpath(String xpath, Message message) {
        XPath xPath = xPathFactory.newXPath();

        // TODO: Leave the comment in till the XML namespace support is realized!
        // - When there is the new registry use it to obtain the XMLConfig.
        //        if (xmlConfig != null && xmlConfig.getNamespaces() != null) {
        //            xPath.setNamespaceContext(xmlConfig.getNamespaces().getNamespaceContext());
        //        }

        try {
            return xPath.evaluate(xpath, parser.parse(XMLUtil.getInputSource(message)), XPathConstants.STRING).toString();
        } catch (XPathExpressionException ignored) {
            return null;
        }
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

}
