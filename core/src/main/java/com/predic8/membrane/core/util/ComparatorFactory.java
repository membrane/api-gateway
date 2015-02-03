/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util;

import java.util.Comparator;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.ExchangesUtil;
import com.predic8.membrane.core.exchangestore.ClientStatistics;
import com.predic8.membrane.core.interceptor.statistics.PropertyComparator;
import com.predic8.membrane.core.rules.AbstractServiceProxy;

public class ComparatorFactory {

	public static Comparator<AbstractExchange> getAbstractExchangeComparator(String propName, String order) {
		if ("statusCode".equals(propName)) {
			return new PropertyComparator<AbstractExchange, Integer>(order, new PropertyComparator.ValueResolver<AbstractExchange, Integer>() {
				public Integer get(AbstractExchange exc) {
					return exc.getResponse().getStatusCode();
				}
			});			
		} else if ("proxy".equals(propName)) {
			return new PropertyComparator<AbstractExchange, String>(order, new PropertyComparator.ValueResolver<AbstractExchange, String>() {
				public String get(AbstractExchange exc) {
					return exc.getRule().toString();
				}
			});			
		} else if ("method".equals(propName)) {
			return new PropertyComparator<AbstractExchange, String>(order, new PropertyComparator.ValueResolver<AbstractExchange, String>() {
				public String get(AbstractExchange exc) {
					return exc.getRequest().getMethod();
				}
			});			
		} else if ("path".equals(propName)) {
			return new PropertyComparator<AbstractExchange, String>(order, new PropertyComparator.ValueResolver<AbstractExchange, String>() {
				public String get(AbstractExchange exc) {
					return exc.getRequest().getUri();
				}
			});			
		} else if ("client".equals(propName)) {
			return new PropertyComparator<AbstractExchange, String>(order, new PropertyComparator.ValueResolver<AbstractExchange, String>() {
				public String get(AbstractExchange exc) {
					return exc.getRemoteAddr();
				}
			});			
		} else if ("server".equals(propName)) {
			return new PropertyComparator<AbstractExchange, String>(order, new PropertyComparator.ValueResolver<AbstractExchange, String>() {
				public String get(AbstractExchange exc) {
					return exc.getServer();
				}
			});			
		} else if ("reqContentType".equals(propName)) {
			return new PropertyComparator<AbstractExchange, String>(order, new PropertyComparator.ValueResolver<AbstractExchange, String>() {
				public String get(AbstractExchange exc) {
					return exc.getRequestContentType();
				}
			});			
		} else if ("reqContentLength".equals(propName)) {
			return new PropertyComparator<AbstractExchange, Integer>(order, new PropertyComparator.ValueResolver<AbstractExchange, Integer>() {
				public Integer get(AbstractExchange exc) {
					return exc.getRequestContentLength();
				}
			});			
		} else if ("respContentType".equals(propName)) {
			return new PropertyComparator<AbstractExchange, String>(order, new PropertyComparator.ValueResolver<AbstractExchange, String>() {
				public String get(AbstractExchange exc) {
					return exc.getResponseContentType();
				}
			});			
		} else if ("respContentLength".equals(propName)) {
			return new PropertyComparator<AbstractExchange, Integer>(order, new PropertyComparator.ValueResolver<AbstractExchange, Integer>() {
				public Integer get(AbstractExchange exc) {
					return exc.getResponseContentLength();
				}
			});			
		} else if ("duration".equals(propName)) {
			return new PropertyComparator<AbstractExchange, Long>(order, new PropertyComparator.ValueResolver<AbstractExchange, Long>() {
				public Long get(AbstractExchange exc) {
					return exc.getTimeResReceived() - exc.getTimeReqSent();
				}
			});			
		} else if ("msgFilePath".equals(propName)) {
			return new PropertyComparator<AbstractExchange, String>(order, new PropertyComparator.ValueResolver<AbstractExchange, String>() {
				public String get(AbstractExchange exc) {
					return exc.getRequest().getMethod();
				}
			});			
		} else if ("time".equals(propName)) {
			return new PropertyComparator<AbstractExchange, String>(order, new PropertyComparator.ValueResolver<AbstractExchange, String>() {
				public String get(AbstractExchange exc) {
					return ExchangesUtil.getTime(exc);	
				}
			});			
		}
		
		throw new IllegalArgumentException("AbstractExchange can't be compared using property ["+propName+"]");
	}

