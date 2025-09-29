/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.proxies.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * @description This interceptor adds the destination specified in the target
 * element to the list of destinations of the exchange object. It
 * must be placed into the transport to make Service Proxies Work
 * properly. It has to be placed after the ruleMatching
 * interceptor. The ruleMatching interceptor looks up a service
 * proxy for an incoming request and places it into the exchange
 * object. The dispatching interceptor needs the service proxy to
 * get information about the target.
 */
@MCElement(name = "dispatching")
public class DispatchingInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DispatchingInterceptor.class.getName());

    public DispatchingInterceptor() {
        name = "dispatching interceptor";
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (exc.getProxy() instanceof AbstractServiceProxy asp) {
            exc.getDestinations().clear();
            try {
                exc.getDestinations().add(getForwardingDestination(exc));
            } catch (URISyntaxException e) {
                ProblemDetails pd = user(false, "invalid-path")
                        .title("Invalid request path")
                        .detail(getMessageForURISyntaxException(exc, e))
                        .internal("path", exc.getRequest().getUri());
                if (e.getIndex() >= 0)
                    pd.internal("index", e.getIndex());
                pd.buildAndSetResponse(exc);
                return ABORT;
            } catch (Exception e) {
                internal(router.isProduction(), getDisplayName())
                        .detail("Could not get forwarding destination to dispatch request")
                        .exception(e)
                        .buildAndSetResponse(exc);
                return ABORT;
            }
            setSNIPropertyOnExchange(exc, asp);
            return CONTINUE;
        }
        exc.getDestinations().add(exc.getRequest().getUri());
        return CONTINUE;
    }

    private static @NotNull String getMessageForURISyntaxException(Exchange exc, URISyntaxException e) {
        var uri = exc.getRequestURI();
        if (e.getIndex() >= 0 && e.getIndex() < uri.length()) {
            return "The request path contains an invalid character '%s' at pos %d".formatted(uri.charAt(e.getIndex()), e.getIndex());
        }
        return "Invalid request URI";
    }

    private void setSNIPropertyOnExchange(Exchange exc, AbstractServiceProxy asp) {
        if (asp.getTargetSSL() == null)
            return;

        String sni = asp.getTargetSSL().getServerName();
        if (sni == null)
            return;

        exc.setProperty(SNI_SERVER_NAME, sni);
    }

    private String getForwardingDestination(Exchange exc) throws Exception {
        String urlResult = getAddressFromTargetElement(exc);
        log.debug("destination: {}", urlResult);
        return urlResult != null ? urlResult : exc.getRequest().getUri();
    }

    protected String getAddressFromTargetElement(Exchange exc) throws MalformedURLException, URISyntaxException {
        AbstractServiceProxy p = (AbstractServiceProxy) exc.getProxy();

        if (p.getTargetURL() != null) {
            String targetURL = p.getTarget().compileUrl(exc, REQUEST);
            if (targetURL.startsWith("http")) {
                String basePath = UriUtil.getPathFromURL(router.getUriFactory(), targetURL);
                if (basePath.isEmpty() || "/".equals(basePath)) {
                    URL base = new URL(targetURL);
                    // Resolve and normalize slashes consistently with the branch below.
                    return new URL(base, getUri(exc)).toString();
                }
            }
            return targetURL;
        }
        if (p.getTargetHost() != null) {
            return new URL(p.getTargetScheme(), p.getTargetHost(), p.getTargetPort(), getUri(exc)).toString();
        }

        // That's fine. Maybe it is a <soapProxy> without a target
        return null;
    }

    private String getUri(Exchange exc) throws URISyntaxException {
        if (exc.getRequest().isCONNECTRequest()) {
            return exc.getRequest().getUri();
        }
        return router.getUriFactory().create(exc.getRequest().getUri()).getPathWithQuery();
    }

    @Override
    public EnumSet<Flow> getAppliedFlow() {
        return REQUEST_FLOW;
    }
}