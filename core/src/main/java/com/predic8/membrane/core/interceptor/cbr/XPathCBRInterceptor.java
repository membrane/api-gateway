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

package com.predic8.membrane.core.interceptor.cbr;

import static com.predic8.membrane.core.util.SynchronizedXPathFactory.newXPath;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPathConstants;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import org.xml.sax.InputSource;

import com.googlecode.jatl.Html;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.TextUtil;

/**
 * @description Changes an exchange's target based on a series of XPath expressions.
 * @topic 3. Enterprise Integration Patterns
 */
@MCElement(name="switch")
public class XPathCBRInterceptor extends AbstractInterceptor {
	private static Log log = LogFactory.getLog(XPathCBRInterceptor.class.getName());
	
	private List<Case> cases = new ArrayList<Case>();
	private Map<String, String> namespaces;
	
	public XPathCBRInterceptor() {
		name = "Content Based Router";
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		if (exc.getRequest().isBodyEmpty()) {
			return Outcome.CONTINUE;
		}
		
		Case r = findRoute(exc.getRequest());		
		if (r == null) {
			return Outcome.CONTINUE;
		}
		log.debug("match found for {"+r.getxPath()+"} routing to {"+ r.getUrl() + "}");
		
		updateDestination(exc, r);
		return Outcome.CONTINUE;
	}

	private void updateDestination(Exchange exc, Case r) {
		exc.setOriginalRequestUri(r.getUrl());		
		exc.getDestinations().clear();
		exc.getDestinations().add(r.getUrl());
	}

	private Case findRoute(Request request) throws Exception {
		for (Case r : cases) {
			//TODO getBodyAsStream creates ByteArray each call. That could be a performance issue. Using BufferedInputStream did't work, because stream got closed.
			InputSource is = new InputSource(request.getBodyAsStreamDecoded());
			is.setEncoding(request.getCharset());
			if ( (Boolean) newXPath(namespaces).evaluate(r.getxPath(), is, XPathConstants.BOOLEAN) ) 
				return r;
			log.debug("no match found for xpath {"+r.getxPath()+"}");
		}			
		return null;			
	}

	public Map<String, String> getNamespaces() {
		return namespaces;
	}

	public void setNamespaces(Map<String, String> namespaces) {
		this.namespaces = namespaces;
	}

	/**
	 * @description Specifies a XPath expression and a target URL.
	 */
	@Required
	@MCChildElement
	public void setCases(List<Case> cases) {
		this.cases = cases;
	}
	
	public List<Case> getCases() {
		return cases;
	}
	
	@Override
	public String getShortDescription() {
		return "Routes incoming requests based on XPath expressions.";
	}
	
	@Override
	public String getLongDescription() {
		StringBuilder sb = new StringBuilder();
		sb.append(getShortDescription());
		sb.append("<br/>");
		StringWriter sw = new StringWriter();
		new Html(sw){{
			text("The requests are routed based on the following rules:");
			table();
				thead();
					tr();
						th().text("XPath").end();
						th().text("URL").end();
					end();
				end();
				tbody();
				for (Case c : cases) {
					tr();
						td().text(c.getxPath()).end();
						td().raw(TextUtil.linkURL(c.getUrl())).end();
					end();
				}
				end();
			end();
		}};
		sb.append(sw.toString());
		return sb.toString();
	}
	
}
