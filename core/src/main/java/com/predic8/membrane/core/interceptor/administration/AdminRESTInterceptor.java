/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.administration;

import com.fasterxml.jackson.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.http.Response.*;
import com.predic8.membrane.core.interceptor.rest.*;
import com.predic8.membrane.core.interceptor.statistics.util.*;
import com.predic8.membrane.core.rules.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.transport.http2.Http2ServerHandler.*;
import static com.predic8.membrane.core.util.ComparatorFactory.*;
import static com.predic8.membrane.core.util.TextUtil.*;
import static java.nio.charset.StandardCharsets.*;

@SuppressWarnings("unused")
public class AdminRESTInterceptor extends RESTInterceptor {

	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(AdminRESTInterceptor.class.getName());

	private boolean useXForwardedForAsClientAddr;

	@Mapping("/admin/rest/clients(/?\\?.*)?")
	public Response getClients(QueryParameter params, String relativeRootPath) throws Exception {
		final List<? extends ClientStatistics> clients = getRouter().getExchangeStore().getClientStatistics();

		clients.sort(getClientStatisticsComparator(params.getString("sort", "name"),
				params.getString("order", "asc")));

		int offset = params.getInt("offset", 0);

		return json(gen -> {
			gen.writeStartObject();
			gen.writeArrayFieldStart("clients");
			for (ClientStatistics s : clients.subList(offset,
					Math.min(offset + getMax(params, clients), clients.size()))) {
				gen.writeStartObject();
				gen.writeStringField("name", s.getClient());
				gen.writeNumberField("count", s.getCount());
				gen.writeNumberField("min", s.getMinDuration());
				gen.writeNumberField("max", s.getMaxDuration());
				gen.writeNumberField("avg", s.getAvgDuration());
				gen.writeEndObject();
			}
			gen.writeEndArray();
			gen.writeNumberField("total", clients.size());
			gen.writeEndObject();
		});
	}

	private static int getMax(QueryParameter params, List<? extends ClientStatistics> clients) {
		return params.getInt("max", clients.size());
	}


	@Mapping("/admin/rest/proxies(/?\\?.*)?")
	public Response getProxies(final QueryParameter params, String relativeRootPath) throws Exception {
		final List<AbstractServiceProxy> proxies = getServiceProxies();

		if ("order".equals(params.getString("sort"))) {
			if (params.getString("order", "asc").equals("desc"))
				Collections.reverse(proxies);
		} else {
			proxies.sort(getAbstractServiceProxyComparator(params.getString("sort", "name"),
					params.getString("order", "asc")));
		}

		final int offset = params.getInt("offset", 0);
		int max = params.getInt("max", proxies.size());

		final List<AbstractServiceProxy> paginated = proxies.subList(offset,
				Math.min(offset + max, proxies.size()));

		return json(gen -> {
			gen.writeStartObject();
			gen.writeArrayFieldStart("proxies");
			int i = offset;
			if (params.getString("order", "asc").equals("desc"))
				i = proxies.size() - i + 1;
			for (AbstractServiceProxy p : paginated) {
				gen.writeStartObject();
				gen.writeNumberField("order", i += params.getString("order", "asc").equals("desc") ? -1 : 1);
				gen.writeStringField("name", p.toString());
				gen.writeBooleanField("active", p.isActive());
				if (!p.isActive())
					gen.writeStringField("error", p.getErrorState());
				gen.writeNumberField("listenPort", p.getKey().getPort());
				gen.writeStringField("virtualHost", p.getKey().getHost());
				gen.writeStringField("method", p.getKey().getMethod());
				gen.writeStringField("path", p.getKey().getPath());
				gen.writeStringField("targetHost", p.getTargetHost());
				gen.writeNumberField("targetPort", p.getTargetPort());
				gen.writeNumberField("count", p.getStatisticCollector().getCount());
				gen.writeObjectFieldStart("actions");
				if (!isReadOnly()) {
					gen.writeStringField("delete", "/admin/service-proxy/delete?name="+URLEncoder.encode(RuleUtil.getRuleIdentifier(p), UTF_8));
				}
				if (!p.isActive())
					gen.writeStringField("start", "/admin/service-proxy/start?name="+URLEncoder.encode(RuleUtil.getRuleIdentifier(p), UTF_8));
				gen.writeEndObject();
				gen.writeEndObject();
			}
			gen.writeEndArray();
			gen.writeNumberField("total", proxies.size());
			gen.writeEndObject();
		});
	}

