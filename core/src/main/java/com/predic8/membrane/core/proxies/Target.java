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
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.router.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;

/**
 * @description <p>
 * The destination where the service proxy will send messages to.
 * Use the target element if you want to send the messages to a target.
 * Supports dynamic destinations through expressions.
 * </p>
 */
@MCElement(name = "target", component = false)
public class Target implements XMLSupport {
    private String host;
    private int port = -1;
    private String method;
    protected String url;
    private boolean adjustHostHeader = true;
    private ExchangeExpression.Language language = SPEL;

    private SSLParser sslParser;

    protected XmlConfig xmlConfig;

    public Target() {
    }

    public Target(String host) {
        setHost(host);
    }

    public Target(String host, int port) {
        setHost(host);
        setPort(port);
    }

    public void applyModifications(Exchange exc, AbstractServiceProxy asp, Router router) {
        computeDestinationExpressions(exc, asp, router);

        // Changing the method must be the last step cause it can empty the body!
        if (asp.getTarget().getMethod() != null) {
            exc.getRequest().changeMethod(asp.getTarget().getMethod());
        }
    }

    private static void computeDestinationExpressions(Exchange exc, AbstractServiceProxy asp, Router router) {
        var target = asp.getTarget();

        var dests = exc.getDestinations().stream().map(url -> {
            var exp = TemplateExchangeExpression.newInstance(new ExchangeExpression.InterceptorAdapter(router, target.getXmlConfig()), target.getLanguage(), url, router);
            return exp.evaluate(exc, REQUEST, String.class);
        }).toList();

        exc.setDestinations(dests);
    }

    public String getHost() {
        return host;
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

    public ExchangeExpression.Language getLanguage() {
        return language;
    }

    /**
     * @description the language of the inline expressions
     * @default SpEL
     * @example SpEL, groovy, jsonpath, xpath
     */
    @MCAttribute
    public void setLanguage(ExchangeExpression.Language language) {
        this.language = language;
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
