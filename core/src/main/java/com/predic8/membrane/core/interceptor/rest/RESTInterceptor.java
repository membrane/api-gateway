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

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.json.JsonFactory;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.administration.*;
import org.slf4j.*;

import java.io.*;
import java.lang.reflect.*;
import java.security.*;
import java.util.regex.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;
import static com.predic8.membrane.core.util.URLParamUtil.*;

public abstract class RESTInterceptor extends AbstractInterceptor {

	private static final Logger log = LoggerFactory.getLogger(RESTInterceptor.class.getName());
	private boolean readOnly;

	private final JsonFactory jsonFactory = new JsonFactory(); // thread-safe after configuration

	@Override
	public Outcome handleRequest(Exchange exc) {
		log.debug("request: {}", exc.getOriginalRequestUri());

		exc.setTimeReqSent(System.currentTimeMillis());

        Outcome o;
        try {
            o = dispatchRequest(exc);
        } catch (Exception e) {
			internal(router.isProduction(),getDisplayName())
					.detail("Error dispatching request!")
					.exception(e)
					.buildAndSetResponse(exc);
            return ABORT;
        }

        exc.setReceived();
		exc.setTimeResReceived(System.currentTimeMillis());

		return o;
	}

	protected Response json(JSONContent content) throws Exception {
		StringWriter jsonTxt = new StringWriter();

		try (JsonGenerator gen = jsonFactory.createGenerator(jsonTxt)) {
			content.write(gen);
		}

		return Response.ok()
				.header(CONTENT_TYPE, APPLICATION_JSON_UTF8)
				.body(jsonTxt.toString()).build();
	}

	private Outcome dispatchRequest(Exchange exc) throws Exception {
		String path = router.getUriFactory().create(exc.getDestinations().getFirst()).getPath();
		for (Method m : getClass().getMethods() ) {
			Mapping a = m.getAnnotation(Mapping.class);
			if (a==null) continue;
			Matcher matcher = Pattern.compile(a.value()).matcher(path);
			if (matcher.matches()) {
				exc.setResponse((Response)m.invoke(this, getParameters(exc, path, m, matcher)));
				return RETURN;
			}
		}
		return CONTINUE;
	}

	private Object[] getParameters(Exchange exc, String path, Method m, Matcher matcher) throws Exception {
		return switch (m.getParameterTypes().length) {
			case 2 -> new Object[]{getQueryParameter(exc, matcher), getRelativeRootPath(path)};
			case 3 -> new Object[]{getQueryParameter(exc, matcher), getRelativeRootPath(path), exc};
			default -> throw new InvalidParameterException("@Mapping is supposed to annotate a 2-parameter method.");
		};
	}

	private QueryParameter getQueryParameter(Exchange exc, Matcher matcher) throws Exception {
		return new QueryParameter(getParams(router.getUriFactory(), exc, ERROR), matcher);
	}

	/**
	 * For example, returns "../.." for the input "/admin/clusters/".
	 */
	public static String getRelativeRootPath(String path) {
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
