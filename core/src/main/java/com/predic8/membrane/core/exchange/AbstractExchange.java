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

import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.model.*;
import com.predic8.membrane.core.rules.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

import static com.predic8.membrane.core.exchange.ExchangeState.*;

public abstract class AbstractExchange {
	private static final Logger log = LoggerFactory.getLogger(AbstractExchange.class.getName());

	protected Request request;
	private Response response;

	private String originalRequestUri;

	private Calendar time = Calendar.getInstance();
	private String errMessage = "";
	private final Set<IExchangeViewerListener> exchangeViewerListeners = new HashSet<>();
	private final Set<IExchangesStoreListener> exchangesStoreListeners = new HashSet<>();

	protected Rule rule;

	protected Map<String, Object> properties = new HashMap<>();

	private ExchangeState status = STARTED;

	private boolean forceToStop = false;


	private long tReqSent;

	private long tReqReceived;

	private long tResSent;

	private long tResReceived;

	private List<String> destinations = new ArrayList<>();


	private String remoteAddr;
	private String remoteAddrIp;

	private ArrayList<Interceptor> interceptorStack = new ArrayList<>(10);

	private int estimatedHeapSize = -1;

	public AbstractExchange() {

	}

	/**
	 * For HttpResendRunnable
	 */
	public AbstractExchange(AbstractExchange original) {
		properties = new HashMap<>(original.properties);
		originalRequestUri = original.originalRequestUri;
		destinations.addAll(original.getDestinations());
		rule = original.getRule();
	}

	public void setStatus(ExchangeState state) {
		this.status = state;
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
		status = COMPLETED;
		notifyExchangeFinished();
	}

	public void setStopped() {
		status = SENT;
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
		if (status != COMPLETED) {
			status = FAILED;
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

	public void setForceToStop(boolean forceToStop) {
		this.forceToStop = forceToStop;
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
		status = RECEIVED;
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

	public long getResponseContentLength() {
		long length = getResponse().getHeader().getContentLength();
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

	public long getRequestContentLength() {
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

	public void setDestinations(List<String> destinations) {
		this.destinations = destinations;
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
	 * If &lt;transport reverseDNS="true"/&gt;, getRemoteAddr() returns the hostname of the incoming TCP connection's remote address.
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
		return "[time:" + DateFormat.getDateInstance().format(time.getTime()) + (request != null ? ",requestURI:"+ request.getUri() : "") + "]";
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

	public int resetHeapSizeEstimation() {
		int estimatedHeapSize2 = estimatedHeapSize;
		estimatedHeapSize = -1;
		return estimatedHeapSize2;
	}

	protected int estimateHeapSize() {
		return 2600 +
				(originalRequestUri != null ? originalRequestUri.length() : 0) +
				(request != null ? request.estimateHeapSize() : 0) +
				(response != null ? response.estimateHeapSize() : 0);
	}

	public static <T extends AbstractExchange> T updateCopy(T source, T copy, Runnable bodyUpdatedCallback, BodyCollectingMessageObserver.Strategy strategy, long limit) {
		if (bodyUpdatedCallback != null) {
			if (source.getRequest() != null)
				copy.setRequest(source.getRequest().createSnapshot(bodyUpdatedCallback, strategy, limit));
			if (source.getResponse() != null)
				copy.setResponse(source.getResponse().createSnapshot(bodyUpdatedCallback, strategy, limit));
		}

		copy.setOriginalRequestUri(source.getOriginalRequestUri());
		copy.setTime(source.getTime());
		copy.setErrorMessage(source.getErrorMessage());
		copy.setRule(source.getRule());
		copy.setProperties(new HashMap<>(source.getProperties()));
		copy.setStatus(source.getStatus());
		copy.setForceToStop(source.isForcedToStop());
		copy.setTimeReqSent(source.getTimeReqSent());
		copy.setTimeReqReceived(source.getTimeReqReceived());
		copy.setTimeResSent(source.getTimeResSent());
		copy.setTimeResReceived(source.getTimeResReceived());
		copy.setDestinations(new ArrayList<>(source.getDestinations()));
		copy.setRemoteAddr(source.getRemoteAddr());
		copy.setRemoteAddrIp(source.getRemoteAddrIp());
		copy.setInterceptorStack(new ArrayList<>(source.getInterceptorStack()));

		return copy;
	}

	public String getPublicUrl(){
		String xForwardedProto = getRequest().getHeader().getFirstValue(Header.X_FORWARDED_PROTO);
		boolean isHTTPS = xForwardedProto != null ? "https".equals(xForwardedProto) : getRule().getSslInboundContext() != null;
		String publicURL = (isHTTPS ? "https://" : "http://") + getRequest().getHeader().getHost().replaceFirst(".*:", "");
		RuleKey key = getRule().getKey();
		if (!key.isPathRegExp() && key.getPath() != null)
			publicURL += key.getPath();

		return publicURL;
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

	public abstract <T extends AbstractExchange> T createSnapshot(Runnable bodyUpdatedCallback, BodyCollectingMessageObserver.Strategy strategy, long limit) throws Exception;

	public ArrayList<Interceptor> getInterceptorStack() {
		return interceptorStack;
	}

	public void setInterceptorStack(ArrayList<Interceptor> interceptorStack){
		this.interceptorStack = interceptorStack;
	}


}
