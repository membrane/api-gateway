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
package com.predic8.membrane.core.interceptor.rest;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.xml.Request;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.xslt.*;
import org.slf4j.*;

import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import static java.nio.charset.StandardCharsets.*;

abstract class SOAPRESTHelper extends AbstractInterceptor {

	private static final Logger log = LoggerFactory.getLogger(REST2SOAPInterceptor.class.getName());

	private final ConcurrentHashMap<String, XSLTTransformer> xsltTransformers = new ConcurrentHashMap<>();

	protected XSLTTransformer getTransformer(String ss) throws Exception {
		String key = ss == null ? "null" : ss;
		XSLTTransformer t = xsltTransformers.get(key);
		if (t == null) {
			int concurrency = 2 * Runtime.getRuntime().availableProcessors();
			t = new XSLTTransformer(ss, router, concurrency);
			XSLTTransformer t2 = xsltTransformers.putIfAbsent(key, t);
			if (t2 != null)
				return t2;
		}
		return t;
	}

	protected StreamSource getRequestXMLSource(Exchange exc) throws Exception {
		Request req = new Request(exc.getRequest());

		String res = req.toXml();
		log.debug("http-xml: " + res);

		return new StreamSource(new StringReader(res));
	}

	protected StreamSource getExchangeXMLSource(Exchange exc) throws Exception {
		com.predic8.membrane.core.http.xml.Exchange xmlExc = new com.predic8.membrane.core.http.xml.Exchange(exc);

		String res = xmlExc.toXml();
		log.debug("http-xml: " + res);

		return new StreamSource(new StringReader(res));
	}

	protected void transformAndReplaceBody(Message msg, String ss, Source src, Map<String, String> properties)
			throws Exception {
		byte[] soapEnv = getTransformer(ss).transform(src, properties);
		if (log.isDebugEnabled())
			log.debug("soap-env: " + new String(soapEnv, UTF_8));
		msg.setBodyContent(soapEnv);
	}

}
