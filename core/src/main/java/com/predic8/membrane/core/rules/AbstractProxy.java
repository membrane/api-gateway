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
package com.predic8.membrane.core.rules;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.FixedStreamReader;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.AbstractConfigElement;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.config.ConfigurationException;
import com.predic8.membrane.core.config.ElementName;
import com.predic8.membrane.core.config.LocalHost;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.CountInterceptor;
import com.predic8.membrane.core.interceptor.ExchangeStoreInterceptor;
import com.predic8.membrane.core.interceptor.HeaderFilterInterceptor;
import com.predic8.membrane.core.interceptor.IndexInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.interceptor.LogInterceptor;
import com.predic8.membrane.core.interceptor.MessageAnalyser;
import com.predic8.membrane.core.interceptor.RegExReplaceInterceptor;
import com.predic8.membrane.core.interceptor.ThrottleInterceptor;
import com.predic8.membrane.core.interceptor.URLNormalizerInterceptor;
import com.predic8.membrane.core.interceptor.WADLInterceptor;
import com.predic8.membrane.core.interceptor.WSDLInterceptor;
import com.predic8.membrane.core.interceptor.acl.AccessControlInterceptor;
import com.predic8.membrane.core.interceptor.administration.AdminConsoleInterceptor;
import com.predic8.membrane.core.interceptor.authentication.BasicAuthenticationInterceptor;
import com.predic8.membrane.core.interceptor.authentication.session.LoginInterceptor;
import com.predic8.membrane.core.interceptor.balancer.ClusterNotificationInterceptor;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingInterceptor;
import com.predic8.membrane.core.interceptor.cbr.XPathCBRInterceptor;
import com.predic8.membrane.core.interceptor.formvalidation.FormValidationInterceptor;
import com.predic8.membrane.core.interceptor.groovy.GroovyInterceptor;
import com.predic8.membrane.core.interceptor.rest.REST2SOAPInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor;
import com.predic8.membrane.core.interceptor.server.WSDLPublisherInterceptor;
import com.predic8.membrane.core.interceptor.server.WebServerInterceptor;
import com.predic8.membrane.core.interceptor.statistics.StatisticsCSVInterceptor;
import com.predic8.membrane.core.interceptor.statistics.StatisticsJDBCInterceptor;
import com.predic8.membrane.core.interceptor.statistics.StatisticsProvider;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.SOAPStackTraceFilterInterceptor;
import com.predic8.membrane.core.interceptor.xmlcontentfilter.XMLContentFilterInterceptor;
import com.predic8.membrane.core.interceptor.xmlprotection.XMLProtectionInterceptor;
import com.predic8.membrane.core.interceptor.xslt.XSLTInterceptor;
import com.predic8.membrane.core.transport.SSLContext;
import com.predic8.membrane.core.util.TextUtil;

