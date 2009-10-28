/* Copyright 2009 predic8 GmbH, www.predic8.com

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

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.model.IExchangeViewerListener;
import com.predic8.membrane.core.model.IRuleTreeViewerListener;
import com.predic8.membrane.core.rules.Rule;

public class Exchange {
	private Request request;
	private Response response;
	
	private Calendar time = Calendar.getInstance();
	private String errMessage = "";
	private Set<IExchangeViewerListener> exchangeViewerListeners = new HashSet<IExchangeViewerListener>();
	private Set<IRuleTreeViewerListener> treeViewerListeners = new HashSet<IRuleTreeViewerListener>();
	private Rule rule;

	protected Map<String, Object> properties = new HashMap<String, Object>();

	private ExchangeState status = ExchangeState.STARTED;

	private boolean forceToStop = false;

	
	private long tReqSent;
	
	private long tReqReceived;
	
	private long tResSent;
	
	private long tResReceived;
	
	private Exception exception;
	
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

	public void addTreeViewerListener(IRuleTreeViewerListener viewer) {
		treeViewerListeners.add(viewer);
	}

	public void removeTreeViewerListener(IRuleTreeViewerListener viewer) {
		treeViewerListeners.remove(viewer);
	}

	public void setCompleted() {
		status = ExchangeState.COMPLETED;
		for (IExchangeViewerListener listener : exchangeViewerListeners) {
			listener.setExchangeFinished();
		}

		for (IRuleTreeViewerListener listener : treeViewerListeners) {
			listener.setExchangeFinished(this);
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
		close();

		if (request != null)
			request.release();
		if (response != null)
			response.release();

		if (refresh) {
			for (IExchangeViewerListener listener : exchangeViewerListeners) {
				listener.setExchangeFinished();
			}

			for (IRuleTreeViewerListener listener : treeViewerListeners) {
				listener.setExchangeFinished(this);
			}
		}
	}

	public void close() {
		
	}

	public boolean isForceToStop() {
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

	public Exception getException() {
		return exception;
	}

	public void setException(Exception exception) {
		this.exception = exception;
	}

}
