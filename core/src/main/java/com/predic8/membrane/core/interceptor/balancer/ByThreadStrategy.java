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
package com.predic8.membrane.core.interceptor.balancer;

import java.util.*;

import javax.xml.stream.*;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.exchange.AbstractExchange;

@MCElement(name="byThreadStrategy")
public class ByThreadStrategy extends AbstractXmlElement implements DispatchingStrategy {

	private int maxNumberOfThreadsPerEndpoint = 5;

	private Map<String, Integer> endpointCount = new Hashtable<>();

	private int retryTimeOnBusy = 1000;

	public void done(AbstractExchange exc) {
		String endPoint = exc.getOriginalRequestUri();
		if (endpointCount.containsKey(endPoint)) {
			Integer counter = endpointCount.get(endPoint);
			counter--;
			if (counter == 0) {
				endpointCount.remove(endPoint);
			} else {
				endpointCount.put(endPoint, counter);
			}
		}
	}

	public Node dispatch(LoadBalancingInterceptor interceptor, AbstractExchange exc) {
		for (int j = 0; j < 5; j++) {
			for (Node ep : interceptor.getEndpoints()) {
				String hostColonPort = getHostColonPort(ep);
				if (!endpointCount.containsKey(hostColonPort)) {
					endpointCount.put(hostColonPort, 1);
					return ep;
				}

				Integer counter = endpointCount.get(hostColonPort);
				if (counter < maxNumberOfThreadsPerEndpoint) {
					counter++;
					endpointCount.put(hostColonPort, counter);
					return ep;
				} else {
					continue;
				}

			}
			try {
				Thread.sleep(retryTimeOnBusy);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		throw new RuntimeException("All available servers are busy.");
	}

	/**
	 * @description Maximum number of concurrently running requests per endpoint.
	 */
	@MCAttribute
	public void setMaxNumberOfThreadsPerEndpoint(int maxNumberOfThreadsPerEndpoint) {
		this.maxNumberOfThreadsPerEndpoint = maxNumberOfThreadsPerEndpoint;
	}

	@MCAttribute
	public void setRetryTimeOnBusy(int retryTimeOnBusy) {
		this.retryTimeOnBusy = retryTimeOnBusy;
	}

	public int getMaxNumberOfThreadsPerEndpoint() {
		return maxNumberOfThreadsPerEndpoint;
	}

	public int getRetryTimeOnBusy() {
		return retryTimeOnBusy;
	}

	private String getHostColonPort(Node ep) {
		return ep.getHost()+":"+ep.getPort();
	}

	@Override
	public void write(XMLStreamWriter out)
			throws XMLStreamException {

		out.writeStartElement("byThreadStrategy");

		out.writeAttribute("retryTimeOnBusy", ""+retryTimeOnBusy);
		out.writeAttribute("maxNumberOfThreadsPerEndpoint", ""+maxNumberOfThreadsPerEndpoint);

		out.writeEndElement();
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) {
		retryTimeOnBusy = Integer.parseInt(token.getAttributeValue("", "retryTimeOnBusy"));
		maxNumberOfThreadsPerEndpoint = Integer.parseInt(token.getAttributeValue("", "maxNumberOfThreadsPerEndpoint"));
	}

	@Override
	protected String getElementName() {
		return "byThreadStrategy";
	}

	@Override
	public void init(Router router) {
		// do nothing
	}
}
