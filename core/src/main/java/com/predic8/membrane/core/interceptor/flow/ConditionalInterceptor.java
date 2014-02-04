/* Copyright 2014 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.flow;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;

import com.google.common.base.Function;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.InterceptorFlowController;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.lang.LanguageSupport;
import com.predic8.membrane.core.lang.groovy.GroovyLanguageSupport;

/**
 * @description
 * <p>
 * 	The "if" interceptor supports conditional execution of a group of executors.
 * </p>
 * 
 * <p>
 * Note that this is a draft implementation only: Design decissions are still pending.
 * </p>
 * <ul>
 * <li>'evaluate condition only once': Should the condition be reevaluated once response handling has begun?</li>
 * <li>'evaluate condition always during request handling already' (even when 'if' is nested in 'response')</li>
 * <li>What happens to ABORT handling of interceptor A in <code>&lt;request&gt;&lt;if test="..."&gt;&lt;A /&gt;&lt;/if&gt;&lt;/response&gt;</code></li>
 * </ul>
 */
@MCElement(name="if")
public class ConditionalInterceptor extends AbstractFlowInterceptor {
	private static final Log log = LogFactory.getLog(InterceptorFlowController.class);
	
	// configuration
	private String test;
	private LanguageType language = LanguageType.GROOVY; 

	// state
	private final InterceptorFlowController interceptorFlowController = new InterceptorFlowController();
	private Function<Map<String, Object>, Boolean> condition;
	
	public enum LanguageType {
		GROOVY,
	}
	
	@Override
	public void init(Router router) throws Exception {
		super.init(router);
		LanguageSupport ls = new GroovyLanguageSupport();
		condition = ls.compileExpression(router, test);
	}
	
	private boolean testCondition(Exchange exc) {
		HashMap<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("exc", exc);
		return condition.apply(parameters);
	}
	
	@Override
	public Outcome handleRequest(Exchange exchange) throws Exception {
		boolean logDebug = log.isDebugEnabled();

		boolean handleRequest = testCondition(exchange);
		if (logDebug)
			log.debug("ConditionalInterceptor: expression evaluated to " + handleRequest);
		
		if (handleRequest) {
			return interceptorFlowController.invokeRequestHandlers(exchange, getInterceptors());
		} else
			return Outcome.CONTINUE;
	}
	
	public LanguageType getLanguage() {
		return language;
	}
	
	/**
	 * @description the language of the 'test' condition
	 * @example groovy
	 */
	@MCAttribute
	public void setLanguage(LanguageType language) {
		this.language = language;
	}
	
	public String getTest() {
		return test;
	}
	
	/**
	 * @description the condition to be tested
	 * @example exc.request.header.userAgentSupportsSNI
	 */
	@Required
	@MCAttribute
	public void setTest(String test) {
		this.test = test;
	}

}
