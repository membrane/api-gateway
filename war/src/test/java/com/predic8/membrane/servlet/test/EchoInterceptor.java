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
package com.predic8.membrane.servlet.test;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.io.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.*;

@MCMain(outputPackage="com.predic8.membrane.servlet.test.config.spring",
outputName="router-conf.xsd",
targetNamespace="http://membrane-soa.org/war-test/1/")
@MCElement(name="echo", configPackage="com.predic8.membrane.servlet.test.config.spring")
public class EchoInterceptor extends AbstractInterceptor {

	private static final Logger log = LoggerFactory.getLogger(EchoInterceptor.class.getName());

	@Override
	public Outcome handleRequest(Exchange exc) {
        try {
            exc.setResponse(Response.ok().body(getBody(exc)).build());
        } catch (IOException e) {
			log.error("",e);
			user(router.isProduction(),getDisplayName())
					.detail("Error creating echo response!")
					.exception(e)
					.buildAndSetResponse(exc);
			return ABORT;
        }
        return RETURN;
	}

	private static @NotNull String getBody(Exchange exc) throws IOException {
		return exc.getRequest().getUri() + "\n" + new String(exc.getRequest().getBody().getContent(), UTF_8);
	}
}
