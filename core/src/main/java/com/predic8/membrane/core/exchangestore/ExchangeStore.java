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
import com.predic8.membrane.core.interceptor.Interceptor.*;
import com.predic8.membrane.core.interceptor.rest.*;
import com.predic8.membrane.core.model.*;
import com.predic8.membrane.core.proxies.*;

import java.util.*;

public interface ExchangeStore {

	void addExchangesStoreListener(IExchangesStoreListener viewer);

	void removeExchangesStoreListener(IExchangesStoreListener viewer);

	void refreshExchangeStoreListeners();

	/**
	 * Adds the current state of the exchange to the store.
	 * <p>
	 * Implementations should take a snapshot of the current state of the request (or response) headers and register a
	 * body observer in which they will be called back as soon as the body has fully been received.
	 * <p>
	 * If flow==REQUEST, the request is added. Elsewise, the response is added (if present).
	 */
	void snap(AbstractExchange exchange, Flow flow);

	void remove(AbstractExchange exchange);

	void removeAllExchanges(Proxy proxy);

	void removeAllExchanges(AbstractExchange[] exchanges);

	AbstractExchange[] getExchanges(RuleKey ruleKey);

	StatisticCollector getStatistics(RuleKey ruleKey);

	Object[] getAllExchanges();

	List<AbstractExchange> getAllExchangesAsList();

	AbstractExchange getExchangeById(long id);

	default void init(Router router) {}

	List<? extends ClientStatistics> getClientStatistics();

	void collect(ExchangeCollector col);

	long getLastModified();
	/**
	 * Returns immediately if lastKnownModification is smaller than last known modification.
	 * Otherwise it waits until a modification occurs.
	 */
	void waitForModification(long lastKnownModification) throws InterruptedException;

    ExchangeQueryResult getFilteredSortedPaged(QueryParameter params, boolean useXForwardedForAsClientAddr) throws Exception;

    List<String> getUniqueValuesOf(String field);
}
