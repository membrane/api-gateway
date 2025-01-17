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
package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.core.exchange.*;
import org.slf4j.*;

import java.util.ArrayList;
import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.util.Arrays.*;
import static org.junit.jupiter.api.Assertions.*;

public class MockInterceptor extends AbstractInterceptor {

	private static final Logger log = LoggerFactory.getLogger(MockInterceptor.class);

	private static final List<String> reqLabels = new ArrayList<>();
	private static final List<String> respLabels = new ArrayList<>();
	private static final List<String> abortLabels = new ArrayList<>();

	private final String label;
	private final Set<String> failurePoints;

	public MockInterceptor(String label) {
		this(label, new String[]{});
	}

	public MockInterceptor(String label, String[] failurePoints) {
		this.label = label;
		name = "mock interceptor: "+label;
		this.failurePoints = new HashSet<>(asList(failurePoints));
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
		log.info("MockInterceptor {} handleRequest()",label);
		reqLabels.add(label);
		if (failurePoints.contains("request"))
			return ABORT;
		return CONTINUE;
	}

	@Override
	public Outcome handleResponse(Exchange exc) {
		log.info("MockInterceptor {} handleResponse()",label);
		respLabels.add(label);
		if (failurePoints.contains("response"))
			return ABORT;
		return CONTINUE;
	}

	@Override
	public void handleAbort(Exchange exchange) {
		abortLabels.add(label);
		if (failurePoints.contains("abort"))
			throw new RuntimeException("fail in abort");
	}

	public static void clear() {
		reqLabels.clear();
		respLabels.clear();
		abortLabels.clear();
	}

	public static void assertContent(List<String> reqLabels, List<String> respLabels, List<String> abortLabels) {

		log.info("Request labels:\nexpected: {}\n actual: {}\n", reqLabels, MockInterceptor.reqLabels);
		log.info("Response labels:\nexpected: {}\n actual: {}\n", respLabels, MockInterceptor.respLabels);
		log.info("Abort labels:\nexpected: {}\n actual: {}\n", abortLabels, MockInterceptor.abortLabels);

		assertIterableEquals(reqLabels, MockInterceptor.reqLabels);
		assertIterableEquals(respLabels, MockInterceptor.respLabels);
		assertIterableEquals(abortLabels, MockInterceptor.abortLabels);
	}

	public static void assertContent(String[] requests, String[] responses, String[] aborts) {
		assertContent(asList(requests), asList(responses), asList(aborts));
	}
}
