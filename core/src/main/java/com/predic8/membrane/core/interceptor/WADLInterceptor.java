/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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

import static com.predic8.membrane.core.Constants.WADL_NS;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.ws.relocator.Relocator;

@MCElement(name="wadlRewriter")
public class WADLInterceptor extends RelocatingInterceptor {

	private static Log log = LogFactory.getLog(WADLInterceptor.class.getName());

	public WADLInterceptor() {
		name = "WADL Rewriting Interceptor";
		setFlow(Flow.RESPONSE);
	}

	protected void rewrite(Exchange exc) throws Exception, IOException {

		log.debug("Changing endpoint address in WADL");

		ByteArrayOutputStream stream = new ByteArrayOutputStream();

		Relocator relocator = new Relocator(new OutputStreamWriter(stream,
				getCharset(exc)), getLocationProtocol(), getLocationHost(exc),
				getLocationPort(exc), pathRewriter);

		relocator.getRelocatingAttributes().put(
				new QName(WADL_NS, "resources"), "base");
		relocator.getRelocatingAttributes().put(new QName(WADL_NS, "include"),
				"href");

		relocator.relocate(new InputStreamReader(new ByteArrayInputStream(exc
				.getResponse().getBody().getContent()), getCharset(exc)));

		exc.getResponse().setBodyContent(stream.toByteArray());
	}

	@MCAttribute
	@Override
	public void setProtocol(String protocol) {
		super.setProtocol(protocol);
	}
	
	@MCAttribute
	@Override
	public void setHost(String host) {
		super.setHost(host);
	}
	
	@MCAttribute
	@Override
	public void setPort(String port) {
		super.setPort(port);
	}
}
