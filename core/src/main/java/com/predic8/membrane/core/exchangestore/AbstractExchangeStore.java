/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.exchangestore;

import java.util.*;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.interceptor.rest.QueryParameter;
import com.predic8.membrane.core.model.IExchangesStoreListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.util.ComparatorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.predic8.membrane.core.interceptor.administration.AdminRESTInterceptor.getClientAddr;

public abstract class AbstractExchangeStore implements ExchangeStore {

	private static final Logger log = LoggerFactory.getLogger(AbstractExchangeStore.class);

	protected Set<IExchangesStoreListener> exchangesStoreListeners = new HashSet<IExchangesStoreListener>();

	public void addExchangesStoreListener(IExchangesStoreListener viewer) {
		exchangesStoreListeners.add(viewer);

	}
	public void removeExchangesStoreListener(IExchangesStoreListener viewer) {
		exchangesStoreListeners.remove(viewer);
	}

	public void refreshExchangeStoreListeners(){
		for (IExchangesStoreListener listener : exchangesStoreListeners) {
			listener.refresh();
		}
	}

	public void notifyListenersOnExchangeAdd(Rule rule, AbstractExchange exchange) {
		for (IExchangesStoreListener listener : exchangesStoreListeners) {
			exchange.addExchangeStoreListener(listener);
			listener.addExchange(rule, exchange);
		}
	}

	public void notifyListenersOnExchangeRemoval(AbstractExchange exchange) {
		for (IExchangesStoreListener listener : exchangesStoreListeners) {
			exchange.removeExchangeStoreListener(listener);
			listener.removeExchange(exchange);
		}
	}

	public AbstractExchange getExchangeById(long id) {
		throw new UnsupportedOperationException("getExchangeById must be implemented in the sub class.");
	}


	public List<? extends ClientStatistics> getClientStatistics() {
		throw new UnsupportedOperationException("getClientStatistics must be implemented in the sub class.");
	}

	public void init(Router router) {
	}

	public synchronized void collect(ExchangeCollector collector) {
		for (AbstractExchange exc: getAllExchangesAsList()) {
			try {
				collector.collect(exc);
			} catch (Exception e) {
				log.debug("Error while collecting properties from Exchange.", e);
			}
		}
	}

	@Override
	public long getLastModified() {
		return System.currentTimeMillis();
	}

	@Override
	public void waitForModification(long lastKnownModification) throws InterruptedException {
		// nothing
	}

	@Override
	public ExchangeQueryResult getFilteredSortedPaged(QueryParameter params, boolean useXForwardedForAsClientAddr) throws Exception {
		List<AbstractExchange> exchanges;
		long lm;
		synchronized (getAllExchangesAsList()) {
			lm = getLastModified();

			exchanges = new ArrayList<AbstractExchange>(
					getAllExchangesAsList());
		}

		exchanges = filter(params, useXForwardedForAsClientAddr, exchanges);

		Collections.sort(
				exchanges,
				ComparatorFactory.getAbstractExchangeComparator(params.getString("sort", "time"),
						params.getString("order", "desc")));

		int offset = params.getInt("offset", 0);
		int max = params.getInt("max", exchanges.size());

		final int total = exchanges.size();
		final List<AbstractExchange> paginated = exchanges.subList(offset,
				Math.min(offset + max, exchanges.size()));

		return new ExchangeQueryResult(paginated, total, lm);
	}

	private List<AbstractExchange> filter(QueryParameter params,
										  boolean useXForwardedForAsClientAddr,
										  List<AbstractExchange> exchanges) throws Exception {

		List<AbstractExchange> list = new ArrayList<AbstractExchange>();
		for (AbstractExchange e : exchanges) {
			if ((!params.has("proxy") || e.getRule().toString().equals(params.getString("proxy"))) &&
					(!params.has("statuscode") || e.getResponse().getStatusCode() == params.getInt("statuscode")) &&
					(!params.has("client") || getClientAddr(useXForwardedForAsClientAddr, e).equals(params.getString("client"))) &&
					(!params.has("server") || params.getString("server").equals(e.getServer()==null?"":e.getServer())) &&
					(!params.has("method") || e.getRequest().getMethod().equals(params.getString("method"))) &&
					(!params.has("reqcontenttype") || e.getRequestContentType().equals(params.getString("reqcontenttype"))) &&
					(!params.has("respcontenttype") || e.getResponseContentType().equals(params.getString("respcontenttype")))) {
				list.add(e);
			}
		}
		return list;
	}
}
