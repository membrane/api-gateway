/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.proxies;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.config.xml.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.util.text.*;
import com.predic8.membrane.core.util.uri.EscapingUtil.*;
import org.slf4j.*;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static com.predic8.membrane.core.util.TemplateUtil.*;
import static com.predic8.membrane.core.util.text.TerminalColors.*;
import static com.predic8.membrane.core.util.uri.EscapingUtil.getEscapingFunction;

/**
 * @description <p>
 * The destination where the service proxy will send messages to.
 * Use the target element if you want to send the messages to a target.
 * Supports dynamic destinations through expressions.
 * </p>
 */
@MCElement(name = "target", component = false)
public class Target implements XMLSupport {

    private static final Logger log = LoggerFactory.getLogger(Target.class);

    private String host;
    private int port = -1;
    private String method;

    protected String url;
    private Language language = SPEL;

    /**
     * Escaping strategy for URL placeholders.
     */
    private Escaping escaping = Escaping.URL;
    private Function<String, String> escapingFunction;

    /**
     * If url contains template marker ${}, if not expression evaluation is skipped
     */
    private boolean urlIsTemplate = false;

    private boolean adjustHostHeader = true;

    private SSLParser sslParser;
    protected XmlConfig xmlConfig;

    private InterceptorAdapter adapter;

    public Target() {
    }

    public Target(String host) {
        setHost(host);
    }

    public Target(String host, int port) {
        setHost(host);
        setPort(port);
    }

    public void init(Router router) {
        // URL Template evaluation is only activated when there are template markers ${ in the URL
        if (!containsTemplateMarker(url))
            return;

        adapter = new InterceptorAdapter(router, xmlConfig);

        if (router.getConfiguration().getUriFactory().isAllowIllegalCharacters()) {
            log.warn("{}Url templates are disabled for security.{} Disable configuration/uriFactory/allowIllegalCharacters to enable them. Illegal characters in templates may lead to injection attacks.", TerminalColors.BRIGHT_RED(), RESET());
            throw new ConfigurationException("""
                    URL Templating and Illegal URL Characters
                    
                    Url templating expressions and enablement of illegal characters in URLs are mutually exclusive. Either disable
                    illegal characters in the configuration (configuration/uriFactory/allowIllegalCharacters) or remove the
                    templating expression %s from the target URL.
                    """.formatted(url));
        }

        // If there is no template marker ${ than do not try to evaluate url as expression
        if(containsTemplateMarker(url)) {
            urlIsTemplate = true;
        }
        escapingFunction = getEscapingFunction(escaping);
    }

    public void applyModifications(Exchange exc, Router router) {
        exc.setDestinations(computeDestinationExpressions(exc, router));

        // Changing the method must be the last step cause it can empty the body!
        if (method != null && !method.isEmpty()) {
            exc.getRequest().changeMethod(method);
        }
    }

    private List<String> computeDestinationExpressions(Exchange exc, Router router) {
        return exc.getDestinations().stream().map(url -> evaluateTemplate(exc, router, url, adapter))
                .collect(Collectors.toList()); // Collectors.toList() generates mutable List .toList() => immutable
    }

    private String evaluateTemplate(Exchange exc, Router router, String url, InterceptorAdapter adapter) {
        // Only evaluate if the target url contains a template marker ${}
        if (!urlIsTemplate)
            return url;

        // Without caching 1_000_000 => 37s with ConcurrentHashMap as Cache => 34s
        // Cache is probably not worth the effort and complexity
        return TemplateExchangeExpression.newInstance(adapter,
                language,
                url,
                router,
                escapingFunction).evaluate(exc, REQUEST, String.class);
    }

    public String getHost() {
        return host;
    }

    public boolean isUrlIsTemplate() {
        return urlIsTemplate;
    }

    /**
     * @description Host address of the target.
     * @example localhost, 192.168.1.1
     */
    @MCAttribute
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * @description Port number of the target.
     * @default 80
     * @example 8080
     */
    @MCAttribute
    public void setPort(int port) {
        this.port = port;
    }

    public String getUrl() {
        return url;
    }

    /**
     * @description Absolute URL of the target. If this is set, <i>host</i> and <i>port</i> will be ignored.
     * Supports inline expressions through <code>${&lt;expression&gt;}</code> elements.
     * @example <a href="http://membrane-soa.org">http://membrane-soa.org</a>
     */
    @MCAttribute
    public void setUrl(String url) {
        this.url = url;
    }

    public SSLParser getSslParser() {
        return sslParser;
    }

    /**
     * @description Configures outbound SSL (HTTPS).
     */
    @MCChildElement(allowForeign = true)
    public void setSslParser(SSLParser sslParser) {
        this.sslParser = sslParser;
    }

    public boolean isAdjustHostHeader() {
        return adjustHostHeader;
    }

    @MCAttribute
    public void setAdjustHostHeader(boolean adjustHostHeader) {
        this.adjustHostHeader = adjustHostHeader;
    }

    public String getMethod() {
        return method;
    }

    /**
     * @param method
     * @description The method that should be used to make the call to the backend.
     * Overwrites the original method.
     */
    @MCAttribute
    public void setMethod(String method) {
        this.method = method;
    }

    public Language getLanguage() {
        return language;
    }

    /**
     * @description the language of the inline expressions
     * @default SpEL
     * @example SpEL, groovy, jsonpath, xpath
     */
    @MCAttribute
    public void setLanguage(Language language) {
        this.language = language;
    }

    public Escaping getEscaping() {
        return escaping;
    }

    /**
     * @param escaping NONE, URL, SEGMENT
     * @description When url contains placeholders ${}, the computed values should be escaped
     * to prevent injection attacks.
     * @default URL
     */
    @MCAttribute
    public void setEscaping(Escaping escaping) {
        this.escaping = escaping;
    }

    /**
     * XML Configuration e.g. declaration of XML namespaces for XPath expressions, ...
     *
     * @param xmlConfig
     */
    @Override
    @MCChildElement(allowForeign = true, order = 10)
    public void setXmlConfig(XmlConfig xmlConfig) {
        this.xmlConfig = xmlConfig;
    }

    @Override
    public XmlConfig getXmlConfig() {
        return xmlConfig;
    }
}
