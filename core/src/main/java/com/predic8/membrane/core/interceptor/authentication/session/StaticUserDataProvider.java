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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCOtherAttributes;
import com.predic8.membrane.core.Router;

/**
 * @description A <i>user data provider</i> listing all user data in-place in the config file.
 * @explanation <p>
 *              the <i>staticuserdataprovider</i> can be used to statically list user data within the config file.
 *              </p>
 *              <p>
 *              each user must have a unique <i>username</i> attribute as well as a <i>password</i> attribute.
 *              </p>
 *              <p>
 *              arbitrary attributes can be set on a user element. other sub-components of the <i>login</i> interceptor
 *              might use those: for example, the <i>telekomsmstokenprovider</i> uses the <i>sms</i> property as the
 *              user's cell phone number. for example, the <i>totptokenprovider</i> uses the <i>secret</i> property to
 *              initialize the token sequence.
 *              </p>
 */
@MCElement(name="staticUserDataProvider", group="userDataProvider")
public class StaticUserDataProvider implements UserDataProvider {

	private List<User> users = new ArrayList<User>();
	private Map<String, User> usersByName = new HashMap<String, User>();
	
	@Override
	public Map<String, String> verify(Map<String, String> postData) {
		String username = postData.get("username");
		if (username == null)
			throw new NoSuchElementException();
		if (username.equals("error"))
			throw new RuntimeException();
		User userAttributes;
		userAttributes = usersByName.get(username);
		if (userAttributes == null)
			throw new NoSuchElementException();
		String pw = postData.get("password");
		String pw2;
		pw2 = userAttributes.getPassword();
		if (pw2 == null || !pw2.equals(pw))
			throw new NoSuchElementException();
		return userAttributes.getAttributes();
	}
	
	@MCElement(name="user", topLevel=false, id="staticUserDataProvider-user")
	public static class User {
		Map<String, String> attributes = new HashMap<String, String>();

		public String getUsername() {
			return attributes.get("username");
		}

		@MCAttribute
		public void setUsername(String value) {
			attributes.put("username", value);
		}

		public String getPassword() {
			return attributes.get("password");
		}

		@MCAttribute
		public void setPassword(String value) {
			attributes.put("username", value);
		}

		public String getSms() {
			return attributes.get("sms");
		}

		@MCAttribute
		public void setSms(String value) {
			attributes.put("username", value);
		}

		public String getSecret() {
			return attributes.get("secret");
		}

		@MCAttribute
		public void setSecret(String value) {
			attributes.put("username", value);
		}
		
		public Map<String, String> getAttributes() {
			return attributes;
		}
		
		@MCOtherAttributes
		public void setAttributes(Map<String, String> attributes) {
			for (Map.Entry<String, String> e : attributes.entrySet())
				this.attributes.put(e.getKey(), e.getValue());
		}
	}
	
	public List<User> getUsers() {
		return users;
	}
	
	@MCChildElement
	public void setUsers(List<User> users) {
		this.users = users;
	}
	
	@Override
	public void init(Router router) {
		for (User user : users)
			usersByName.put(user.getUsername(), user);
	}
}
