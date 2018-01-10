/* Copyright 2012,2014 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.groovy;

import java.util.HashMap;
import java.util.Map;

import com.predic8.membrane.core.rules.ServiceProxy;
import org.apache.commons.lang.StringEscapeUtils;

import com.google.common.base.Function;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.lang.groovy.GroovyLanguageSupport;
import com.predic8.membrane.core.util.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @topic 4. Interceptors/Features
 */
@MCElement(name = "groovy", mixed = true)
public class GroovyInterceptor extends AbstractInterceptor {
	Logger log = LoggerFactory.getLogger(GroovyInterceptor.class);
	private String src = "";

	private Function<Map<String, Object>, Object> script;

	public GroovyInterceptor() {
		name = "Groovy";
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		return runScript(exc, Flow.REQUEST);
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		return runScript(exc, Flow.RESPONSE);
	}

	@Override
	public void handleAbort(Exchange exc) {
		try {
			runScript(exc, Flow.ABORT);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public void init() {
		if (router == null)
			return;
		if ("".equals(src))
			return;

		script = new GroovyLanguageSupport().compileScript(router, src);

	}

	private Outcome runScript(Exchange exc, Flow flow) throws InterruptedException {
		HashMap<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("exc", exc);
		parameters.put("flow", flow);
		parameters.put("spring", router.getBeanFactory());
		Object res = null;
		try {
			 res = script.apply(parameters);
		}catch (Exception e){
			logGroovyException(flow, e);
			return Outcome.ABORT;
		}

		if (res instanceof Outcome) {
			return (Outcome) res;
		}

		if (res instanceof Response) {
			exc.setResponse((Response) res);
			return Outcome.RETURN;
		}

		if (res instanceof Request) {
			exc.setRequest((Request) res);
		}
		return Outcome.CONTINUE;

	}

	private void logGroovyException(Flow flow, Exception e) {
		ServiceProxy sp = getRule();
		log.error("Exception in Groovy script in service proxy '" + sp.getName() + "' on port " + sp.getPort() + " with path " + (sp.getPath() != null ? sp.getPath() : "*"));
		log.error("Flow: " + flow.name());
		e.printStackTrace();
	}

	public String getSrc() {
		return src;
	}

	@MCTextContent
	public void setSrc(String src) {
		this.src = src;
	}

	@Override
	public String getShortDescription() {
		return "Executes a groovy script.";
	}

	@Override
	public String getLongDescription() {
		StringBuilder sb = new StringBuilder();
		sb.append(TextUtil.removeFinalChar(getShortDescription()));
		sb.append(":<br/><pre style=\"overflow-x:auto\">");
		sb.append(StringEscapeUtils.escapeHtml(TextUtil.removeCommonLeadingIndentation(src)));
		sb.append("</pre>");
		return sb.toString();
	}

}
