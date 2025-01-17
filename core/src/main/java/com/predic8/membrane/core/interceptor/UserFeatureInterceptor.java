/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

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
import org.slf4j.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.*;

/**
 * Handles features that are user-configured in proxies.xml .
 */
@MCElement(name="userFeature")
public class UserFeatureInterceptor extends AbstractInterceptor {

	private static final Logger log = LoggerFactory.getLogger(UserFeatureInterceptor.class.getName());

	public UserFeatureInterceptor() {
		name = "user feature";
		setFlow(REQUEST_RESPONSE_ABORT_FLOW); // ?
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
        return getFlowController().invokeRequestHandlers(exc, exc.getProxy().getInterceptors());
	}

	@Override
	public Outcome handleResponse(Exchange exc) {
        return getFlowController().invokeResponseHandlers(exc, exc.getProxy().getInterceptors());
	}

	@Override
	public void handleAbort(Exchange exc) {
        getFlowController().invokeAbortHandlers(exc, exc.getProxy().getInterceptors(), exc.getProxy().getInterceptors().size());
	}
}