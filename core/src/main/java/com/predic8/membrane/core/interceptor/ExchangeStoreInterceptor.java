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

import java.util.HashSet;
import java.util.Set;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchangestore.ExchangeStore;
import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.MessageObserver;
import com.predic8.membrane.core.interceptor.administration.AdminConsoleInterceptor;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;

public class ExchangeStoreInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(AbstractInterceptor.class.getName());
	
	private ExchangeStore store;
	private String exchangeStoreBeanId;
	
	private Set<ServiceProxy> serviceProxiesContainingAdminConsole = new HashSet<ServiceProxy>();
	
	public ExchangeStoreInterceptor() {
		name = "Exchange Store Interceptor";
	}
	
	@Override
	public Outcome handleResponse(final Exchange exc) throws Exception {	
		return handle(exc);
	}
	
	@Override
	public void handleAbort(final Exchange exc) {
		handle(exc);	
	}

	protected Outcome handle(final Exchange exc) {
		if (serviceProxiesContainingAdminConsole.contains(exc.getRule())) {
			return Outcome.CONTINUE;
		}
			
		try {
			exc.getResponse().addObserver(new MessageObserver() {
				public void bodyComplete(AbstractBody body) {
					store.add(exc);
				}
			});
		} catch (Exception e) {
			log.info("Exchange has no probably no response due to a prior error in the response chain.");
		}
		
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
		
		searchAdminConsole();
	}
	
	private void searchAdminConsole() {
		for (Rule r : router.getRuleManager().getRules()) {
			if (!(r instanceof ServiceProxy)) continue;
			
			for (Interceptor i : r.getInterceptors()) {
				if (i instanceof AdminConsoleInterceptor) {
					serviceProxiesContainingAdminConsole.add((ServiceProxy)r);
				}
			}
		}			
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