	@Mapping("/admin/rest/exchanges/(-?\\d+)/(response|request)/raw")
	public Response getRaw(QueryParameter params, String relativeRootPath) {
		AbstractExchange exc = router.getExchangeStore().getExchangeById(params.getGroupLong(1));

		if (exc== null) {
			return Response.notFound().build();
		}

		Message msg = params.getGroup(2).equals("response")?exc.getResponse():exc.getRequest();

		if (msg == null) {
			return Response.noContent().build();
		}
		return Response.ok().contentType(TEXT_PLAIN_UTF8).body(msg.toString()).build();//TODO uses body.toString that doesn't handle different encodings than utf-8
	}

	@Mapping("/admin/web/exchanges/(-?\\d+)/(response|request)/body")
	public Response getBeautifiedBody(QueryParameter params, String relativeRootPath) throws Exception {
		AbstractExchange exc = router.getExchangeStore().getExchangeById(params.getGroupLong(1));

		if (exc== null) {
			return Response.notFound().build();
		}

		Message msg = params.getGroup(2).equals("response")?exc.getResponse():exc.getRequest();

		if (msg== null || msg.isBodyEmpty()) {
			return Response.noContent().build();
		}
		return Response.ok().contentType(TEXT_HTML_UTF8).body(formatXML(new InputStreamReader(msg.getBodyAsStreamDecoded(), msg.getCharset()), true)).build();
	}

	@Mapping("/admin/rest/exchanges/(-?\\d+)/(response|request)/body")
	public Response getRequestBody(QueryParameter params, String relativeRootPath) throws Exception {
		AbstractExchange exc = router.getExchangeStore().getExchangeById(params.getGroupLong(1));

		if (exc== null) {
			return Response.notFound().build();
		}

		Message msg = params.getGroup(2).equals("response")?exc.getResponse():exc.getRequest();
		String ct = params.getGroup(2).equals("response")?exc.getResponseContentType():exc.getRequestContentType();

		if (msg== null || msg.isBodyEmpty()) {
			return Response.noContent().build();
		}
		ResponseBuilder rb = Response.ok().contentType(ct).body(msg.getBodyAsStream(), false);
		String contentEncoding = msg.getHeader().getContentEncoding();
		if (contentEncoding != null)
			rb.header(CONTENT_ENCODING, contentEncoding);
		return rb.build();
	}

	@Mapping("/admin/rest/exchanges/(-?\\d+)/(response|request)/header")
	public Response getRequestHeader(QueryParameter params, String relativeRootPath) throws Exception {
		final AbstractExchange exc = router.getExchangeStore().getExchangeById(params.getGroupLong(1));

		if (exc== null) {
			return Response.notFound().build();
		}

		final Message msg = params.getGroup(2).equals("response")?exc.getResponse():exc.getRequest();

		if (msg== null) {
			return Response.noContent().build();
		}

		return json(gen -> {
			gen.writeStartObject();
			gen.writeArrayFieldStart("headers");
			for (HeaderField hf : msg.getHeader().getAllHeaderFields()) {
				gen.writeStartObject();
				gen.writeStringField("name", hf.getHeaderName().toString());
				gen.writeStringField("value", hf.getValue());
				gen.writeEndObject();
			}
			gen.writeEndArray();
			gen.writeEndObject();
		});
	}

	@Mapping("/admin/rest/exchanges/(-?\\d+)")
	public Response getExchange(QueryParameter params, String relativeRootPath) throws Exception {

		final AbstractExchange exc = router.getExchangeStore().getExchangeById(params.getGroupLong(1));

		if (exc== null) {
			return Response.notFound().build();
		}

		return json(gen -> writeExchange(exc, gen));
	}

	@Mapping("/admin/rest/exchanges(/?\\?.*)?")
	public Response getExchanges(QueryParameter params, String relativeRootPath) throws Exception {

		if (params.getString("waitForModification") != null) {
			getRouter().getExchangeStore().waitForModification(params.getLong("waitForModification"));
		}

		ExchangeQueryResult res = getRouter().getExchangeStore().getFilteredSortedPaged(params, useXForwardedForAsClientAddr);

		return json(gen -> {
			gen.writeStartObject();
			gen.writeArrayFieldStart("exchanges");
			for (AbstractExchange e : res.getExchanges()) {
				writeExchange(e, gen);
			}
			gen.writeEndArray();
			gen.writeNumberField("total", res.getCount());
			gen.writeNumberField("lastModified", res.getLastModified());
			gen.writeEndObject();
		});
	}

