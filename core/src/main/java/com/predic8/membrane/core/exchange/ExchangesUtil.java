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

package com.predic8.membrane.core.exchange;

import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Request;

import java.time.*;
import java.time.format.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.predic8.membrane.core.Constants.*;

public class ExchangesUtil {

	public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

	public static String getStatusCode(AbstractExchange exc) {
		if (exc.getResponse() == null)
			return "";
		return "" + exc.getResponse().getStatusCode();
	}

	public static String getTime(AbstractExchange exc) {
		if (exc.getTime() == null)
			return UNKNOWN;
		return DATE_FORMATTER
				.withZone(ZoneId.systemDefault())
				.format(exc.getTime().getTime().toInstant());
	}

	public static String getRequestContentLength(AbstractExchange exc) {
		if (exc.getRequestContentLength() == -1)
			return UNKNOWN;
		return "" + exc.getRequestContentLength();
	}

	public static String getResponseContentLength(AbstractExchange exc) {
		if (exc.getResponseContentLength() == -1)
			return UNKNOWN;
		return "" + exc.getResponseContentLength();
	}

	public static String getResponseContentType(AbstractExchange exc) {
		if (exc.getResponse() == null)
			return N_A;
		return exc.getResponseContentType();
	}

	public static String getTimeDifference(AbstractExchange exc) {
		return "" + (exc.getTimeResReceived() - exc.getTimeReqSent());
	}

	public static Exchange copyRequestExchange(AbstractExchange exc) {
		Exchange newExc = new Request.Builder()
				.method(exc.getRequest().getMethod())
				.body(exc.getRequest().getBodyAsStream())
				.header(copyHeader(exc))
				.buildExchange();
		newExc.setProxy(exc.getProxy());
		newExc.setProperties(new HashMap<>(exc.getProperties()));
		newExc.setDestinations(exc.getDestinations());
		return newExc;
	}

	public static Header copyHeader(AbstractExchange exc) {
		Header newHeader = new Header();
		Arrays.stream(exc.getRequest().getHeader().getAllHeaderFields()).forEach(header -> newHeader.add(header.getHeaderName().getName(), header.getValue()));
		return newHeader;
	}
}
