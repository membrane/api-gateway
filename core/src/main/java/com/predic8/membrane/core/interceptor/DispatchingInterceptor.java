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
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.proxies.*;
import org.slf4j.*;

import java.net.*;

import static com.predic8.membrane.core.exchange.Exchange.*;
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
        name = "Dispatching Interceptor";
        setFlow(Flow.Set.REQUEST);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {

        if (exc.getProxy() instanceof AbstractServiceProxy asp) {
            exc.getDestinations().clear();
            exc.getDestinations().add(getForwardingDestination( exc));
            setSNIPropertyOnExchange(exc, asp);
            return CONTINUE;
        }

        exc.getDestinations().add(exc.getRequest().getUri());

        return CONTINUE;
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
        String urlResult = getAddressFromTargetElement( exc);
        log.debug("destination: {}", urlResult);
        return urlResult != null ? urlResult : exc.getRequest().getUri();
    }

    protected String getAddressFromTargetElement(Exchange exc) throws MalformedURLException, URISyntaxException {
        AbstractServiceProxy p = (AbstractServiceProxy) exc.getProxy();

        if (p.getTargetURL() != null) {
            if (p.getTargetURL().startsWith("http") && !UriUtil.getPathFromURL(router.getUriFactory(), p.getTargetURL()).contains("/")) {
                return p.getTargetURL() + exc.getRequestURI();
            }
            return p.getTargetURL();
        }
        if (p.getTargetHost() != null) {
            return new URL(p.getTargetScheme(), p.getTargetHost(), p.getTargetPort(), exc.getRequest().getUri()).toString();
        }

        // That's fine. Maybe it is a <soapProxy> without a target
        return null;
    }
}