	public static Comparator<AbstractServiceProxy> getAbstractServiceProxyComparator(final String propName, String order) {
		if ("listenPort".equals(propName)) {
			return new PropertyComparator<AbstractServiceProxy, Integer>(order, new PropertyComparator.ValueResolver<AbstractServiceProxy, Integer>() {
				public Integer get(AbstractServiceProxy p) {
					return p.getKey().getPort();
				}
			});			
		} else if ("virtualHost".equals(propName)) {
			return new PropertyComparator<AbstractServiceProxy, String>(order, new PropertyComparator.ValueResolver<AbstractServiceProxy, String>() {
				public String get(AbstractServiceProxy p) {
					return p.getKey().getHost();
				}
			});			
		} else if ("method".equals(propName)) {
			return new PropertyComparator<AbstractServiceProxy, String>(order, new PropertyComparator.ValueResolver<AbstractServiceProxy, String>() {
				public String get(AbstractServiceProxy p) {
					return p.getKey().getMethod();
				}
			});			
		} else if ("path".equals(propName)) {
			return new PropertyComparator<AbstractServiceProxy, String>(order, new PropertyComparator.ValueResolver<AbstractServiceProxy, String>() {
				public String get(AbstractServiceProxy p) {
					return p.getKey().getPath();
				}
			});			
		} else if ("targetHost".equals(propName)) {
			return new PropertyComparator<AbstractServiceProxy, String>(order, new PropertyComparator.ValueResolver<AbstractServiceProxy, String>() {
				public String get(AbstractServiceProxy p) {
					return p.getTargetHost();
				}
			});			
		} else if ("targetPort".equals(propName)) {
			return new PropertyComparator<AbstractServiceProxy, Integer>(order, new PropertyComparator.ValueResolver<AbstractServiceProxy, Integer>() {
				public Integer get(AbstractServiceProxy p) {
					return p.getTargetPort();
				}
			});			
		} else if ("count".equals(propName)) {
			return new PropertyComparator<AbstractServiceProxy, Integer>(order, new PropertyComparator.ValueResolver<AbstractServiceProxy, Integer>() {
				public Integer get(AbstractServiceProxy p) {
					return p.getCount();
				}
			});			
		} else if ("name".equals(propName)) {
			return new PropertyComparator<AbstractServiceProxy, String>(order, new PropertyComparator.ValueResolver<AbstractServiceProxy, String>() {
				public String get(AbstractServiceProxy p) {
					return p.toString();
				}
			});			
		}
		
		throw new IllegalArgumentException("AbstractServiceProxy can't be compared using property ["+propName+"]");
		
	}

	public static Comparator<ClientStatistics> getClientStatisticsComparator(String propName,
			String order) {
		if ("name".equals(propName)) {
			return new PropertyComparator<ClientStatistics, String>(order, new PropertyComparator.ValueResolver<ClientStatistics, String>() {
				public String get(ClientStatistics c) {
					return c.getClient();
				}
			});			
		} else if ("count".equals(propName)) {
			return new PropertyComparator<ClientStatistics, Integer>(order, new PropertyComparator.ValueResolver<ClientStatistics, Integer>() {
				public Integer get(ClientStatistics c) {
					return c.getCount();
				}
			});			
		} else if ("min".equals(propName)) {
			return new PropertyComparator<ClientStatistics, Long>(order, new PropertyComparator.ValueResolver<ClientStatistics, Long>() {
				public Long get(ClientStatistics c) {
					return c.getMinDuration();
				}
			});			
		} else if ("max".equals(propName)) {
			return new PropertyComparator<ClientStatistics, Long>(order, new PropertyComparator.ValueResolver<ClientStatistics, Long>() {
				public Long get(ClientStatistics c) {
					return c.getMaxDuration();
				}
			});			
		} else if ("avg".equals(propName)) {
			return new PropertyComparator<ClientStatistics, Long>(order, new PropertyComparator.ValueResolver<ClientStatistics, Long>() {
				public Long get(ClientStatistics c) {
					return c.getAvgDuration();
				}
			});			
		}
		
		throw new IllegalArgumentException("ClientsStatistics can't be compared using property ["+propName+"]");
	}
	
}
