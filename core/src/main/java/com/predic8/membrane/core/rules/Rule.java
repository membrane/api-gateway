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
package com.predic8.membrane.core.rules;

import java.util.List;
import java.util.Map;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.transport.SSLContext;

public interface Rule {
	
	public List<Interceptor> getInterceptors();
	
	public void setInterceptors(List<Interceptor> interceptors); 
	
	public boolean isBlockRequest();
	
	public boolean isBlockResponse();
	
	public RuleKey getKey();
	
	public void setKey(RuleKey ruleKey);
	
	public void setName(String name);
	
	public String getName();
	
	public void setBlockRequest(boolean blockStatus);
	
	public void setBlockResponse(boolean blockStatus);
	
	public String getLocalHost();
	
	public void setLocalHost(String localHost);
	
	public void collectStatisticsFrom(Exchange exc);

	public Map<Integer, StatisticCollector> getStatisticsByStatusCodes();
	
	public int getCount();
	
	public SSLContext getSslInboundContext();

	public SSLContext getSslOutboundContext();
	
	public void init(Router router) throws Exception;
}
