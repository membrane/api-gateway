/* Copyright 2009, 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.swagger;

import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import io.swagger.util.Json;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

/**
 * @description Allow Swagger proxying
 */
@MCElement(name = "swaggerRewriter")
public class SwaggerRewriterInterceptor extends AbstractInterceptor {

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		if (exc.getResponseContentType().equalsIgnoreCase("application/json")) {
			Swagger swagBody = new SwaggerParser().parse(exc.getResponse().getBodyAsStringDecoded());
			swagBody.setHost(exc.getOriginalHostHeaderHost()
					+ (exc.getOriginalHostHeaderPort().length() > 0 ? ":" + exc.getOriginalHostHeaderPort() : ""));
			exc.getResponse().setBodyContent(Json.pretty(swagBody).getBytes());
		}
		return super.handleResponse(exc);
	}

	@Override
	public String getShortDescription() {
		return super.getShortDescription();
	}


}
