/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

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

import java.util.Map;

import javax.xml.transform.stream.StreamSource;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.util.TextUtil;

/**
 * @description <p>
 *              The transform feature applies an XSLT transformation to the content in the body of a message. After the
 *              transformation the body content is replaced with the result of the transformation.
 *              </p>
 * @topic 3. Enterprise Integration Patterns
 */
@MCElement(name="transform")
public class XSLTInterceptor extends AbstractInterceptor {

	private String xslt;
	private volatile XSLTTransformer xsltTransformer;
	private XOPReconstitutor xopr = new XOPReconstitutor();

	public XSLTInterceptor() {
		name = "XSLT Transformer";
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		transformMsg(exc.getRequest(), xslt, exc.getStringProperties());
		return Outcome.CONTINUE;
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		transformMsg(exc.getResponse(), xslt, exc.getStringProperties());
		return Outcome.CONTINUE;
	}

	private void transformMsg(Message msg, String ss, Map<String, String> parameter) throws Exception {
		if (msg.isBodyEmpty())
			return;
		msg.setBodyContent(xsltTransformer.transform(
				new StreamSource(xopr.reconstituteIfNecessary(msg)), parameter));
	}
	
	@Override
	public void init() throws Exception {
		int concurrency = Runtime.getRuntime().availableProcessors() * 2;
		xsltTransformer = new XSLTTransformer(xslt, router, concurrency);
	}
	
	public String getXslt() {
		return xslt;
	}

	/**
	 * @description Location of the XSLT stylesheet that will be applied to request and response.
	 * @example strip.xslt
	 */
	@MCAttribute
	public void setXslt(String xslt) {
		this.xslt = xslt;
		this.xsltTransformer = null;
	}
	
	@Override
	public String getShortDescription() {
		return "Applies an XSLT transformation.";
	}
	
	@Override
	public String getLongDescription() {
		StringBuilder sb = new StringBuilder();
		sb.append(TextUtil.removeFinalChar(getShortDescription()));
		sb.append(" using the stylesheet at ");
		sb.append(TextUtil.linkURL(xslt));
		sb.append(" .");
		return sb.toString();
	}
	
}
