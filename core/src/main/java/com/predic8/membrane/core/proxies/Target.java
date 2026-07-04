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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.config.xml.XmlConfig;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.XMLSupport;
import com.predic8.membrane.core.lang.ExchangeExpression.InterceptorAdapter;
import com.predic8.membrane.core.lang.ExchangeExpression.Language;
import com.predic8.membrane.core.lang.TemplateExchangeExpression;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.text.SerializationFunction;
import com.predic8.membrane.core.util.text.SerializationUtil.Serialization;
import com.predic8.membrane.core.util.text.TerminalColors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;
import static com.predic8.membrane.core.util.TemplateUtil.containsTemplateMarker;
import static com.predic8.membrane.core.util.text.SerializationUtil.getSerialization;
import static com.predic8.membrane.core.util.text.TerminalColors.RESET;

/**
 * @description Defines the backend a proxy forwards messages to, given either as a <code>host</code>
 *              with a <code>port</code> or as a full <code>url</code>. The destination can be computed
 *              per request with inline <code>${expression}</code> templates; outbound TLS and an
 *              overriding HTTP method can also be configured here.
 * @yaml <pre><code>
 * target:
 *   url: https://api.predic8.de
 * </code></pre>
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
    private Serialization escaping = Serialization.URL;
    private SerializationFunction escapingFunction;

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
        escapingFunction = getSerialization(escaping);
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
     * @description Host name or IP address of the target. Ignored when <code>url</code> is set.
     * @example localhost
     */
    @MCAttribute
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * @description Port number of the target. Ignored when <code>url</code> is set.
     * @default 80, or 443 when the target uses TLS
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
     * @description Absolute URL of the target. When set, <code>host</code> and <code>port</code> are
     *              ignored. Supports inline <code>${&lt;expression&gt;}</code> templates. If the URL
     *              contains a path, that path replaces the request path; usually the request path
     *              should be kept, so give a URL without a path such as
     *              <code>https://api.predic8.de</code>.
     * @example https://api.predic8.de
     */
    @MCAttribute
    public void setUrl(String url) {
        this.url = url;
    }

    public SSLParser getSslParser() {
        return sslParser;
    }

    /**
     * @description Configures outbound TLS for the connection to the target (HTTPS).
     */
    @MCChildElement(allowForeign = true)
    public void setSslParser(SSLParser sslParser) {
        this.sslParser = sslParser;
    }

    public boolean isAdjustHostHeader() {
        return adjustHostHeader;
    }

    /**
     * @description Rewrites the outgoing <code>Host</code> header to the target host. Disable to
     *              forward the client's original <code>Host</code> header unchanged.
     * @default true
     * @example false
     */
    @MCAttribute
    public void setAdjustHostHeader(boolean adjustHostHeader) {
        this.adjustHostHeader = adjustHostHeader;
    }

    public String getMethod() {
        return method;
    }

    /**
     * @description Overrides the HTTP method used for the backend call, replacing the method of the
     *              incoming request. When not set, the original method is kept.
     * @example POST
     */
    @MCAttribute
    public void setMethod(String method) {
        this.method = method;
    }

    public Language getLanguage() {
        return language;
    }

    /**
     * @description Expression language used to evaluate inline <code>${...}</code> templates in the URL.
     * @default SpEL
     * @example groovy
     */
    @MCAttribute
    public void setLanguage(Language language) {
        this.language = language;
    }

    public Serialization getEscaping() {
        return escaping;
    }

    /**
     * @description How values computed from <code>${...}</code> templates are escaped before being
     *              inserted into the URL, to prevent injection. <code>URL</code> escapes a whole URL,
     *              <code>SEGMENT</code> a single path segment, <code>NONE</code> disables escaping.
     * @default URL
     * @example SEGMENT
     */
    @MCAttribute
    public void setEscaping(Serialization escaping) {
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
