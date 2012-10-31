package com.predic8.membrane.core.interceptor.authentication.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.xml.stream.XMLStreamReader;

import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.config.XMLElement;

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
	
	public void setUserDataProviders(List<UserDataProvider> userDataProviders) {
		this.userDataProviders = userDataProviders;
	}

}
