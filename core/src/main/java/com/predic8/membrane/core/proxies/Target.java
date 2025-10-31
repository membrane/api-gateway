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

package com.predic8.membrane.core.proxies;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.Interceptor.*;
import com.predic8.membrane.core.lang.*;

import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;

/**
 * @description <p>
 * The destination where the service proxy will send messages to.
 * Use the target element if you want to send the messages to a target.
 * Supports dynamic destinations through expressions.
 * </p>
 */
@MCElement(name = "target", topLevel = true)
public class Target {
    private String host;
    private int port = -1;
    private String method;
    private String url;
    private boolean adjustHostHeader = true;
    private ExchangeExpression.Language language = SPEL;
    private ExchangeExpression exchangeExpression;

    private SSLParser sslParser;

    public void init(Router router) {
        if (url != null) exchangeExpression = TemplateExchangeExpression.newInstance(router, language, url);
    }

    public String compileUrl(Exchange exc, Flow flow) {
        /**
         * Will always evaluate on every call. This is fine as SpEL is fast enough and performs its own optimizations.
         * 1.000.000 calls ~10ms
         */
        if (exchangeExpression != null) {
            return exchangeExpression.evaluate(exc, flow, String.class);
        }
        return url;
    }

    public Target() {
    }

    public Target(String host) {
        setHost(host);
    }

    public Target(String host, int port) {
        setHost(host);
        setPort(port);
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

    public ExchangeExpression getExchangeExpression() {
        return exchangeExpression;
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
}

