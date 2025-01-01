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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.stats.*;
import com.predic8.membrane.core.transport.ssl.*;

import java.util.*;

public interface Rule extends Cloneable {

	List<Interceptor> getInterceptors();

	void setInterceptors(List<Interceptor> interceptors);

	boolean isBlockRequest();

	boolean isBlockResponse();

	RuleKey getKey();

	void setKey(RuleKey ruleKey);

	void setName(String name);

	String getName();

	void setBlockRequest(boolean blockStatus);

	void setBlockResponse(boolean blockStatus);

	RuleStatisticCollector getStatisticCollector();

	// Question: Can we push up ssl stuff?
	SSLContext getSslInboundContext();

	SSLProvider getSslOutboundContext();

	void init(Router router) throws Exception;

	boolean isTargetAdjustHostHeader();

	boolean isActive();

	String getErrorState();

	Rule clone() throws CloneNotSupportedException;
}
