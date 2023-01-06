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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.groovy.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.util.*;
import org.apache.commons.lang3.*;
import org.codehaus.groovy.control.*;
import org.slf4j.*;

import java.util.*;
import java.util.function.*;

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

		try {
			script = new GroovyLanguageSupport().compileScript(router, src);
		}catch (MultipleCompilationErrorsException e){
			logGroovyException(null,e);
			throw new RuntimeException(e);
		}

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
		try {
			Rule rule = getRule();
			if(rule instanceof ServiceProxy){
				ServiceProxy sp = (ServiceProxy) rule;
				log.error("Exception in Groovy script in service proxy '" + sp.getName() + "' on port " + sp.getPort() + " with path " + (sp.getPath() != null ? sp.getPath().getValue() : "*"));
			} else
				log.error("Exception in Groovy script in service proxy '" + rule.getName() + "'");

			if (flow != null)
				log.error("Flow: " + flow.name());
			else
				log.error("There is possibly a syntax error in the groovy script (compilation error)");
		}catch (NoSuchElementException e2){
			//ignore - logging should not break anything
		}finally{
			e.printStackTrace();
		}
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
		sb.append(StringEscapeUtils.escapeHtml4(TextUtil.removeCommonLeadingIndentation(src)));
		sb.append("</pre>");
		return sb.toString();
	}

}
