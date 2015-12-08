/* Copyright 2015 predic8 GmbH, www.predic8.com

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

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;

@MCElement(name = "nodeOnlineChecker")
public class NodeOnlineChecker {
	private static Log log = LogFactory.getLog(NodeOnlineChecker.class.getName());
	LoadBalancingInterceptor lbi;

	public NodeOnlineChecker() {
	}

	public void handle(Exchange exc) {
		if (exc.getNodeExceptions() != null) {
			for (int i = 0; i < exc.getDestinations().size(); i++) {
				if (exc.getNodeExceptions()[i] != null) {
					setNodeDown(exc, i);
				}
			}
		}
		if (exc.getNodeStatusCodes() != null) {
			for (int i = 0; i < exc.getDestinations().size(); i++) {
				if (exc.getNodeStatusCodes()[i] != 0) {
					int status = exc.getNodeStatusCodes()[i];
					if (status >= 400 && status < 600) {
						setNodeDown(exc, i);
					}
				}
			}
		}
	}

	public void setNodeDown(Exchange exc, int destination) {
		URL destUrl = getUrlObjectFromDestination(exc, destination);
		log.info("Node down: " + destUrl.toString());
		for (Cluster cl : lbi.getClusterManager().getClusters()) {
			cl.nodeDown(new Node(destUrl.getHost(), destUrl.getPort()));
		}
	}

	private URL getUrlObjectFromDestination(Exchange exc, int destination) {
		String url = exc.getDestinations().get(destination);
		URL u = null;
		try {
			u = new URL(url);
		} catch (MalformedURLException e) {
		}
		return u;
	}

	public LoadBalancingInterceptor getLbi() {
		return lbi;
	}

	public void setLbi(LoadBalancingInterceptor lbi) {
		this.lbi = lbi;
	}

}
