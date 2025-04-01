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

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.rest.QueryParameter;
import com.predic8.membrane.core.model.IExchangesStoreListener;
import com.predic8.membrane.core.proxies.Proxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.predic8.membrane.core.interceptor.administration.AdminRESTInterceptor.getClientAddr;
import static com.predic8.membrane.core.util.ComparatorFactory.getAbstractExchangeComparator;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.containsIgnoreCase;

public abstract class AbstractExchangeStore implements ExchangeStore {

	private static final Logger log = LoggerFactory.getLogger(AbstractExchangeStore.class);

	protected final Set<IExchangesStoreListener> exchangesStoreListeners = new HashSet<>();

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

	public void notifyListenersOnExchangeAdd(Proxy proxy, AbstractExchange exchange) {
		for (IExchangesStoreListener listener : exchangesStoreListeners) {
			exchange.addExchangeStoreListener(listener);
			listener.addExchange(proxy, exchange);
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
	public List<String> getUniqueValuesOf(String field) {
		List<AbstractExchange> exchanges;
		synchronized (getAllExchangesAsList()) {
			exchanges = new ArrayList<>(getAllExchangesAsList());
		}
		return exchanges.stream().map(switch (field) {
			case "statuscode" -> (AbstractExchange exc) -> exc.getResponse() == null ? "0" :  String.valueOf(exc.getResponse().getStatusCode());
			case "method" -> (AbstractExchange exc) -> exc.getRequest().getMethod();
			case "path" -> (AbstractExchange exc) -> exc.getRequest().getUri();
			default -> throw new IllegalStateException("Unexpected value: " + field);
		}).distinct().toList();
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
		boolean noClient = !params.has("client");
		boolean noServer = !params.has("server");
		boolean noMethod = !params.has("method");
		boolean noReqcontenttypn = !params.has("reqcontenttype");
		boolean noRespcontenttype = !params.has("respcontenttype");
		boolean noSearch =  !params.has("search");
		boolean noStatuscode = !params.has("statuscode");

		String client = params.getString("client");
		String server = params.getString("server");
		String method = params.getString("method");
		String reqcontenttype = params.getString("reqcontenttype");
		String respcontenttype = params.getString("respcontenttype");
		String search = params.getString("search");
		int statuscode = noStatuscode ? -1 : params.getInt("statuscode");

		return exchanges.stream().filter(e -> filterExchanges(params,
				useXForwardedForAsClientAddr,
				noStatuscode,
				noClient,
				noServer,
				noMethod,
				noReqcontenttypn, noRespcontenttype,
				noSearch,
				statuscode,
				client,
				server,
				method,
				reqcontenttype,
				respcontenttype,
				search, e)
		).collect(toList()); // Do not simplify cause a mutable list is needed.
	}

	private static boolean filterExchanges(QueryParameter params, boolean useXForwardedForAsClientAddr, boolean noStatuscode, boolean noClient, boolean noServer, boolean noMethod, boolean noReqcontenttypn, boolean noRespcontenttype, boolean noSearch, int statuscode, String client, String server, String method, String reqcontenttype, String respcontenttype, String search, AbstractExchange e) {
		return (!params.has("proxy") || e.getProxy().toString().equals(params.getString("proxy"))) &&
               (noStatuscode || compareStatusCode(statuscode, e)) &&
               (noClient || getClientAddr(useXForwardedForAsClientAddr, e).equals(client)) &&
               (noServer || server.equals(e.getServer() == null ? "" : e.getServer())) &&
               (noMethod || e.getRequest().getMethod().equals(method)) &&
               (noReqcontenttypn || e.getRequestContentType().equals(reqcontenttype)) &&
               (noRespcontenttype || e.getResponseContentType().equals(respcontenttype)) &&
               (noSearch || bodyContains(search, e) || requestHeaderContains(search, e) || responseHeaderContains(search, e));
	}

	private static boolean compareStatusCode(int statuscode, AbstractExchange e) {
		if (e.getResponse() == null)
			return false;
		return e.getResponse().getStatusCode() == statuscode;
	}

	protected static boolean pathContains(String search, AbstractExchange e) {
		return containsIgnoreCase(e.getRequest().getUri(), search);
	}

	protected static boolean requestHeaderContains(String search, AbstractExchange e) {
		return headerFieldsContains(search, e.getRequest().getHeader().getAllHeaderFields());
	}

	protected static boolean responseHeaderContains(String search, AbstractExchange e) {
		if (e.getResponse() == null)
			return false;
		return headerFieldsContains(search, e.getResponse().getHeader().getAllHeaderFields());
	}

	private static boolean headerFieldsContains(String search, HeaderField[] fields) {
		for (HeaderField field: fields) {
			if (containsIgnoreCase(field.getHeaderName().toString(), search)) {
				return true;
			}
			if (containsIgnoreCase(field.getValue(), search)) {
				return true;
			}
		}
		return false;
	}

	private static boolean bodyContains(String search, AbstractExchange e) {
		return bodyContains(search, e.getRequest()) || (e.getResponse() != null &&  bodyContains(search, e.getResponse()));
	}

	private static boolean bodyContains(String search, Message msg) {
		try {
			if (msg.isBodyEmpty())
				return false;
			return containsIgnoreCase(msg.getBodyAsStringDecoded(),search);
		} catch (Exception e) {
			return false;
		}
	}
}