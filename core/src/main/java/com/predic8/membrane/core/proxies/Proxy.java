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
package com.predic8.membrane.core.proxies;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.stats.*;

import java.util.*;

/**
 * Should not know anything about HTTP
 */
public interface Proxy extends Cloneable {

	List<Interceptor> getFlow();

	void setFlow(List<Interceptor> interceptors);

	RuleKey getKey();

	void setKey(RuleKey ruleKey);

	void setName(String name);

	String getName();

	RuleStatisticCollector getStatisticCollector();

	void init(Router router) throws Exception;

	boolean isTargetAdjustHostHeader();

	boolean isActive();

	String getErrorState();

	Proxy clone() throws CloneNotSupportedException;

	String getProtocol();
}
