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
package com.predic8.membrane.core.interceptor.balancer;

import static junit.framework.Assert.assertEquals;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.http.params.HttpProtocolParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.balancer.*;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.services.DummyWebServiceInterceptor;
import com.predic8.membrane.interceptor.LoadBalancingInterceptorTest;

public class LoadBalancingWithClusterManagerAndNoSessionTest extends LoadBalancingInterceptorTest{

	@Before
	public void setUp() throws Exception {
		super.setUp();
		ClusterManager cm = new ClusterManager();
		cm.up("Default", "localhost", 2000);
		cm.up("Default", "localhost", 3000);
		balancer.setClusterManager(cm);
		balancingInterceptor.setRouter(balancer);
		balancingInterceptor.setEndpoints(new LinkedList<String>());
	}

}