public abstract class AbstractProxy extends AbstractConfigElement implements
		Rule {

	private static Log log = LogFactory.getLog(AbstractProxy.class.getName());

	protected String name = "";

	protected RuleKey key;

	protected volatile boolean blockRequest;
	protected volatile boolean blockResponse;

	protected List<Interceptor> interceptors = new ArrayList<Interceptor>();

	/**
	 * Used to determine the IP address for outgoing connections
	 */
	protected String localHost;
	
	/**
	 * Map<Status Code, StatisticCollector>
	 */
	private ConcurrentHashMap<Integer, StatisticCollector> statusCodes = new ConcurrentHashMap<Integer, StatisticCollector>();

	private class InOutElement extends AbstractXmlElement {
		private Interceptor.Flow flow;

		protected void parseAttributes(XMLStreamReader token) throws Exception {
			if ("request".equals(token.getLocalName())) {
				flow = Flow.REQUEST;
			} else {
				flow = Flow.RESPONSE;
			}
		}

		@Override
		protected void parseChildren(XMLStreamReader token, String child)
				throws Exception {
			parseInterceptors(token, child).setFlow(flow);
		}
	}

	public AbstractProxy() {
	}

	public AbstractProxy(RuleKey ruleKey) {
		this.key = ruleKey;
	}

	protected abstract void parseKeyAttributes(XMLStreamReader token);

	@Override
	public String toString() { // TODO toString, getName, setName und name=""
								// Initialisierung vereinheitlichen.
		return getName();
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child)
			throws Exception {
		if (LocalHost.ELEMENT_NAME.equals(child)) {
			this.localHost = ((LocalHost) (new LocalHost().parse(token)))
					.getValue();
		} else if (Pattern.matches("request|response", child)) {
			new InOutElement().parse(token);
		} else {
			parseInterceptors(token, child);
		}
	}

	protected void writeExtension(XMLStreamWriter out)
			throws XMLStreamException {
	}

	protected void writeRule(XMLStreamWriter out) throws XMLStreamException {
		if (this.name != null)
			out.writeAttribute("name", this.name);
		if (key.getPort() != -1)
			out.writeAttribute("port", "" + key.getPort());
		writeAttrIfTrue(out, blockRequest, "blockRequest", blockRequest);
		writeAttrIfTrue(out, blockResponse, "blockResponse", blockResponse);

		writeExtension(out);

		writeLocalHost(out);

		writeInterceptors(out);

	}

	private void writeLocalHost(XMLStreamWriter out) throws XMLStreamException {
		if (localHost == null)
			return;

		new LocalHost(localHost).write(out);
	}

	private Interceptor parseInterceptors(XMLStreamReader token, String child)
			throws Exception {
		Interceptor i = null;
		if ("interceptor".equals(child)) {
			i = getInterceptorFor(readInterceptor(token).getId());
		} else {
			i = getInlinedInterceptor(token, child);
		}
		interceptors.add(i);
		return i;
	}

	private AbstractInterceptor readInterceptor(XMLStreamReader token)
			throws Exception {
		return (AbstractInterceptor) (new AbstractInterceptor()).parse(token);
	}

	private Interceptor getInterceptorFor(String id) {
		Interceptor i = Router.getBeanFactory().getBean(id, Interceptor.class);
		i.setId(id); // returned bean does not have id set
		return i;
	}
	
	private Interceptor getInlinedInterceptor(XMLStreamReader token, String name)
			throws Exception {
		AbstractInterceptor i = null;
		if ("adminConsole".equals(name)) {
			i = new AdminConsoleInterceptor();
		} else if ("transform".equals(name)) {
			i = new XSLTInterceptor();
		} else if ("index".equals(name)) {
			i = new IndexInterceptor();
		} else if ("counter".equals(name)) {
			i = new CountInterceptor();
		} else if ("webServer".equals(name)) {
			i = new WebServerInterceptor();
		} else if ("balancer".equals(name)) {
			i = new LoadBalancingInterceptor();
		} else if ("clusterNotification".equals(name)) {
			i = new ClusterNotificationInterceptor();
		} else if ("rewriter".equals(name)) {
			i = new RewriteInterceptor();
		} else if ("validator".equals(name)) {
			i = new ValidatorInterceptor();
		} else if ("wsdlPublisher".equals(name)) {
			i = new WSDLPublisherInterceptor();
		} else if ("rest2Soap".equals(name)) {
			i = new REST2SOAPInterceptor();
		} else if ("basicAuthentication".equals(name)) {
			i = new BasicAuthenticationInterceptor();
		} else if ("regExReplacer".equals(name)) {
			i = new RegExReplaceInterceptor();
		} else if ("switch".equals(name)) {
			i = new XPathCBRInterceptor();
		} else if ("wsdlRewriter".equals(name)) {
			i = new WSDLInterceptor();
		} else if ("wadlRewriter".equals(name)) {
			i = new WADLInterceptor();
		} else if ("accessControl".equals(name)) {
			i = new AccessControlInterceptor();
		} else if ("statisticsCSV".equals(name)) {
			i = new StatisticsCSVInterceptor();
		} else if ("statisticsJDBC".equals(name)) {
			i = new StatisticsJDBCInterceptor();
		} else if ("exchangeStore".equals(name)) {
			i = new ExchangeStoreInterceptor();
		} else if ("groovy".equals(name)) {
			i = new GroovyInterceptor();
		} else if ("throttle".equals(name)) {
			i = new ThrottleInterceptor();
		} else if ("log".equals(name)) {
			i = new LogInterceptor();
		} else if ("formValidation".equals(name)) {
			i = new FormValidationInterceptor();
		} else if ("xmlContentFilter".equals(name)) {
			i = new XMLContentFilterInterceptor();
		} else if ("xmlProtection".equals(name)) {
			i = new XMLProtectionInterceptor();
		} else if ("soapStackTraceFilter".equals(name)) {
			i = new SOAPStackTraceFilterInterceptor();
		} else if ("headerFilter".equals(name)) {
			i = new HeaderFilterInterceptor();
		} else if ("analyser".equals(name)) {
			i = new MessageAnalyser();
		} else if ("statisticsProvider".equals(name)) {
			i = new StatisticsProvider();
		} else if ("urlNormalizer".equals(name)) {
			i = new URLNormalizerInterceptor();
		} else if ("login".equals(name)) {
			i = new LoginInterceptor();
		} else {
			for (Object bean : Router.getBeanFactory()
					.getBeansWithAnnotation(ElementName.class).values()) {
				String beanName = bean.getClass()
						.getAnnotation(ElementName.class).value();
				if (beanName == null)
					throw new Exception(
							bean.getClass().getName()
									+ " declared @ElementName(null), null is not allowed.");
				if (beanName.equals(name)) {
					i = (AbstractInterceptor) bean.getClass().newInstance();
					break;
				}
			}
			if (i == null)
				throw new ConfigurationException("Unknown interceptor found \"" + name + "\". Check the documentation at http://membrane-soa.org/esb-doc/current/configuration/reference/index.htm .");
		}
		i.parse(token);
		return i;
	}

	private void parseBlocking(XMLStreamReader token) {
		blockRequest = getBoolean(token, "blockRequest");
		blockResponse = getBoolean(token, "blockResponse");
	}

	protected void writeInterceptors(XMLStreamWriter out)
			throws XMLStreamException {
		writeInterceptors(out, interceptors);
	}
	
	protected void writeInterceptors(XMLStreamWriter out, List<Interceptor> interceptors)
			throws XMLStreamException {
		Flow lastFlow = Flow.REQUEST_RESPONSE;
		for (Interceptor i : interceptors) {
			if (i.getFlow() != lastFlow) {
				if (lastFlow != Flow.REQUEST_RESPONSE) {
					out.writeEndElement();
					log.debug(lastFlow == Flow.REQUEST ? "</request>"
							: "</response>");
				}

				if (i.getFlow() == Flow.REQUEST) {
					out.writeStartElement("request");
					log.debug("<request>");
				} else if (i.getFlow() == Flow.RESPONSE) {
					out.writeStartElement("response");
					log.debug("<response>");
				}
				lastFlow = i.getFlow();
			}
			log.debug(i.getFlow() + ":" + i.getDisplayName());
			i.write(out);
		}
		if (lastFlow != Flow.REQUEST_RESPONSE) {
			out.writeEndElement();
			log.debug(lastFlow == Flow.REQUEST ? "</request>" : "</response>");
		}
	}

	protected <E> void writeAttrIfTrue(XMLStreamWriter out, boolean exp,
			String n, E v) throws XMLStreamException {
		if (exp) {
			out.writeAttribute(n, "" + v);
		}
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) {
		name = token.getAttributeValue("", "name");
		parseKeyAttributes(token);
		parseBlocking(token);
	}

	public List<Interceptor> getInterceptors() {
		return interceptors;
	}

	public void setInterceptors(List<Interceptor> interceptors) {
		this.interceptors = interceptors;
	}

	public String getName() {
		return name;
	}

	public RuleKey getKey() {
		return key;
	}

	public boolean isBlockRequest() {
		return blockRequest;
	}

	public boolean isBlockResponse() {
		return blockResponse;
	}

	public void setName(String name) {
		if (name == null)
			return;
		this.name = name;

	}

	public void setKey(RuleKey ruleKey) {
		this.key = ruleKey;
	}

	public void setBlockRequest(boolean blockStatus) {
		this.blockRequest = blockStatus;
	}

	public void setBlockResponse(boolean blockStatus) {
		this.blockResponse = blockStatus;
	}

	public String getLocalHost() {
		return localHost;
	}

	public void setLocalHost(String localHost) {
		this.localHost = localHost;
	}

	private StatisticCollector getStatisticCollectorByStatusCode(int code) {
		StatisticCollector sc = statusCodes.get(code);
		if (sc == null) {
			sc = new StatisticCollector(true);
			StatisticCollector sc2 = statusCodes.putIfAbsent(code, sc);
			if (sc2 != null)
				sc = sc2;
		}
		return sc;
	}

	public void collectStatisticsFrom(Exchange exc) {
		StatisticCollector sc = getStatisticCollectorByStatusCode(exc
				.getResponse().getStatusCode());
		synchronized (sc) {
			sc.collectFrom(exc);
		}
	}

	public Map<Integer, StatisticCollector> getStatisticsByStatusCodes() {
		return statusCodes;
	}

	public int getCount() {
		int c = 0;
		for (StatisticCollector statisticCollector : statusCodes.values()) {
			c += statisticCollector.getCount();
		}
		return c;
	}

	@Override
	public Rule getDeepCopy() throws Exception {
		String xml = serialize();

		XMLStreamReader r = getStreamReaderFor(xml.getBytes(Constants.UTF_8_CHARSET));
		AbstractProxy newObject = (AbstractProxy) getNewInstance().parse(r);
		return newObject;
	}

	private String serialize() throws XMLStreamException,
			FactoryConfigurationError, UnsupportedEncodingException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		XMLStreamWriter writer = XMLOutputFactory.newInstance()
				.createXMLStreamWriter(baos, Constants.UTF_8);
		write(writer);
		ByteArrayInputStream stream = new ByteArrayInputStream(
				baos.toByteArray());
		InputStreamReader reader = new InputStreamReader(stream,
				Constants.UTF_8);
		return TextUtil.formatXML(reader);
	}

	public XMLStreamReader getStreamReaderFor(byte[] bytes)
			throws XMLStreamException {
		XMLInputFactory factory = XMLInputFactory.newInstance();
		ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
		return new FixedStreamReader(factory.createXMLStreamReader(stream, Constants.UTF_8));
	}

	protected abstract AbstractProxy getNewInstance();

	@Override
	public SSLContext getSslInboundContext() {
		return null;
	}

	@Override
	public SSLContext getSslOutboundContext() {
		return null;
	}
	
	/**
	 * Should not be overridden by subclasses. Override {@link #init()} instead.
	 */
	@Override
	public final void doAfterParsing() throws Exception {
		super.doAfterParsing();
	}
	
	/**
	 * Called after parsing is complete and this has been added to the object tree (whose root is Router).
	 */
	public void init(Router router) throws Exception {
		init();
		for (Interceptor i : interceptors)
			i.init(router);
	}
	
	public void init() throws Exception {
	}
	
	public AbstractProxy parse(XMLStreamReader token) throws Exception {
		return (AbstractProxy) super.parse(token); // we know it's covariant
	}
}
