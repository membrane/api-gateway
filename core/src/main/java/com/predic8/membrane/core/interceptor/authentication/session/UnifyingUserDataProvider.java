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

import com.predic8.membrane.annot.Required;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;

/**
 * @explanation <p>
 *              The <i>unifyingUserDataProvider</i> can be used to merge two or more other <i>user data providers</i>
 *              into one.
 *              </p>
 *              <p>
 *              The <i>unifyingUserDataProvider</i> will forward a login attempt (username and password) to each inner
 *              user data provider in the order they are specified. After one of the inner user data providers returned
 *              a successful login (and returned the user's attributes), the procedure terminates. If no inner user data
 *              provider could verify the user, the login attempt fails.
 *              </p>
 */
@MCElement(name="unifyingUserDataProvider", topLevel=false)
public class UnifyingUserDataProvider implements UserDataProvider {

	private List<UserDataProvider> userDataProviders = new ArrayList<>();

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

	@Override
	public void init(Router router) {
		for (UserDataProvider udp : userDataProviders)
			udp.init(router);
	}

}
