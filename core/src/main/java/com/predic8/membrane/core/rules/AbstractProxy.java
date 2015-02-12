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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.SSLProvider;

public abstract class AbstractProxy implements Rule {
	private static final Log log = LogFactory.getLog(AbstractProxy.class.getName());

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

	private boolean active;
	private String error;

	protected Router router;
	
	public AbstractProxy() {
	}

	public AbstractProxy(RuleKey ruleKey) {
		this.key = ruleKey;
	}

	@Override
	public String toString() { // TODO toString, getName, setName und name=""
								// Initialisierung vereinheitlichen.
		return getName();
	}

	public List<Interceptor> getInterceptors() {
		return interceptors;
	}

	@MCChildElement(allowForeign=true, order=100)
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

	/**
	 * @description The name as shown in the Admin Console.
	 * @default By default, a name will be automatically generated from the target host, port, etc.
	 */
	@MCAttribute
	public void setName(String name) {
		if (name == null)
			return;
		this.name = name;

	}

	public void setKey(RuleKey ruleKey) {
		this.key = ruleKey;
	}

	/**
	 * @description <i>legacy attribute</i> for usage by Membrane Monitor
	 * @default false
	 */
	@MCAttribute
	public void setBlockRequest(boolean blockStatus) {
		this.blockRequest = blockStatus;
	}

	/**
	 * @description <i>legacy attribute</i> for usage by Membrane Monitor
	 * @default false
	 */
	@MCAttribute
	public void setBlockResponse(boolean blockStatus) {
		this.blockResponse = blockStatus;
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

	protected abstract AbstractProxy getNewInstance();

	@Override
	public SSLContext getSslInboundContext() {
		return null;
	}

	@Override
	public SSLProvider getSslOutboundContext() {
		return null;
	}
	
	/**
	 * Called after parsing is complete and this has been added to the object tree (whose root is Router).
	 */
	public final void init(Router router) throws Exception {
		this.router = router;
		try {
			init();
			for (Interceptor i : interceptors)
				i.init(router);
			active = true;
		} catch (Exception e) {
			if (!router.isRetryInit())
				throw e;
			log.error(e);
			active = false;
			error = e.getMessage();
		}
	}
	
	public abstract void init() throws Exception;
	
	public boolean isTargetAdjustHostHeader() {
		return false;
	}
	
	@Override
	public boolean isActive() {
		return active;
	}
	
	@Override
	public String getErrorState() {
		return error;
	}
	
	@Override
	public AbstractProxy clone() throws CloneNotSupportedException {
		AbstractProxy clone = (AbstractProxy) super.clone();
		try {
			clone.init(router);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return clone;
	}

}
