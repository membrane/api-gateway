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
package com.predic8.membrane.core.rules;

import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import com.google.common.collect.Lists;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.WSDLInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor;
import com.predic8.membrane.core.interceptor.server.WSDLPublisherInterceptor;
import com.predic8.membrane.core.interceptor.soap.WebServiceExplorerInterceptor;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;
import com.predic8.membrane.core.resolver.HTTPSchemaResolver;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.util.URLUtil;
import com.predic8.membrane.core.ws.relocator.Relocator.PathRewriter;
import com.predic8.wsdl.AbstractBinding;
import com.predic8.wsdl.Definitions;
import com.predic8.wsdl.Port;
import com.predic8.wsdl.Service;
import com.predic8.wsdl.WSDLParser;
import com.predic8.wsdl.WSDLParserContext;

import javax.xml.namespace.QName;

/**
 * @description <p>
 *              A SOAP proxy can be deployed on front of a SOAP Web Service. It conceals the server and offers the same
 *              interface as the target server to its clients.
 *              </p>
 * @explanation If the WSDL referenced by the <i>wsdl</i> attribute is not available at startup, the &lt;soapProxy&gt;
 *              will become inactive. Through the admin console, reinitialization attempts can be triggered and, by
 *              default, the {@link Router} also periodically triggers such attempts.
 * @topic 2. Proxies
 */
@MCElement(name="soapProxy")
public class SOAPProxy extends AbstractServiceProxy {
	private static final Logger log = LoggerFactory.getLogger(SOAPProxy.class.getName());
	private static final Pattern relativePathPattern = Pattern.compile("^./[^/?]*\\?");

	// configuration attributes
	protected String wsdl;
	protected String portName;
	protected String targetPath;
	protected HttpClientConfiguration httpClientConfig;

	// set during initialization
	protected ResolverMap resolverMap;

	public SOAPProxy() {
		this.key = new ServiceProxyKey(80);
	}

	@Override
	protected AbstractProxy getNewInstance() {
		return new SOAPProxy();
	}

	/**
	 * @return error or null for success
	 */
	private void parseWSDL() throws Exception {
		WSDLParserContext ctx = new WSDLParserContext();
		ctx.setInput(ResolverMap.combine(router.getBaseLocation(), wsdl));
		try {
			WSDLParser wsdlParser = new WSDLParser();
			wsdlParser.setResourceResolver(resolverMap.toExternalResolver().toExternalResolver());

			Definitions definitions = wsdlParser.parse(ctx);

			List<Service> services = definitions.getServices();
			if (services.size() != 1)
				throw new IllegalArgumentException("There are " + services.size() + " services defined in the WSDL, but exactly 1 is required for soapProxy.");
			Service service = services.get(0);

			if (StringUtils.isEmpty(name))
				name = StringUtils.isEmpty(service.getName()) ? definitions.getName() : service.getName();

				List<Port> ports = service.getPorts();
				Port port = selectPort(ports, portName);

				String location = port.getAddress().getLocation();
				if (location == null)
					throw new IllegalArgumentException("In the WSDL, there is no @location defined on the port.");
				try {
					URL url = new URL(location);
					if (wsdl.startsWith("service:")) {
						target.setUrl(wsdl.substring(0, wsdl.indexOf('/')));
					} else {
						target.setHost(url.getHost());
						if (url.getPort() != -1)
							target.setPort(url.getPort());
						else
							target.setPort(url.getDefaultPort());
					}
					if (key.getPath() == null) {
						key.setUsePathPattern(true);
						key.setPathRegExp(false);
						key.setPath(url.getPath());
					} else {
						String query = "";
						if(url.getQuery() != null){
							query = "?" + url.getQuery();
						}
						targetPath = url.getPath()+ query;
					}
					if(location.startsWith("https")){
						SSLParser sslOutboundParser = new SSLParser();
						target.setSslParser(sslOutboundParser);
					}
					((ServiceProxyKey)key).setMethod("*");
				} catch (MalformedURLException e) {
					throw new IllegalArgumentException("WSDL endpoint location '"+location+"' is not an URL.", e);
				}
				return;
		} catch (Exception e) {
			Throwable f = e;
			while (f.getCause() != null && ! (f instanceof ResourceRetrievalException))
				f = f.getCause();
			if (f instanceof ResourceRetrievalException) {
				ResourceRetrievalException rre = (ResourceRetrievalException) f;
				if (rre.getStatus() >= 400)
					throw rre;
				Throwable cause = rre.getCause();
				if (cause != null) {
					if (cause instanceof UnknownHostException)
						throw (UnknownHostException) cause;
					else if (cause instanceof ConnectException)
						throw (ConnectException) cause;
				}
			}
			throw new IllegalArgumentException("Could not download the WSDL '" + wsdl + "'.", e);
		}
	}

