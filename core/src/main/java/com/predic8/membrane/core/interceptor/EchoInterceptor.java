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
package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * @description Returns the flow of plugins and copies the content of the
 * request into a new response. The response has a status code of 200.
 * Useful for testing.
 */
@MCElement(name="echo", topLevel = false)
public class EchoInterceptor extends AbstractInterceptor {

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		Response response = Response.ok()
				.body(exc.getRequest().getBody().getContent())
				.header(exc.getRequest().getHeader())
				.build();
		exc.setResponse(response);
		return RETURN;
	}
}
