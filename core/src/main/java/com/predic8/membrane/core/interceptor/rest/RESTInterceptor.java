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
package com.predic8.membrane.core.interceptor.rest;

import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.security.InvalidParameterException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.administration.Mapping;
import com.predic8.membrane.core.util.URLParamUtil;
import com.predic8.membrane.core.util.URLUtil;

public abstract class RESTInterceptor extends AbstractInterceptor {
	private static Log log = LogFactory.getLog(RESTInterceptor.class.getName());
	private boolean readOnly;

	private final JsonFactory jsonFactory = new JsonFactory(); // thread-safe after configuration
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		log.debug("request: " + exc.getOriginalRequestUri());

		exc.setTimeReqSent(System.currentTimeMillis());
		
		Outcome o = dispatchRequest(exc);
		
		exc.setReceived();
		exc.setTimeResReceived(System.currentTimeMillis());

		return o;
	}

	protected Response json(JSONContent content) throws Exception {
		StringWriter jsonTxt = new StringWriter();

		JsonGenerator gen = jsonFactory.createJsonGenerator(jsonTxt);
		content.write(gen);
		gen.flush();
		
		return Response.ok().body(jsonTxt.toString()).build();
	}

	private Outcome dispatchRequest(Exchange exc) throws Exception {
		String pathQuery = URLUtil.getPathQuery(router.getUriFactory(), exc.getDestinations().get(0));
		for (Method m : getClass().getMethods() ) {
			Mapping a = m.getAnnotation(Mapping.class);
			if (a==null) continue;
			Matcher matcher = Pattern.compile(a.value()).matcher(pathQuery);
			if (matcher.matches()) {
				Object[] parameters;
				switch (m.getParameterTypes().length) {
				case 2:
					parameters = new Object[] { new QueryParameter(URLParamUtil.getParams(router.getUriFactory(), exc), matcher), getRelativeRootPath(pathQuery) };
					break;
				case 3:
					parameters = new Object[] { new QueryParameter(URLParamUtil.getParams(router.getUriFactory(), exc), matcher), getRelativeRootPath(pathQuery), exc };
					break;
				default:
					throw new InvalidParameterException("@Mapping is supposed to annotate a 2-parameter method.");
				}
				exc.setResponse((Response)m.invoke(this, parameters));
				return Outcome.RETURN;
			}
		}
		return Outcome.CONTINUE;
	}

	/**
	 * For example, returns "../.." for the input "/admin/clusters/".
	 */
	public static String getRelativeRootPath(String pathQuery) throws MalformedURLException {
		String path = URLUtil.getPathFromPathQuery(pathQuery);
		// count '/'s
		int depth = 0;
		for (int i = 0; i < path.length(); i++)
			if (path.charAt(i) == '/')
				depth++;
		// remove leading '/'
		if (depth > 0)
			depth--;
		// build relative path for depth
		StringBuilder relativeRootPath = new StringBuilder();
		if (depth == 0)
			relativeRootPath.append(".");
		else
			for (; depth>0; depth--)
				if (depth == 1)
					relativeRootPath.append("..");
				else
					relativeRootPath.append("../");
		return relativeRootPath.toString();
	}

	public boolean isReadOnly() {
		return readOnly;
	}
	
	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}

}
