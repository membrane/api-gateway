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

import java.util.ArrayList;
import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.util.Arrays.*;
import static org.junit.jupiter.api.Assertions.*;

public class MockInterceptor extends AbstractInterceptor {

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
		name = "MockInterceptor: "+label;
		this.failurePoints = new HashSet<>(asList(failurePoints));
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		System.out.println("MockInterceptor.handleRequest");
		reqLabels.add(label);
		if (failurePoints.contains("request"))
			return ABORT;
		return super.handleRequest(exc);
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		System.out.println("MockInterceptor.handleResponse");
		respLabels.add(label);
		if (failurePoints.contains("response"))
			return ABORT;
		return super.handleResponse(exc);
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
		assertEquals(reqLabels, MockInterceptor.reqLabels);
		assertEquals(respLabels, MockInterceptor.respLabels);
		assertEquals(abortLabels, MockInterceptor.abortLabels);
	}

	public static void assertContent(String[] requests, String[] responses, String[] aborts) {
		assertContent(asList(requests), asList(responses), asList(aborts));
	}
}
