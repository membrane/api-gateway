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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.rest.*;
import com.predic8.membrane.core.model.*;
import com.predic8.membrane.core.rules.*;
import org.apache.commons.lang3.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.administration.AdminRESTInterceptor.*;
import static com.predic8.membrane.core.util.ComparatorFactory.*;

public abstract class AbstractExchangeStore implements ExchangeStore {

	private static final Logger log = LoggerFactory.getLogger(AbstractExchangeStore.class);

	protected Set<IExchangesStoreListener> exchangesStoreListeners = new HashSet<>();

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

			exchanges = new ArrayList<>(getAllExchangesAsList());
		}

		exchanges = filter(params, useXForwardedForAsClientAddr, exchanges);

		exchanges.sort(getAbstractExchangeComparator(params.getString("sort", "time"),
				params.getString("order", "desc")));

		return new ExchangeQueryResult(getPaginated(params, exchanges, params.getInt("offset", 0)), exchanges.size(), lm);
	}

	private static List<AbstractExchange> getPaginated(QueryParameter params, List<AbstractExchange> exchanges, int offset) {
		return exchanges.subList(offset,
				Math.min(offset + getMax(params, exchanges), exchanges.size()));
	}

	protected static int getMax(QueryParameter params, List<AbstractExchange> exchanges) {
		return params.getInt("max", exchanges.size());
	}

	private List<AbstractExchange> filter(QueryParameter params,
										  boolean useXForwardedForAsClientAddr,
										  List<AbstractExchange> exchanges) {

		// Speed up search
		boolean noStatuscode = !params.has("statuscode");
		boolean noClient = !params.has("client");
		boolean noServer = !params.has("server");
		boolean noMethod = !params.has("method");
		boolean noReqcontenttypn = !params.has("reqcontenttype");
		boolean noRespcontenttype = !params.has("respcontenttype");
		boolean noSearch =  !params.has("search");
		int statuscode = -1;
		if (!noStatuscode)
			 statuscode = params.getInt("statuscode");
		String client = params.getString("client");
		String server = params.getString("server");
		String method = params.getString("method");
		String reqcontenttype = params.getString("reqcontenttype");
		String respcontenttype = params.getString("respcontenttype");
		String search = params.getString("search");

		List<AbstractExchange> list = new ArrayList<>();
		for (AbstractExchange e : exchanges) {
			if ((!params.has("proxy") || e.getRule().toString().equals(params.getString("proxy"))) &&
				(noStatuscode || e.getResponse().getStatusCode() == statuscode) &&
				(noClient || getClientAddr(useXForwardedForAsClientAddr, e).equals(client)) &&
				(noServer || server.equals(e.getServer() == null?"":e.getServer())) &&
				(noMethod || e.getRequest().getMethod().equals(method)) &&
				(noReqcontenttypn || e.getRequestContentType().equals(reqcontenttype)) &&
				(noRespcontenttype || e.getResponseContentType().equals(respcontenttype)) &&
				(noSearch || containsString(search,e))
			) {
				list.add(e);
			}
		}
		return list;
	}

	private static boolean containsString(String search, AbstractExchange e) {
		return bodyContainsString(search, e.getRequest()) || bodyContainsString(search, e.getResponse());
	}

	private static boolean bodyContainsString(String search, Message msg) {
		try {
			if (msg.isBodyEmpty())
				return false;
			return StringUtils.containsIgnoreCase(msg.getBodyAsStringDecoded(),search);
		} catch (Exception e) {
			return false;
		}
	}
}