	private void writeExchange(AbstractExchange exc, JsonGenerator gen)
			throws IOException {
		gen.writeStartObject();
		gen.writeNumberField("id", exc.getId());
		if (exc.getResponse() != null) {
			gen.writeNumberField("statusCode", exc.getResponse().getStatusCode());
			if (exc.getResponseContentLength()!=-1) {
				gen.writeNumberField("respContentLength", exc.getResponseContentLength());
			} else {
				gen.writeNullField("respContentLength");
			}
		} else {
			gen.writeNullField("statusCode");
			gen.writeNullField("respContentLength");
		}
		gen.writeStringField("time", ExchangesUtil.getTime(exc));
		gen.writeStringField("proxy", exc.getRule().toString());
		gen.writeNumberField("listenPort", exc.getRule().getKey().getPort());
		if (exc.getRequest() != null) {
			gen.writeStringField("method", exc.getRequest().getMethod());
			gen.writeStringField("path", exc.getRequest().getUri());
			gen.writeStringField("reqContentType", exc.getRequestContentType());
			gen.writeStringField("protocol", exc.getProperty(HTTP2) != null? "2" : exc.getRequest().getVersion());
		} else {
			gen.writeNullField("method");
			gen.writeNullField("path");
			gen.writeNullField("reqContentType");
			if (exc.getProperty(HTTP2) != null)
				gen.writeStringField("protocol", "2");
			else
				gen.writeNullField("protocol");
		}
		gen.writeStringField("client", getClientAddr(useXForwardedForAsClientAddr, exc));
		gen.writeStringField("server", exc.getServer());
		gen.writeNumberField("serverPort",  getServerPort(exc));
		if (exc.getRequest() != null && exc.getRequestContentLength()!=-1) {
			gen.writeNumberField("reqContentLength", exc.getRequestContentLength());
		} else {
			gen.writeNullField("reqContentLength");
		}
		gen.writeStringField("respContentType", exc.getResponseContentType());
		if(exc.getStatus() == ExchangeState.RECEIVED || exc.getStatus() == ExchangeState.COMPLETED)
			if (exc.getResponse() != null && exc.getResponseContentLength()!=-1) {
				gen.writeNumberField("respContentLength", exc.getResponseContentLength());
			} else {
				gen.writeNullField("respContentLength");
			}
		else
			gen.writeStringField("respContentLength", "Not finished");

		gen.writeNumberField("duration",
				exc.getTimeResReceived() - exc.getTimeReqSent());
		gen.writeStringField("msgFilePath", JDBCUtil.getFilePath(exc));
		gen.writeEndObject();
	}

	public static String getClientAddr(boolean useXForwardedForAsClientAddr, AbstractExchange exc) {
		if (useXForwardedForAsClientAddr) {
			Request request = exc.getRequest();
			if (request != null) {
				Header header = request.getHeader();
				if (header != null) {
					String value = header.getFirstValue(X_FORWARDED_FOR);
					if (value != null)
						return value;
				}
			}
		}
		return exc.getRemoteAddr();
	}

	private int getServerPort(AbstractExchange exc) {
		return exc.getRule()instanceof AbstractServiceProxy?((AbstractServiceProxy) exc.getRule()).getTargetPort():-1;
	}

	private List<AbstractServiceProxy> getServiceProxies() {
		List<AbstractServiceProxy> rules = new LinkedList<>();
		for (Rule r : router.getRuleManager().getRules()) {
			if (!(r instanceof AbstractServiceProxy)) continue;
			rules.add((AbstractServiceProxy) r);
		}
		return rules;
	}

	public boolean isUseXForwardedForAsClientAddr() {
		return useXForwardedForAsClientAddr;
	}

	public void setUseXForwardedForAsClientAddr(boolean useXForwardedForAsClientAddr) {
		this.useXForwardedForAsClientAddr = useXForwardedForAsClientAddr;
	}
}
