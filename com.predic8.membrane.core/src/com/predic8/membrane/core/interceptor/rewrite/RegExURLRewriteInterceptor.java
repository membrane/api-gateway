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
package com.predic8.membrane.core.interceptor.rewrite;

import java.util.*;
import java.util.regex.Pattern;

import javax.xml.stream.*;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.config.GenericConfigElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

public class RegExURLRewriteInterceptor extends AbstractInterceptor {

	public static class Mapping {
		public String uri;
		public String regex;

		public Mapping(String regex, String uri) {
			this.regex = regex;
			this.uri = uri;
		}
	}

	private static Log log = LogFactory.getLog(RegExURLRewriteInterceptor.class
			.getName());

	private List<Mapping> mappings = new ArrayList<Mapping>();

	public RegExURLRewriteInterceptor() {
		name = "RegEx URL Rewriter";
		priority = 150;
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		String uri = exc.getRequest().getUri();

		log.debug("uri: " + uri);

		Mapping mapping = findFirstMatchingRegEx(uri);
		if (mapping == null)
			return Outcome.CONTINUE;

		log.debug("match found: " + mapping.regex);
		log.debug("replacing with: " + mapping.uri);

		exc.getRequest().setUri(replace(uri, mapping));
		return Outcome.CONTINUE;
	}

	private String replace(String uri, Mapping mapping) {
		String replaced = uri.replaceAll(mapping.regex, mapping.uri);

		log.debug("replaced URI: " + replaced);

		return replaced;
	}

	private Mapping findFirstMatchingRegEx(String uri) {
		for (Mapping mapping : mappings) {
			if (Pattern.matches(mapping.regex, uri))
				return mapping;
		}
		return null;
	}

	public List<Mapping> getMappings() {
		return mappings;
	}

	public void setMappings(List<Mapping> mappings) {
		this.mappings = mappings;
	}

	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {

		out.writeStartElement("regExUrlRewriter");

		for (Mapping m : mappings) {
			out.writeStartElement("mapping");

			out.writeAttribute("regex", m.regex);
			out.writeAttribute("uri", m.uri);

			out.writeEndElement();
		}

		out.writeEndElement();
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child)
			throws XMLStreamException {
		if (token.getLocalName().equals("mapping")) {
			GenericConfigElement mapping = new GenericConfigElement();
			mapping.parse(token);
			mappings.add(new RegExURLRewriteInterceptor.Mapping(mapping
					.getAttribute("regex"), mapping.getAttribute("uri")));
		} else {
			super.parseChildren(token, child);
		}
	}
}
