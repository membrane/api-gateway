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

import java.io.*;

import javax.xml.transform.*;
import javax.xml.transform.stream.*;

import org.apache.commons.logging.*;

public class XSLTTransformer {
	private static Log log = LogFactory.getLog(XSLTTransformer.class.getName());
	
	private TransformerFactory fac = TransformerFactory.newInstance();
	
	public String transform(String ss, Source xml) throws Exception {		
		log.debug("applying transformation: "+ss);		
		StringWriter sw = new StringWriter();
		getTransformer(ss).transform(xml, new StreamResult(sw));
		return sw.toString();		
	}

	private Transformer getTransformer(String ss)
			throws TransformerConfigurationException {
		
		if (isNullOrEmpty(ss)) return fac.newTransformer();
		
		return fac.newTransformer(getStylesheetSource(ss));
	}
	
	private StreamSource getStylesheetSource(String name) {
		if (name.startsWith("classpath:")) {
			log.debug("loading stylesheet from classpath: "+name);
			return new StreamSource(getClass().getResourceAsStream(name.substring(10)));
		}
		//return new StreamSource(FileUtil.prefixMembraneHomeIfNeeded(new File(name)));
		log.debug("loading stylesheet from filesystem: "+name);
		return new StreamSource(new File(name));
	}
}
