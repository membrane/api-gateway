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

import com.predic8.membrane.core.*;

import java.util.*;

public interface UserDataProvider {

	void init(Router router);

	/**
	 * @throws NoSuchElementException
	 *             to display an error message as a response
	 * @return a hash map containing the user's attributes (used for the token
	 *         generator or to pass on HTTP headers)
	 */
	Map<String, String> verify(Map<String, String> postData);
}
