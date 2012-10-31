/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.xml.stream.XMLStreamReader;

import com.predic8.membrane.core.config.AbstractXmlElement;

public class StaticUserDataProvider extends AbstractXmlElement implements UserDataProvider {

	private Map<String, Map<String, String>> users = new HashMap<String, Map<String,String>>();
	
	@Override
	public Map<String, String> verify(Map<String, String> postData) {
		String username = postData.get("username");
		if (username == null)
			throw new NoSuchElementException();
		if (username.equals("error"))
			throw new RuntimeException();
		Map<String, String> userAttributes;
		synchronized (users) {
			userAttributes = users.get(username);
		}
		if (userAttributes == null)
			throw new NoSuchElementException();
		String pw = postData.get("password");
		String pw2;
		synchronized (userAttributes) {
			pw2 = userAttributes.get("password");
		}
		if (pw2 == null || !pw2.equals(pw))
			throw new NoSuchElementException();
		return userAttributes;
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {
		if (child.equals("user")) {
			HashMap<String, String> attributes = new HashMap<String, String>();
			for (int i = 0; i < token.getAttributeCount(); i++)
				attributes.put(token.getAttributeName(i).getLocalPart(), token.getAttributeValue(i));
			synchronized (users) {
				users.put(attributes.get("username"), attributes);
			}
			new AbstractXmlElement() {}.parse(token);
		} else {
			super.parseChildren(token, child);
		}
	}
	
	public Map<String, Map<String, String>> getUsers() {
		return users;
	}
	
	public void setUsers(Map<String, Map<String, String>> users) {
		this.users = users;
	}

}
