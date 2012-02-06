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

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.config.GenericComplexElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

public class RewriteInterceptor extends AbstractInterceptor {

	public enum Type {
		REWRITE,
		REDIRECT_TEMPORARY,
		REDIRECT_PERMANENT,
	}
	
	public static class Mapping {
		public String to;
		public String from;
		public final Type do_;
		
		private Pattern pattern;
		
		public Mapping(String from, String to, String do_) {
			this.from = from;
			this.to = to;
			
			if (StringUtils.isEmpty(do_))
				this.do_ = to.contains("://") ? Type.REDIRECT_TEMPORARY : Type.REWRITE;
			else if (do_.equals("rewrite"))
				this.do_ = Type.REWRITE;
			else if (do_.equals("redirect") || do_.equals("redirect-temporary"))
				this.do_ = Type.REDIRECT_TEMPORARY;
			else if (do_.equals("redirect-permanent"))
				this.do_ = Type.REDIRECT_PERMANENT;
			else
				throw new IllegalArgumentException("Unknown value '" + do_ + "' for rewriter/@do.");

			pattern = Pattern.compile(from);
		}
		
		public boolean matches(String uri) {
			return pattern.matcher(uri).find();
		}
	}

	private static Log log = LogFactory.getLog(RewriteInterceptor.class.getName());

	private List<Mapping> mappings = new ArrayList<Mapping>();

	public RewriteInterceptor() {
		name = "URL Rewriter";
		setFlow(Flow.REQUEST);
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
	
		logMappings();
		
		ListIterator<String>  it = exc.getDestinations().listIterator();
		while ( it.hasNext() ) {
			String dest = it.next();
			
			String pathQuery = URIUtil.getPathQuery(dest);
			int pathBegin = dest.lastIndexOf(pathQuery);
			String schemaHostPort = pathBegin == -1 ? dest : dest.substring(0, pathBegin); // TODO check -1 case
			
			log.debug("pathQuery: " + pathQuery);
			log.debug("schemaHostPort: " + schemaHostPort);
			
			Mapping mapping = findFirstMatchingRegEx(pathQuery);
			if (mapping == null)
				continue;
			
			log.debug("match found: " + mapping.from);
			log.debug("replacing with: " + mapping.to);
			log.debug("for type: " + mapping.do_);
			
			String newDest = replace(pathQuery, mapping);

			if (mapping.do_ == Type.REDIRECT_PERMANENT || mapping.do_ == Type.REDIRECT_TEMPORARY) {
				exc.setResponse(Response.redirect(newDest, mapping.do_ == Type.REDIRECT_PERMANENT).build());
				return Outcome.ABORT;
			}

			if (!newDest.contains("://")) {
				// prepend schema, host and port from original uri
				newDest = schemaHostPort + newDest;
			}
			
			it.set(newDest);
		}
		return Outcome.CONTINUE;
	}

	private void logMappings() {
		for (Mapping m : mappings) {
			log.debug("[from:"+m.from+"],[to:"+m.to+"],[do:"+m.do_+"]");
		}
	}
	
	private String replace(String uri, Mapping mapping) {
		String replaced = uri.replaceAll(mapping.from, mapping.to);

		log.debug("replaced URI: " + replaced);

		return replaced;
	}

	private Mapping findFirstMatchingRegEx(String uri) {
		for (Mapping m : mappings) {
			if (m.matches(uri))
				return m;
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

		out.writeStartElement("rewriter");

		for (Mapping m : mappings) {
			out.writeStartElement("map");

			out.writeAttribute("from", m.from);
			out.writeAttribute("to", m.to);
			out.writeAttribute("do", m.do_.toString().toLowerCase().replace('_', '-'));

			out.writeEndElement();
		}

		out.writeEndElement();
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child)
			throws Exception {
		if (token.getLocalName().equals("map")) {
			GenericComplexElement mapping = new GenericComplexElement();
			mapping.parse(token);
			mappings.add(new RewriteInterceptor.Mapping(mapping
					.getAttribute("from"), mapping.getAttribute("to"), mapping.getAttribute("do")));
		} else {
			super.parseChildren(token, child);
		}
	}
}
