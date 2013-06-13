/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.authentication.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.xml.stream.XMLStreamReader;

import org.springframework.beans.factory.annotation.Required;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.config.XMLElement;

@MCElement(name="unifyingUserDataProvider", group="userDataProvider", topLevel=false)
public class UnifyingUserDataProvider extends AbstractXmlElement implements UserDataProvider {

	private List<UserDataProvider> userDataProviders = new ArrayList<UserDataProvider>(); 

	@Override
	public XMLElement parse(XMLStreamReader token) throws Exception {
		userDataProviders.clear();
		return super.parse(token);
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {
		UserDataProvider userDataProvider;
		if (child.equals("staticUserDataProvider")) {
			userDataProvider = new StaticUserDataProvider();
			((StaticUserDataProvider) userDataProvider).parse(token);
			userDataProviders.add(userDataProvider);
		} else if (child.equals("ldapUserDataProvider")) {
			userDataProvider = new LDAPUserDataProvider();
			((LDAPUserDataProvider) userDataProvider).parse(token);
			userDataProviders.add(userDataProvider);
		} else if (child.equals("unifyingUserDataProvider")) {
			userDataProvider = new UnifyingUserDataProvider();
			((UnifyingUserDataProvider) userDataProvider).parse(token);
			userDataProviders.add(userDataProvider);
		} else {
			super.parseChildren(token, child);
		}
	}

	
	@Override
	public Map<String, String> verify(Map<String, String> postData) {
		for (UserDataProvider udp : userDataProviders)
			try {
				return udp.verify(postData);
			} catch (NoSuchElementException e) {
			}
		throw new NoSuchElementException();
	}
	
	public List<UserDataProvider> getUserDataProviders() {
		return userDataProviders;
	}
	
	@Required
	@MCChildElement
	public void setUserDataProviders(List<UserDataProvider> userDataProviders) {
		this.userDataProviders = userDataProviders;
	}

}
