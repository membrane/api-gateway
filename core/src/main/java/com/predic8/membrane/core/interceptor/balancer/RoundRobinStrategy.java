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
package com.predic8.membrane.core.interceptor.balancer;

import javax.xml.stream.*;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.exchange.AbstractExchange;

import java.util.List;

/**
 * Strategy that iterates the endpoints according to https://en.wikipedia.org/wiki/Round-robin
 * This strategy is agnostic to every other factor, such as sessions and endpoint performance.
 * All endpoints that are considered to be UP are in.
 */
@MCElement(name="roundRobinStrategy")
public class RoundRobinStrategy extends AbstractXmlElement implements DispatchingStrategy {

	private int last = -1;

	public void done(AbstractExchange exc) {
	}

	public synchronized Node dispatch(LoadBalancingInterceptor interceptor) throws EmptyNodeListException {
		//getting a decoupled copy to avoid index out of bounds in case of concurrent modification (dynamic config files reload...)
		List<Node> endpoints = interceptor.getEndpoints(); //this calls synchronizes access internally.
		if (endpoints.isEmpty()) {
			throw new EmptyNodeListException();
		}
		int i = incrementAndGet(endpoints.size());
		return endpoints.get(i);
	}

	/**
	 * Must be atomic, therefore synchronized.
	 */
	private synchronized int incrementAndGet(int numEndpoints) {
		last ++;
		if (last >= numEndpoints) {
			last = 0;
		}
		return last;
	}

	@Override
	public void write(XMLStreamWriter out)
			throws XMLStreamException {

		out.writeStartElement("roundRobinStrategy");

		out.writeEndElement();
	}

	@Override
	protected String getElementName() {
		return "roundRobinStrategy";
	}

}
