package com.predic8.membrane.core.util;

import java.util.Comparator;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.ExchangesUtil;
import com.predic8.membrane.core.interceptor.statistics.PropertyComparator;
import com.predic8.membrane.core.rules.ServiceProxy;

public class ComparatorFactory {

	public static Comparator<AbstractExchange> getAbstractExchangeComparator(String propName, String order) {
		if ("statusCode".equals(propName)) {
			return new PropertyComparator<AbstractExchange, Integer>(order, new PropertyComparator.ValueResolver<AbstractExchange, Integer>() {
				public Integer get(AbstractExchange exc) {
					return exc.getResponse().getStatusCode();
				}
			});			
		} else if ("rule".equals(propName)) {
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
					return exc.getSourceHostname();
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
		} else if ("reqContentLenght".equals(propName)) {
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
		} else if ("respContentLenght".equals(propName)) {
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
		}
		return new PropertyComparator<AbstractExchange, String>(order, new PropertyComparator.ValueResolver<AbstractExchange, String>() {
			public String get(AbstractExchange exc) {
				return ExchangesUtil.getTime(exc);	
			}
		});			
	}

	public static Comparator<ServiceProxy> getServiceProxyComparator(final String propName, String order) {
		if ("listenPort".equals(propName)) {
			return new PropertyComparator<ServiceProxy, Integer>(order, new PropertyComparator.ValueResolver<ServiceProxy, Integer>() {
				public Integer get(ServiceProxy p) {
					return p.getKey().getPort();
				}
			});			
		} else if ("virtualHost".equals(propName)) {
			return new PropertyComparator<ServiceProxy, String>(order, new PropertyComparator.ValueResolver<ServiceProxy, String>() {
				public String get(ServiceProxy p) {
					return p.getKey().getHost();
				}
			});			
		} else if ("method".equals(propName)) {
			return new PropertyComparator<ServiceProxy, String>(order, new PropertyComparator.ValueResolver<ServiceProxy, String>() {
				public String get(ServiceProxy p) {
					return p.getKey().getMethod();
				}
			});			
		} else if ("path".equals(propName)) {
			return new PropertyComparator<ServiceProxy, String>(order, new PropertyComparator.ValueResolver<ServiceProxy, String>() {
				public String get(ServiceProxy p) {
					return p.getKey().getPath();
				}
			});			
		} else if ("targetHost".equals(propName)) {
			return new PropertyComparator<ServiceProxy, String>(order, new PropertyComparator.ValueResolver<ServiceProxy, String>() {
				public String get(ServiceProxy p) {
					return p.getTargetHost();
				}
			});			
		} else if ("targetPort".equals(propName)) {
			return new PropertyComparator<ServiceProxy, Integer>(order, new PropertyComparator.ValueResolver<ServiceProxy, Integer>() {
				public Integer get(ServiceProxy p) {
					return p.getTargetPort();
				}
			});			
		} else if ("count".equals(propName)) {
			return new PropertyComparator<ServiceProxy, Integer>(order, new PropertyComparator.ValueResolver<ServiceProxy, Integer>() {
				public Integer get(ServiceProxy p) {
					return p.getCount();
				}
			});			
		}
		return new PropertyComparator<ServiceProxy, String>(order, new PropertyComparator.ValueResolver<ServiceProxy, String>() {
			public String get(ServiceProxy p) {
				return p.toString();
			}
		});
	}
	
}
