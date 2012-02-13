/* Copyright 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.xslt;

import static com.predic8.membrane.core.util.TextUtil.isNullOrEmpty;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ArrayBlockingQueue;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.util.ResourceResolver;

public class XSLTTransformer {
	private static Log log = LogFactory.getLog(XSLTTransformer.class.getName());

	private final TransformerFactory fac = TransformerFactory.newInstance();
	private final ArrayBlockingQueue<Transformer> transformers;
	private final String styleSheet;
	
	public XSLTTransformer(String styleSheet, ResourceResolver resolver, int concurrency) throws Exception {
		this.styleSheet = styleSheet;
		log.debug("using " + concurrency + " parallel transformer instances for " + styleSheet);
		transformers = new ArrayBlockingQueue<Transformer>(concurrency);
		for (int i = 0; i < concurrency; i++) {
			Transformer t;
			if (isNullOrEmpty(styleSheet))
				t = fac.newTransformer();
			else
				t = fac.newTransformer(new StreamSource(resolver.resolve(styleSheet)));
			transformers.put(t);
		}
	}

	public byte[] transform(Source xml)
			throws Exception {
		log.debug("applying transformation: " + styleSheet);
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Transformer t = transformers.take();
		try {
			t.transform(xml, new StreamResult(baos));
		} finally {
			transformers.put(t);
		}
		return baos.toByteArray();
	}

}
