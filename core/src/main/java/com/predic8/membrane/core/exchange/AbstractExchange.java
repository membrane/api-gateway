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

package com.predic8.membrane.core.exchange;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.predic8.membrane.core.exchangestore.ExchangeStore;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.model.IExchangeViewerListener;
import com.predic8.membrane.core.model.IExchangesStoreListener;
import com.predic8.membrane.core.rules.AbstractServiceProxy;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.Rule;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public abstract class AbstractExchange {
	private static final Log log = LogFactory.getLog(AbstractExchange.class.getName());

	protected Request request;
	private Response response;

	private String originalRequestUri;

	private Calendar time = Calendar.getInstance();
	private String errMessage = "";
	private Set<IExchangeViewerListener> exchangeViewerListeners = new HashSet<IExchangeViewerListener>();
	private Set<IExchangesStoreListener> exchangesStoreListeners = new HashSet<IExchangesStoreListener>();
	protected Rule rule;

	protected Map<String, Object> properties = new HashMap<String, Object>();

	private ExchangeState status = ExchangeState.STARTED;

	private boolean forceToStop = false;


	private long tReqSent;

	private long tReqReceived;

	private long tResSent;

	private long tResReceived;

	private List<String> destinations = new ArrayList<String>();


	private String remoteAddr;
	private String remoteAddrIp;

	private final ArrayList<Interceptor> interceptorStack = new ArrayList<Interceptor>(10);

	private int estimatedHeapSize = -1;

	public AbstractExchange() {

	}

	/**
	 * For HttpResendRunnable
	 * @param original
	 */
	public AbstractExchange(AbstractExchange original) {
		properties = new HashMap<String, Object>(original.properties);
		originalRequestUri = original.originalRequestUri;

		List<String> origDests = original.getDestinations();
		for (String dest : origDests) {
			destinations.add(dest);
		}
		rule = original.getRule();
	}

	public ExchangeState getStatus() {
		return status;
	}

	public Calendar getTime() {
		return time;
	}

	public void setTime(Calendar time) {
		this.time = time;
	}

	public Request getRequest() {
		return request;
	}

	public void setRequest(Request request) {
		this.request = request;
		if (this.request != null) {
			this.request.setErrorMessage(errMessage);
		}
		for (IExchangeViewerListener listener : exchangeViewerListeners) {
			listener.addRequest(request);
		}

	}

	public Response getResponse() {
		return response;
	}

	public void setResponse(Response res) {
		response = res;
		if (response != null) {
			response.setErrorMessage(errMessage);
		}

		for (IExchangeViewerListener listener : exchangeViewerListeners) {
			listener.addResponse(res);
		}
	}

	public Rule getRule() {
		return rule;
	}

	public void setRule(Rule rule) {
		this.rule = rule;
	}

	public void addExchangeViewerListener(IExchangeViewerListener viewer) {
		exchangeViewerListeners.add(viewer);

	}

	public void removeExchangeViewerListener(IExchangeViewerListener viewer) {
		exchangeViewerListeners.remove(viewer);

	}

	public void addExchangeStoreListener(IExchangesStoreListener viewer) {
		exchangesStoreListeners.add(viewer);
	}

	public void removeExchangeStoreListener(IExchangesStoreListener viewer) {
		exchangesStoreListeners.remove(viewer);
	}

	public void setCompleted() {
		status = ExchangeState.COMPLETED;
		notifyExchangeFinished();
	}

	public void setStopped() {
		status = ExchangeState.SENT;
		notifyExchangeStopped();
	}

	private void notifyExchangeFinished() {
		for (IExchangeViewerListener listener : exchangeViewerListeners) {
			listener.setExchangeFinished();
		}

		for (IExchangesStoreListener listener : exchangesStoreListeners) {
			listener.setExchangeFinished(this);
		}
	}

	private void notifyExchangeStopped() {
		for (IExchangeViewerListener listener : exchangeViewerListeners) {
			listener.setExchangeStopped();
		}

		for (IExchangesStoreListener listener : exchangesStoreListeners) {
			listener.setExchangeStopped(this);
		}
	}

	public void finishExchange(boolean refresh) {
		finishExchange(refresh, "");
	}

	public void finishExchange(boolean refresh, String errmsg) {
		errMessage = errmsg;
		if (status != ExchangeState.COMPLETED) {
			status = ExchangeState.FAILED;
			forceToStop = true;
		}

		if (request != null)
			request.release();
		if (response != null)
			response.release();

		if (refresh) {
			notifyExchangeFinished();
		}
	}

	public boolean isForcedToStop() {
		return forceToStop;
	}

	public String getErrorMessage() {
		return errMessage;
	}

	public void setErrorMessage(String errMessage) {
		this.errMessage = errMessage;
	}

	public void informExchangeViewerOnRemoval() {
		for (IExchangeViewerListener listener : exchangeViewerListeners) {
			listener.removeExchange();
		}
	}

	public void setReceived() {
		status = ExchangeState.RECEIVED;
	}

	public Object getProperty(String key) {
		return properties.get(key);
	}

	public String getStringProperty(String key) {
		return (String) properties.get(key);
	}

	public void setProperty(String key, Object value) {
		properties.put(key, value);
	}

	public long getTimeReqSent() {
		return tReqSent;
	}

	public void setTimeReqSent(long tReqSent) {
		this.tReqSent = tReqSent;
	}

	public long getTimeReqReceived() {
		return tReqReceived;
	}

	public void setTimeReqReceived(long tReqReceived) {
		this.tReqReceived = tReqReceived;
	}

	public void received() {
		setTimeReqReceived(System.currentTimeMillis());
	}

	public long getTimeResSent() {
		return tResSent;
	}

	public void setTimeResSent(long tResSent) {
		this.tResSent = tResSent;
	}

	public long getTimeResReceived() {
		return tResReceived;
	}

	public void setTimeResReceived(long tResReceived) {
		this.tResReceived = tResReceived;
	}

	public String getOriginalRequestUri() {
		return originalRequestUri;
	}

	public void setOriginalRequestUri(String requestUri) {
		this.originalRequestUri = requestUri;
	}

	public String getServer() {
		if (getRule() instanceof ProxyRule) {
			try {
				if (getRequest().isCONNECTRequest()) {
					return getRequest().getHeader().getHost();
				}

				return new URL(getOriginalRequestUri()).getHost();
			} catch (MalformedURLException e) {
				log.error("", e);
			}
			return getOriginalRequestUri();
		}
		if (getRule() instanceof AbstractServiceProxy) {
			return ((AbstractServiceProxy) getRule()).getTargetHost();
		}
		return "";
	}

	public int getResponseContentLength() {
		int length = getResponse().getHeader().getContentLength();
		if (length != -1)
			return length;

		if (length == -1 && getResponse().getBody().isRead()) {
			try {
				return  getResponse().getBody().getLength();
			} catch (IOException e) {
				log.error("", e);
			}
		}

		return -1;
	}

	public int getRequestContentLength() {
		return getRequest().getHeader().getContentLength();
	}

	public String getRequestContentType() {
		return extractContentTypeValue(getRequest().getHeader().getContentType());
	}

	public String getResponseContentType() {
		if (getResponse() == null)
			return "";
		return extractContentTypeValue(getResponse().getHeader().getContentType());
	}

	private String extractContentTypeValue(String contentType) {
		if (contentType == null)
			return "";

		int index = contentType.indexOf(";");
		if (index > 0) {
			return contentType.substring(0, index);
		}
		return contentType;
	}

	/**
	 * @return Probably never empty.
	 *         Is this guaranteed to always contain at least 1 entry? There are many hardcoded calls with
	 *         getDestinations().get(0) in the system.
	 */
	public List<String> getDestinations() {
		return destinations;
	}

	/**
	 * If &lt;transport reverseDNS="true"/&gt;, {@link #getRemoteAddr()} returns the hostname of the incoming TCP connection's remote address.
	 * If false, it returns the IP address.
	 */
	public String getRemoteAddr() {
		return remoteAddr;
	}

	public void setRemoteAddr(String remoteAddr) {
		this.remoteAddr = remoteAddr;
	}

	/**
	 * @return The IP address of the incoming TCP connection's remote address.
	 */
	public String getRemoteAddrIp() {
		return remoteAddrIp;
	}

	public void setRemoteAddrIp(String remoteAddrIp) {
		this.remoteAddrIp = remoteAddrIp;
	}

	@Override
	public String toString() {
		return "[time:"+DateFormat.getDateInstance().format(time.getTime())+",requestURI:"+request.getUri()+"]";
	}

	public void pushInterceptorToStack(Interceptor i) {
		interceptorStack.add(i);
	}

	public Interceptor popInterceptorFromStack() {
		int s = interceptorStack.size();
		return s == 0 ? null : interceptorStack.remove(s-1);
	}

	public int getHeapSizeEstimation() {
		if (estimatedHeapSize == -1)
			estimatedHeapSize = estimateHeapSize();
		return estimatedHeapSize;
	}

	protected int resetHeapSizeEstimation() {
		int estimatedHeapSize2 = estimatedHeapSize;
		estimatedHeapSize = 0;
		return estimatedHeapSize2;
	}

	protected int estimateHeapSize() {
		return 2000 +
				(originalRequestUri != null ? originalRequestUri.length() : 0) +
				(request != null ? request.estimateHeapSize() : 0) +
				(response != null ? response.estimateHeapSize() : 0);
	}

	/**
	 * Prepares for long-term storage (for example, in-memory {@link ExchangeStore}s).
	 */
	public void detach() {
		properties.clear();
	}

	public abstract long getId();

	public Map<String, Object> getProperties() {
		return properties;
	}

	public void setProperties(Map<String, Object> properties) {
		this.properties = properties;
	}
}
