/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.balancer;

import java.util.regex.*;

import javax.xml.stream.*;

import org.apache.commons.logging.*;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.http.Message;

/**
 * @description The <i>jSessionIdExtractor</i> extracts the JSESSIONID from a
 *              message and provides it to the {@link Balancer}.
 */
@MCElement(name="jSessionIdExtractor")
public class JSESSIONIDExtractor extends AbstractSessionIdExtractor {

	private static Log log = LogFactory.getLog(JSESSIONIDExtractor.class.getName());

	Pattern pattern = Pattern.compile(".*JSESSIONID\\s*=([^;]*)");

	@Override
	public String getSessionId(Message msg) throws Exception {

		String cookie = msg.getHeader().getFirstValue("Cookie");
		if (cookie == null) {
			log.debug("no cookie set");
			return null;
		}

		Matcher m = pattern.matcher(cookie);

		log.debug("cookie: " + msg.getHeader().getFirstValue("Cookie"));

		if (!m.lookingAt()) return null;

		log.debug("JSESSION cookie found: "+m.group(1).trim());
		return m.group(1).trim();
	}

	@Override
	public void write(XMLStreamWriter out)
			throws XMLStreamException {

		out.writeStartElement("jSessionIdExtractor");
		out.writeEndElement();
	}

}
