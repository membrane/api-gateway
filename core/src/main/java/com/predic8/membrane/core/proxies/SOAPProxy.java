/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import com.google.common.collect.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.rewrite.*;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.*;
import com.predic8.membrane.core.interceptor.schemavalidation.*;
import com.predic8.membrane.core.interceptor.server.*;
import com.predic8.membrane.core.interceptor.soap.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.util.wsdl.parser.*;
import org.apache.commons.lang3.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.net.*;
import java.util.regex.*;

import static com.predic8.membrane.core.interceptor.InterceptorUtil.*;

/**
 * @description <p>
 * A SOAP proxy automatically configures itself using a WSDL description. It reads the WSDL to extract:
 * </p>
 * - The &lt;soap:address/&gt; for target, port, and path.
 * <p>
 * The proxy sits in front of a SOAP Web Service, masking it while providing the same interface to clients
 * as the target server. The proxy serves the WSDL to gateway clients, with the WSDL address pointing to the proxy
 * instead of the backend. This ensures that client requests using the WSDL are routed through the API Gateway.
 * </p>
 * Additionally, the SOAP proxy:
 * - Can validate requests against the WSDL
 * - Provides a simple service explorer
 * @explanation If the WSDL specified by the <i>wsdl</i> attribute is unavailable at startup, the &lt;soapProxy&gt;
 * becomes inactive. Reinitialization can be triggered via the admin console or automatically by the
 * {@link Router}, which periodically attempts to restore the proxy.
 * @topic 1. Proxies and Flow
 */
@MCElement(name = "soapProxy", topLevel = true, component = false)
public class SOAPProxy extends AbstractServiceProxy {

    // configuration attributes
    protected String wsdl;
    protected String portName;
    protected HttpClientConfiguration httpClientConfig;
    protected String serviceName;

    // set during initialization
    protected ResolverMap resolverMap;

    public SOAPProxy() {
        this.key = new ServiceProxyKey(80);
    }

    @Override
    public void init() {
        resolverMap = router.getResolverMap();
        if (httpClientConfig != null) {
            resolverMap = resolverMap.clone();
            resolverMap.addSchemaResolver(new HTTPSchemaResolver(router.getHttpClientFactory().createClient(httpClientConfig)));
        }
        configureFromWSDL();
        super.init(); // Must be called last! Otherwise, SSL will not be configured!

        for (var interceptor : interceptors) {
            if (interceptor instanceof WSDLPublisherInterceptor wpi) {
                wpi.setSoapProxy(this);
            } else if (interceptor instanceof ValidatorInterceptor vi) {
                vi.setSoapProxy(this);
            }
        }
    }

    protected void configureFromWSDL() {
        var defs = parseWSDL();
        var service = getService(defs);
        setProxyName(service, defs);
        var location = getLocation(service);

        // Signal to the later processing that the outgoing connection is using TLS
        if (location.startsWith("https")) {
            target.setSslParser(new SSLParser());
        }

        prepareRouting(location);

        // Add interceptors (in reverse order) cause each one calls List.addFirst.
        // This is needed because there might be already a validator interceptor that must go last
        addWebServiceExplorer(); // Will be last to validator
        addWSDLPublisherInterceptor(); // Will be before WebServiceExplorer
        var wsdlInterceptor = addAndGetWSDLInterceptor(); // WSDLInterceptor will be first
        wsdlInterceptor.setPathRewriterOnWSDLInterceptor(key.getPath());
    }

    private Service getService(Definitions defs) {
        if (serviceName != null)
            return defs.getService(serviceName).orElseThrow(
                    () -> new ConfigurationException("No service with name '%s' found in WSDL %s".formatted(serviceName, wsdl))
            );
        return defs.getServices().getFirst();
    }

    private @NotNull Definitions parseWSDL() {
        try {
             return Definitions.parse(resolverMap, wsdl);
        } catch (Exception e) {
            throw new ConfigurationException("""
                    Cannot parse WSDL
                    
                    API: %s
                    WSDL location: %s.
                    Error. %s
                    """.formatted( name, wsdl, e.getMessage()));
        }
    }

