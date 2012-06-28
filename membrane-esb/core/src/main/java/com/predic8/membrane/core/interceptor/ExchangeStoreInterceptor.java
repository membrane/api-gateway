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

package com.predic8.membrane.core.interceptor;

import javax.xml.stream.*;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchangestore.ExchangeStore;
import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.MessageObserver;

public class ExchangeStoreInterceptor extends AbstractInterceptor {

	private ExchangeStore store;
	private String exchangeStoreBeanId;
	
	public ExchangeStoreInterceptor() {
		name = "Exchange Store Interceptor";
	}
	
	@Override
	public Outcome handleRequest(final Exchange exc) throws Exception {
		exc.getRequest().addObserver(new MessageObserver() {
			public void bodyComplete(AbstractBody body) {
				store.add(exc);
			}
		});
		return Outcome.CONTINUE;
	}

	@Override
	public Outcome handleResponse(final Exchange exc) throws Exception {
		exc.getResponse().addObserver(new MessageObserver() {
			public void bodyComplete(AbstractBody body) {
				store.add(exc);
			}
		});
		return Outcome.CONTINUE;
	}
	
	public ExchangeStore getExchangeStore() {
		return store;		
	}

	public void setExchangeStore(ExchangeStore exchangeStore) {
		store = exchangeStore;
	}
	
	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {

		out.writeStartElement("exchangeStore");

		out.writeAttribute("name", exchangeStoreBeanId);

		out.writeEndElement();
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) {
		exchangeStoreBeanId = token.getAttributeValue("", "name");
	}
	
	public void init() throws Exception {
		if (exchangeStoreBeanId != null)
			store = router.getBean(exchangeStoreBeanId, ExchangeStore.class);
	}
	
	@Override
	public String getShortDescription() {
		return "Logs all exchanges (requests and responses) into an exchange store "+
				"that can be inspected using <a href=\"http://www.membrane-soa.org/soap-monitor/\">Membrane Monitor</a>.";
	}
	
	@Override
	public String getHelpId() {
		return "exchangeStore";
	}

}
