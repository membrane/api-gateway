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
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.rewrite.*;
import com.predic8.membrane.core.interceptor.server.*;
import com.predic8.membrane.core.interceptor.soap.*;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIInterceptor;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.util.*;
import com.predic8.wsdl.*;
import org.apache.commons.lang3.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import javax.xml.namespace.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.Constants.*;

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
@MCElement(name = "soapProxy")
public class SOAPProxy extends AbstractServiceProxy {

    private static final Logger log = LoggerFactory.getLogger(SOAPProxy.class.getName());
    private static final Pattern relativePathPattern = Pattern.compile("^./[^/?]*\\?");

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
            HTTPSchemaResolver httpSR = new HTTPSchemaResolver(router.getHttpClientFactory());
            httpSR.setHttpClientConfig(httpClientConfig);
            resolverMap = resolverMap.clone();
            resolverMap.addSchemaResolver(httpSR);
        }
        configureFromWSDL();
        super.init(); // Must be called last! Otherwise, SSL will not be configured!

        for(Interceptor interceptor: interceptors) {
            if(interceptor instanceof WSDLPublisherInterceptor wpi) {
                wpi.setSoapProxy(this);
            }
        }
    }

    protected void configureFromWSDL() {

        Definitions definitions = parseWSDLOnly();
        Service service = getService(definitions);
        setProxyName(service, definitions);

        String location = getLocation(service);

        // Signal to the later processing that the outgoing connection is using TLS
        if (location.startsWith("https")) {
            target.setSslParser(new SSLParser());
        }

        prepareRouting(location);

        // add interceptors (in reverse order) to position 0.
        addWebServiceExplorer();
        addWSDLPublisherInterceptor();
        addWSDLInterceptor();
        renameMe();

    }

    private Definitions parseWSDLOnly() {
        try {
            return getWsdlParser().parse(getWsdlParserContext());
        } catch (Exception e) {
            String msg = "Could not parse WSDL from %s.".formatted(getWsdlParserContext().getInput());
            log.error("{}: {}", msg, e.getMessage());
            throw new ConfigurationException(msg, e);
        }
    }

    private void prepareRouting(String location) {
        try {
            URL url = new URL(location);
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

        RewriteInterceptor ri = new RewriteInterceptor();
        ri.setMappings(Lists.newArrayList(new RewriteInterceptor.Mapping("^" + Pattern.quote(key.getPath()), Matcher.quoteReplacement(targetPath), "rewrite")));
        interceptors.addFirst(ri);
        automaticallyAddedInterceptorCount++;
    }

    private static @NotNull String getTargetPath(URL url) {
        if (url.getQuery() != null) {
            return url.getPath() + "?" + url.getQuery();
        }
        return url.getPath();
    }

    private @NotNull String getLocation(Service service) {
        String location = getPort(service).getAddress().getLocation();

        if (location == null)
            throw new ConfigurationException("In the WSDL %s, there is no @location defined on the port.".formatted(wsdl));
        return location;
    }

    private @NotNull Port getPort(Service service) {
        return selectPort(service.getPorts(), portName);
    }

    private @NotNull WSDLParserContext getWsdlParserContext() {
        WSDLParserContext ctx = new WSDLParserContext();
        ctx.setInput(ResolverMap.combine(router.getBaseLocation(), wsdl));
        return ctx;
    }

    private @NotNull WSDLParser getWsdlParser() {
        WSDLParser wsdlParser = new WSDLParser();
        wsdlParser.setResourceResolver(resolverMap.toExternalResolver().toExternalResolver());
        return wsdlParser;
    }

    private Service getService(Definitions definitions) {
        List<Service> services = definitions.getServices();
        if (services.size() == 1)
            return services.getFirst();
        if (serviceName == null) {
            throw new SOAPProxyMultipleServicesException(this, getServiceNames(services));
        }
        return getServiceByName(services, serviceName);
    }

    private Service getServiceByName(List<Service> services, String serviceName) {
        return services.stream()
                .filter(s -> s.getName().equals(serviceName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No service with name %s found in the WSDL. Available services are: %s".formatted(serviceName, getServiceNames(services))));
    }

    private static @NotNull List<String> getServiceNames(List<Service> services) {
        return services.stream()
                .map(WSDLElement::getName)
                .toList();
    }

    private void setTarget(URL url) {
        if (wsdl.startsWith("internal:")) {
            try {
                target.setUrl(UriUtil.getPathFromURL(router.getUriFactory(), wsdl)); // TODO
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

    public static Port selectPort(List<Port> ports, String portName) {
        if (portName != null) {
            for (Port port : ports)
                if (portName.equals(port.getName()))
                    return port;
            throw new IllegalArgumentException("No port with name '" + portName + "' found.");
        }
        return getPort(ports);
    }

    private static Port getPort(List<Port> ports) {
        Port port = getPortByNamespace(ports, WSDL_SOAP11_NS);
        if (port == null)
            port = getPortByNamespace(ports, WSDL_SOAP12_NS);
        if (port == null)
            throw new IllegalArgumentException("No SOAP/1.1 or SOAP/1.2 ports found in WSDL.");
        return port;
    }

    private static Port getPortByNamespace(List<Port> ports, String namespace) {
        for (Port port : ports) {
            try {
                if (port.getBinding() == null)
                    continue;
                if (port.getBinding().getBinding() == null)
                    continue;
                AbstractBinding binding = port.getBinding().getBinding();
                if (!"http://schemas.xmlsoap.org/soap/http".equals(binding.getProperty("transport")))
                    continue;
                if (!namespace.equals(((QName) binding.getElementName()).getNamespaceURI()))
                    continue;
                return port;
            } catch (Exception e) {
                log.warn("Error inspecting WSDL port binding.", e);
            }
        }
        return null;
    }

    private int automaticallyAddedInterceptorCount;

    private void addWSDLInterceptor() {
        if (getFirstInterceptorOfType(WSDLInterceptor.class).isEmpty()) {
            WSDLInterceptor wsdlInterceptor = new WSDLInterceptor();
            interceptors.addFirst(wsdlInterceptor);
            automaticallyAddedInterceptorCount++;
        }
    }

    private void renameMe() {
        if (key.getPath() == null)
            return;

        Optional<WSDLInterceptor> wsdlInterceptor = getFirstInterceptorOfType(WSDLInterceptor.class);

        if (wsdlInterceptor.isEmpty()) {
            log.warn("No wsdl interceptor set.");
            return;
        }

        final String keyPath = key.getPath();
        final String name = getReplacementName(keyPath);
        wsdlInterceptor.get().setPathRewriter(path2 -> {
            try {
                if (path2.contains("://")) {
                    return new URL(new URL(path2), keyPath).toString();
                } else {
                    Matcher m = relativePathPattern.matcher(path2);
                    return m.replaceAll("./" + name + "?");
                }
            } catch (MalformedURLException e) {
                log.error("Cannot parse URL {}", path2);
            }
            return path2;
        });
    }

    private @NotNull String getReplacementName(String keyPath) {
        try {
            return URLUtil.getName(router.getUriFactory(), keyPath);
        } catch (URISyntaxException e) {
            log.error("Error parsing URL {}", keyPath, e);
            throw new RuntimeException("Check!");
        }
    }

    private void addWebServiceExplorer() {
        WebServiceExplorerInterceptor sui = new WebServiceExplorerInterceptor();
        sui.setWsdl(wsdl);
        sui.setPortName(portName);
        interceptors.addFirst(sui);
        automaticallyAddedInterceptorCount++;
    }

    private void addWSDLPublisherInterceptor() {
        if (hasWSDLPublisherInterceptor())
            return;

        WSDLPublisherInterceptor wp = new WSDLPublisherInterceptor();
        wp.setWsdl(wsdl);
        wp.init(router);
        interceptors.addFirst(wp);
        automaticallyAddedInterceptorCount++;
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

    @MCAttribute
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