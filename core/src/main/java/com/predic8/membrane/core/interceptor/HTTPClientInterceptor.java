/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.METHOD_GET;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * @description The <i>httpClient</i> sends the request of an exchange to a Web
 * Server using the HTTP protocol. Usually it will be globally used
 * inside the transport. However, it is also possible to use it
 * inside a proxy to give the proxy an individual configuration for
 * its outgoing HTTP connection that is different from the global
 * configuration in the transport.
 */
@MCElement(name = "httpClient")
public class HTTPClientInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HTTPClientInterceptor.class.getName());

    private static final String PROXIES_HINT = " Maybe the target is only reachable over an HTTP proxy server. Please check proxy settings in conf/proxies.xml.";

    private boolean failOverOn5XX;
    private boolean adjustHostHeader = true;
    private HttpClientConfiguration httpClientConfig;

    private HttpClient hc;

    public HTTPClientInterceptor() {
        name = "http client";
        setFlow(REQUEST_FLOW);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        try {
            exc.blockRequestIfNeeded();
        } catch (TerminateException e) {
            log.error("Could not block request.", e);
        }

        changeMethod(exc);

        try {
            hc.call(exc, adjustHostHeader, failOverOn5XX);
            return RETURN;
        } catch (ConnectException e) {
            String msg = "Target %s is not reachable.".formatted(getDestination(exc));
            log.warn(msg + PROXIES_HINT);
            ProblemDetails.gateway(router.isProduction(), getDisplayName())
                    .statusCode(502)
                    .detail(msg)
                    .buildAndSetResponse(exc);
            return ABORT;
        } catch (SocketTimeoutException e) {
            // Details are logged further down in the HTTPClient
            internal(router.isProduction(), getDisplayName())
                    .detail("Target %s is not reachable.".formatted(exc.getDestinations()))
                    .buildAndSetResponse(exc);
            return ABORT;
        } catch (UnknownHostException e) {
            String msg = "Target host %s of API %s is unknown. DNS was unable to resolve host name.".formatted(URLUtil.getHost(getDestination(exc)), exc.getProxy().getName());
            log.warn(msg + PROXIES_HINT);
            ProblemDetails.gateway(router.isProduction(), getDisplayName())
                    .statusCode(502)
                    .detail(msg)
                    .buildAndSetResponse(exc);
            return ABORT;
        } catch (MalformedURLException e) {
            log.error("", e);
            internal(router.isProduction(), getDisplayName())
                    .exception(e)
                    .internal("proxy", exc.getProxy().getName())
                    .buildAndSetResponse(exc);
            return ABORT;
        } catch (Exception e) {
            log.error("", e);
            internal(router.isProduction(), getDisplayName())
                    .exception(e)
                    .internal("proxy", exc.getProxy().getName())
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    /**
     * Makes it possible to change the method by specifying <target method="POST"/>
     *
     * @param exc
     */
    private static void changeMethod(Exchange exc) {
        if (!(exc.getProxy() instanceof AbstractServiceProxy asp) || asp.getTarget() == null)
            return;

        String newMethod = asp.getTarget().getMethod();
        if (newMethod == null || newMethod.equalsIgnoreCase(exc.getRequest().getMethod()))
            return;

        log.debug("Changing method from {} to {}", exc.getRequest().getMethod(), newMethod);
        exc.getRequest().setMethod(newMethod);

        if (newMethod.equalsIgnoreCase(METHOD_GET)) {
            handleBodyContentWhenChangingToGET(exc);
        }
    }

    private static void handleBodyContentWhenChangingToGET(Exchange exc) {
        Request req = exc.getRequest();
        try {
            req.readBody();
        } catch (IOException ignored) {
        }
        req.setBody(new EmptyBody());
        req.getHeader().removeFields(CONTENT_LENGTH);
        req.getHeader().removeFields(CONTENT_TYPE);
        req.getHeader().removeFields(CONTENT_ENCODING);
    }

    private String getDestination(Exchange exc) {
        return exc.getDestinations().getFirst();
    }

    @Override
    public void init() {
        super.init();
        hc = router.getHttpClientFactory().createClient(httpClientConfig);
        hc.setStreamPumpStats(getRouter().getStatistics().getStreamPumpStats());
    }


    public boolean isFailOverOn5XX() {
        return failOverOn5XX;
    }

    /**
     * @description Whether to retry again (possibly the next node, when load
     * balancing is active) after a HTTP status code
     * 500&lt;=<i>x</i>&lt;600 was received.
     * @default false
     */
    @MCAttribute
    public void setFailOverOn5XX(boolean failOverOn5XX) {
        this.failOverOn5XX = failOverOn5XX;
    }

    public boolean isAdjustHostHeader() {
        return adjustHostHeader;
    }

    /**
     * @description Whether the HTTP "Host" header should be set before the response will be forwarded to its destination.
     * @explanation Set this to <i>false</i>, if the incoming HTTP "Host" header should not be modified.
     * @default true
     */
    @MCAttribute
    public void setAdjustHostHeader(boolean adjustHostHeader) {
        this.adjustHostHeader = adjustHostHeader;
    }

    public HttpClientConfiguration getHttpClientConfig() {
        return httpClientConfig;
    }

    @MCChildElement
    public void setHttpClientConfig(HttpClientConfiguration httpClientConfig) {
        this.httpClientConfig = httpClientConfig;
    }
}
