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
package com.predic8.membrane.core.interceptor.ws_addressing;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import org.slf4j.*;
import org.springframework.context.*;

import javax.xml.stream.*;
import java.io.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

public class WsaEndpointRewriterInterceptor extends AbstractInterceptor {

	private static final Logger log = LoggerFactory.getLogger(WsaEndpointRewriterInterceptor.class);

	@Override
	public Outcome handleRequest(Exchange exc) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
			// Why is port 2020 hard coded?
            new WsaEndpointRewriter(getRegistry()).rewriteEndpoint(exc.getRequest().getBodyAsStreamDecoded(), output, 2020, exc);
        } catch (XMLStreamException e) {
			log.error("",e);
			internal(router.isProduction(),getDisplayName())
					.detail("Could not rewrite endpoint!")
					.exception(e)
					.buildAndSetResponse(exc);
			return ABORT;
        }

        exc.getRequest().setBodyContent(output.toByteArray());

		return Outcome.CONTINUE;
	}

	private DecoupledEndpointRegistry getRegistry() {
		ApplicationContext beanFactory = getRouter().getBeanFactory();
		if (beanFactory == null) {
			return new DecoupledEndpointRegistry();
		}
		return beanFactory.getBean(DecoupledEndpointRegistry.class);
	}
}