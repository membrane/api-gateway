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

import com.google.common.collect.Lists;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.interceptor.WSDLInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.Mapping;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor;
import com.predic8.membrane.core.interceptor.server.WSDLPublisherInterceptor;
import com.predic8.membrane.core.interceptor.soap.WebServiceExplorerInterceptor;
import com.predic8.membrane.core.openapi.util.UriUtil;
import com.predic8.membrane.core.resolver.HTTPSchemaResolver;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import com.predic8.membrane.core.util.wsdl.parser.Service;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.interceptor.InterceptorUtil.moveToFirstPosition;

/**
 * @description The <code>soapProxy</code> auto-configures itself from a WSDL:
 * it reads the <code>soap:address</code> to derive the backend host, port, and path, rewrites the WSDL
 * endpoint to point to the gateway, and serves the rewritten WSDL at <code>?wsdl</code>. A built-in
 * service explorer is available at the same address. When a <code>path</code> is configured, request paths
 * are automatically rewritten between the gateway path and the backend path.
 * See tutorials/soap/20-SOAPProxy.yaml.
 * @explanation If the WSDL is unavailable at startup, the proxy becomes inactive. Reinitialization can be
 * triggered via the admin console or automatically by the router, which periodically retries.
 * @topic 1. Proxies and Flow
 * @yaml <pre><code>
 * # Minimal: auto-configure from WSDL
 * soapProxy:
 *   port: 2000
 *   wsdl: https://www.predic8.de/city-service?wsdl
 *
 * ---
 *
 * # Full: path rewriting, WSDL validation, custom host/port in rewritten WSDL,
 * # explicit service selection, and a dedicated HTTP client for WSDL retrieval
 * soapProxy:
 *   port: 2000
 *   host: api.example.com
 *   wsdl: https://www.predic8.de/city-service?wsdl
 *   serviceName: CityService
 *   portName: CityServiceSoap
 *   path:
 *     uri: /city-service
 *   wsdlHttpClientConfig:
 *     connection:
 *       timeout: 5000
 *   flow:
 *     - wsdlRewriter:
 *         host: api.example.com
 *         protocol: https
 *         port: 443
 *     - validator: {}
 * </code></pre>
 */
@MCElement(name = "soapProxy", topLevel = true, component = false)
public class SOAPProxy extends AbstractServiceProxy {

    // configuration attributes
    protected String wsdl;
    protected String resolvedWsdl;
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
        if (defs.getServices().isEmpty())
            throw new ConfigurationException("No service element found in WSDL %s".formatted(wsdl));
        return defs.getServices().getFirst();
    }

    private @NotNull Definitions parseWSDL() {
        try {
            resolvedWsdl = resolveWsdlLocation();
            return Definitions.parse(resolverMap, resolvedWsdl);
        } catch (ResourceRetrievalException e) {
            try {
                resolvedWsdl = wsdl;
                return Definitions.parse(resolverMap, resolvedWsdl);
            } catch (Exception fallbackException) {
                throw createWsdlConfigurationException(fallbackException);
            }
        } catch (Exception e) {
            throw createWsdlConfigurationException(e);
        }
    }

    private ConfigurationException createWsdlConfigurationException(Exception e) {
        return new ConfigurationException("""
                Cannot parse WSDL
                
                API: %s
                WSDL location: %s.
                Error. %s
                """.formatted(name, wsdl, e.getMessage()));
    }

    private String resolveWsdlLocation() {
        return ResolverMap.combine(router.getConfiguration().getUriFactory(), getBeanBaseLocation(), wsdl);
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
        sui.setWsdl(getResolvedWsdl());
        sui.setPortName(portName);
        interceptors.addFirst(sui);
    }

    private void addWSDLPublisherInterceptor() {
        if (hasWSDLPublisherInterceptor())
            return;

        var wp = new WSDLPublisherInterceptor();
        wp.setWsdl(getResolvedWsdl());
        wp.init(router);
        interceptors.addFirst(wp);
    }

    private boolean hasWSDLPublisherInterceptor() {
        return getFirstInterceptorOfType(WSDLPublisherInterceptor.class).isPresent();
    }

    public String getWsdl() {
        return wsdl;
    }

    public String getResolvedWsdl() {
        return resolvedWsdl != null ? resolvedWsdl : wsdl;
    }

    /**
     * @description URL or file path of the WSDL document. Both HTTP/HTTPS URLs and <code>file:</code> paths are supported.
     * @example https://www.predic8.de/city-service?wsdl
     */
    @Required
    @MCAttribute
    public void setWsdl(String wsdl) {
        this.wsdl = wsdl;
        resolvedWsdl = null;
    }

    public String getPortName() {
        return portName;
    }

    /**
     * @description Name of the WSDL port. When omitted, the explorer shows the first port defined in the WSDL.
     */
    @MCAttribute
    public void setPortName(String portName) {
        this.portName = portName;
    }

    public HttpClientConfiguration getWsdlHttpClientConfig() {
        return httpClientConfig;
    }

    /**
     * @description HTTP client settings used when fetching the WSDL document. Use this to configure
     * timeouts, a proxy, or TLS settings for the WSDL retrieval connection.
     */
    @MCChildElement
    public void setWsdlHttpClientConfig(HttpClientConfiguration httpClientConfig) {
        this.httpClientConfig = httpClientConfig;
    }

    public String getServiceName() {
        return serviceName;
    }

    /**
     * @description Name of the WSDL service to use, if the WSDL defines more than one. Optional;
     * if omitted, the first service defined in the WSDL is used.
     */
    @MCAttribute
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
