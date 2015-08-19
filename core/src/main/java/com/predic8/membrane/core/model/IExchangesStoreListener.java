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

package com.predic8.membrane.core.model;


import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.rules.Rule;


public interface IExchangesStoreListener{

	//	public void ruleAdded(Rule rule);
	//
	//	public void removeRule(Rule rule, int rulesLeft);

	public void addExchange(Rule rule,AbstractExchange exchange);

	public void removeExchange(AbstractExchange exchange);

	public void removeExchanges(Rule parent, AbstractExchange[] exchanges);

	public void removeExchanges(AbstractExchange[] exchanges);

	public void setExchangeFinished(AbstractExchange exchange);

	public void setExchangeStopped(AbstractExchange exchange);

	public void refresh();

}