    private void prepareRouting(String location) {
        try {
            var url = new URL(location);
            setTarget(url); // Set target URL from WSDL location
            if (key.getPath() == null) { // If the config does not contain a path, use the path from the WSDL(address/@location) for the proxy key
                key.setUsePathPattern(true);
                key.setPathRegExp(false);
                key.setPath(url.getPath());
            } else {
                configureRewritingOfPath(getTargetPath(url));
            }

            ((ServiceProxyKey) key).setMethod("*"); // GET and POST are used for SOAP
        } catch (MalformedURLException e) {
            throw new ConfigurationException("WSDL endpoint location '" + location + "' is not an URL.");
        }
    }

    private void configureRewritingOfPath(String targetPath) {
        if (targetPath == null)
            return;

        var ri = new RewriteInterceptor();
        ri.setMappings(Lists.newArrayList(new Mapping("^" + Pattern.quote(key.getPath()), Matcher.quoteReplacement(targetPath), "rewrite")));
        interceptors.addFirst(ri);
    }

    private static @NotNull String getTargetPath(URL url) {
        if (url.getQuery() != null) {
            return url.getPath() + "?" + url.getQuery();
        }
        return url.getPath();
    }

    private @NotNull String getLocation(com.predic8.membrane.core.util.wsdl.parser.Service service) {
        var location = service.getPorts().getFirst().getAddress().getLocation();

        if (location == null)
            throw new ConfigurationException("In the WSDL %s, there is no @location defined on the port.".formatted(wsdl));
        return location;
    }

    private void setTarget(URL url) {
        if (wsdl.startsWith("internal:")) {
            try {
                target.setUrl(UriUtil.getPathFromURL(router.getConfiguration().getUriFactory(), wsdl)); // TODO
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            return;
        }
        target.setHost(url.getHost());
        target.setPort(getPort(url));
    }

    private int getPort(URL url) {
        return url.getPort() != -1 ? url.getPort() : url.getDefaultPort();
    }

    private void setProxyName(Service service, Definitions definitions) {
        if (StringUtils.isEmpty(name))
            name = StringUtils.isEmpty(service.getName()) ? definitions.getName() : service.getName();
    }

    private WSDLInterceptor addAndGetWSDLInterceptor() {
        return moveToFirstPosition(interceptors, WSDLInterceptor.class, WSDLInterceptor::new).orElseThrow();
    }

    private void addWebServiceExplorer() {
        var sui = new WebServiceExplorerInterceptor();
        sui.setWsdl(wsdl);
        sui.setPortName(portName);
        interceptors.addFirst(sui);
    }

    private void addWSDLPublisherInterceptor() {
        if (hasWSDLPublisherInterceptor())
            return;

        var wp = new WSDLPublisherInterceptor();
        wp.setWsdl(wsdl);
        wp.init(router);
        interceptors.addFirst(wp);
    }

    private boolean hasWSDLPublisherInterceptor() {
        return getFirstInterceptorOfType(WSDLPublisherInterceptor.class).isPresent();
    }

    public String getWsdl() {
        return wsdl;
    }

    /**
     * @description The WSDL of the SOAP service.
     * @example <a href="http://predic8.de/my.wsdl">http://predic8.de/my.wsdl</a> <i>or</i> file:my.wsdl
     */
    @Required
    @MCAttribute
    public void setWsdl(String wsdl) {
        this.wsdl = wsdl;
    }

    public String getPortName() {
        return portName;
    }

    @MCAttribute
    public void setPortName(String portName) {
        this.portName = portName;
    }

    public HttpClientConfiguration getWsdlHttpClientConfig() {
        return httpClientConfig;
    }

    @MCChildElement
    public void setWsdlHttpClientConfig(HttpClientConfiguration httpClientConfig) {
        this.httpClientConfig = httpClientConfig;
    }

    public String getServiceName() {
        return serviceName;
    }

    @MCAttribute
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}