	public static Port selectPort(List<Port> ports, String portName) {
		if (portName != null) {
			for (Port port : ports)
				if (portName.equals(port.getName()))
					return port;
			throw new IllegalArgumentException("No port with name '" + portName + "' found.");
		}
		Port port = getPortByNamespace(ports, Constants.WSDL_SOAP11_NS);
		if (port == null)
			port = getPortByNamespace(ports, Constants.WSDL_SOAP12_NS);
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
				if (!namespace.equals(((QName)binding.getElementName()).getNamespaceURI()))
					continue;
				return port;
			} catch (Exception e) {
				log.warn("Error inspecting WSDL port binding.", e);
			}
		}
		return null;
	}

	private int automaticallyAddedInterceptorCount;

	public void configure() throws Exception {

		parseWSDL();
		// remove previously added interceptors
		for(; automaticallyAddedInterceptorCount > 0; automaticallyAddedInterceptorCount--)
			interceptors.remove(0);


		// add interceptors (in reverse order) to position 0.

		WebServiceExplorerInterceptor sui = new WebServiceExplorerInterceptor();
		sui.setWsdl(wsdl);
		sui.setPortName(portName);
		interceptors.add(0, sui);
		automaticallyAddedInterceptorCount++;

		boolean hasPublisher = getInterceptorOfType(WSDLPublisherInterceptor.class) != null;
		if (!hasPublisher) {
			WSDLPublisherInterceptor wp = new WSDLPublisherInterceptor();
			wp.setWsdl(wsdl);
			interceptors.add(0, wp);
			automaticallyAddedInterceptorCount++;
		}

		WSDLInterceptor wsdlInterceptor = getInterceptorOfType(WSDLInterceptor.class);
		boolean hasRewriter = wsdlInterceptor != null;
		if (!hasRewriter) {
			wsdlInterceptor = new WSDLInterceptor();
			interceptors.add(0, wsdlInterceptor);
			automaticallyAddedInterceptorCount++;
		}
		if (key.getPath() != null) {
			final String keyPath = key.getPath();
			final String name = URLUtil.getName(router.getUriFactory(), keyPath);
			wsdlInterceptor.setPathRewriter(new PathRewriter() {
				@Override
				public String rewrite(String path2) {

					try {
						if (path2.contains("://")) {
							path2 = new URL(new URL(path2), keyPath).toString();
						} else {
							Matcher m = relativePathPattern.matcher(path2);
							path2 = m.replaceAll("./" + name + "?");
						}
					} catch (MalformedURLException e) {
					}
					return path2;
				}
			});
		}

		if (hasRewriter && !hasPublisher)
			log.warn("A <soapProxy> contains a <wsdlRewriter>, but no <wsdlPublisher>. Probably you want to insert a <wsdlPublisher> just after the <wsdlRewriter>. (Or, if this is a valid use case, please notify us at " + Constants.PRODUCT_CONTACT_EMAIL + ".)");

		if (targetPath != null) {
			RewriteInterceptor ri = new RewriteInterceptor();
			ri.setMappings(Lists.newArrayList(new RewriteInterceptor.Mapping("^" + Pattern.quote(key.getPath()), Matcher.quoteReplacement(targetPath), "rewrite")));
			interceptors.add(0, ri);
			automaticallyAddedInterceptorCount++;
		}
	}

	@Override
	public void init() throws Exception {
		super.init();

		if (wsdl == null)
			return;

		resolverMap = router.getResolverMap();
		if (httpClientConfig != null) {
			HTTPSchemaResolver httpSR = new HTTPSchemaResolver(router.getHttpClientFactory());
			httpSR.setHttpClientConfig(httpClientConfig);
			resolverMap = resolverMap.clone();
			resolverMap.addSchemaResolver(httpSR);
		}

		configure();
	}

	@SuppressWarnings("unchecked")
	private <T extends Interceptor> T getInterceptorOfType(Class<T> class1) {
		for (Interceptor i : interceptors)
			if (class1.isInstance(i))
				return (T) i;
		return null;
	}

	public String getWsdl() {
		return wsdl;
	}

	/**
	 * @description The WSDL of the SOAP service.
	 * @example http://predic8.de/my.wsdl <i>or</i> file:my.wsdl
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

}
