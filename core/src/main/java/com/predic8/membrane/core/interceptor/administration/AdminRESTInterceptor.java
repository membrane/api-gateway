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


import tools.jackson.core.JsonGenerator;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.http.Response.*;
import com.predic8.membrane.core.interceptor.adminApi.AdminApiInterceptor;
import com.predic8.membrane.core.interceptor.rest.*;
import com.predic8.membrane.core.interceptor.statistics.util.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.proxies.Proxy;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Response.noContent;
import static com.predic8.membrane.core.http.Response.ok;
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

			gen.writeName("clients");
			gen.writeStartArray();
			for (ClientStatistics s : clients.subList(offset,
					Math.min(offset + getMax(params, clients), clients.size()))) {
				gen.writeStartObject();

				gen.writeName("name");
				gen.writeString(s.getClient());

				gen.writeName("count");
				gen.writeNumber(s.getCount());

				gen.writeName("min");
				gen.writeNumber(s.getMinDuration());

				gen.writeName("max");
				gen.writeNumber(s.getMaxDuration());

				gen.writeName("avg");
				gen.writeNumber(s.getAvgDuration());

				gen.writeEndObject();
			}
			gen.writeEndArray();

			gen.writeName("total");
			gen.writeNumber(clients.size());

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

			gen.writeName("proxies");
			gen.writeStartArray();
			int i = offset;
			if (params.getString("order", "asc").equals("desc"))
				i = proxies.size() - i + 1;
			for (AbstractServiceProxy p : paginated) {
				gen.writeStartObject();

				gen.writeName("order");
				gen.writeNumber(i += params.getString("order", "asc").equals("desc") ? -1 : 1);

				gen.writeName("name");
				gen.writeString(p.toString());

				gen.writeName("active");
				gen.writeBoolean(p.isActive());

				if (!p.isActive()) {
					gen.writeName("error");
					gen.writeString(p.getErrorState());
				}

				gen.writeName("listenPort");
				gen.writeNumber(p.getKey().getPort());

				gen.writeName("virtualHost");
				gen.writeString(p.getKey().getHost());

				gen.writeName("method");
				gen.writeString(p.getKey().getMethod());

				gen.writeName("path");
				gen.writeString(p.getKey().getPath());

				gen.writeName("targetHost");
				gen.writeString(p.getTargetHost());

				gen.writeName("targetPort");
				gen.writeNumber(p.getTargetPort());

				gen.writeName("count");
				gen.writeNumber(p.getStatisticCollector().getCount());

				gen.writeName("actions");
				gen.writeStartObject();
				if (!isReadOnly()) {
					gen.writeName("delete");
					gen.writeString("/admin/service-proxy/delete?name=" +
							URLEncoder.encode(RuleUtil.getRuleIdentifier(p), UTF_8));
				}
				if (!p.isActive()) {
					gen.writeName("start");
					gen.writeString("/admin/service-proxy/start?name=" +
							URLEncoder.encode(RuleUtil.getRuleIdentifier(p), UTF_8));
				}
				gen.writeEndObject(); // actions

				gen.writeEndObject(); // proxy
			}
			gen.writeEndArray();

			gen.writeName("total");
			gen.writeNumber(proxies.size());

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
			return noContent().build();
		}
		return ok().contentType(TEXT_PLAIN_UTF8).body(msg.getBodyAsStringDecoded()).build();
	}

	@Mapping("/admin/web/exchanges/(-?\\d+)/(response|request)/body")
	public Response getBeautifiedBody(QueryParameter params, String relativeRootPath) throws Exception {
		AbstractExchange exc = router.getExchangeStore().getExchangeById(params.getGroupLong(1));

		if (exc== null) {
			return Response.notFound().build();
		}

		Message msg = params.getGroup(2).equals("response")?exc.getResponse():exc.getRequest();

		if (msg== null || msg.isBodyEmpty()) {
			return noContent().build();
		}
		return ok().contentType(TEXT_HTML_UTF8)
				.body(formatXML(msg.getBodyAsStreamDecoded(), true))
				.build();
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
			return noContent().build();
		}
		ResponseBuilder rb = ok().contentType(ct).body(msg.getBodyAsStream(), false);
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
			return noContent().build();
		}

		return json(gen -> {
			gen.writeStartObject();

			gen.writeName("headers");
			gen.writeStartArray();
			for (HeaderField hf : msg.getHeader().getAllHeaderFields()) {
				gen.writeStartObject();

				gen.writeName("name");
				gen.writeString(hf.getHeaderName().toString());

				gen.writeName("value");
				gen.writeString(hf.getValue());

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

			gen.writeName("exchanges");
			gen.writeStartArray();
			for (AbstractExchange e : res.getExchanges()) {
				writeExchange(e, gen);
			}
			gen.writeEndArray();

			gen.writeName("total");
			gen.writeNumber(res.getCount());

			gen.writeName("lastModified");
			gen.writeNumber(res.getLastModified());

			gen.writeEndObject();
		});
	}

	private void writeExchange(AbstractExchange exc, JsonGenerator gen) {
		gen.writeStartObject();

		gen.writeName("id");
		gen.writeNumber(exc.getId());

		if (exc.getResponse() != null) {
			gen.writeName("statusCode");
			gen.writeNumber(exc.getResponse().getStatusCode());

			if (exc.getResponseContentLength() != -1) {
				gen.writeName("respContentLength");
				gen.writeNumber(exc.getResponseContentLength());
			} else {
				gen.writeName("respContentLength");
				gen.writeNull();
			}
		} else {
			gen.writeName("statusCode");
			gen.writeNull();

			gen.writeName("respContentLength");
			gen.writeNull();
		}

		gen.writeName("time");
		gen.writeString(ExchangesUtil.getTime(exc));

		if (exc.getProxy() != null) {
			gen.writeName("proxy");
			gen.writeString(exc.getProxy().toString());

			gen.writeName("listenPort");
			gen.writeNumber(exc.getProxy().getKey().getPort());
		} else {
			gen.writeName("proxy");
			gen.writeString("UNKNOWN");

			gen.writeName("listenPort");
			gen.writeNull();
		}

		if (exc.getRequest() != null) {
			gen.writeName("method");
			gen.writeString(exc.getRequest().getMethod());

			gen.writeName("path");
			gen.writeString(exc.getRequest().getUri());

			gen.writeName("reqContentType");
			gen.writeString(exc.getRequestContentType());

			gen.writeName("protocol");
			gen.writeString(exc.getProperty(HTTP2_SERVER) != null ? "2" : exc.getRequest().getVersion());
		} else {
			gen.writeName("method");
			gen.writeNull();

			gen.writeName("path");
			gen.writeNull();

			gen.writeName("reqContentType");
			gen.writeNull();

			gen.writeName("protocol");
			String proto = exc.getProperty(HTTP2_SERVER) != null ? "2" : null;
			if (proto == null) {
				gen.writeNull();
			} else {
				gen.writeString(proto);
			}
		}

		gen.writeName("client");
		gen.writeString(getClientAddr(useXForwardedForAsClientAddr, exc));

		gen.writeName("server");
		gen.writeString(exc.getServer());

		gen.writeName("serverPort");
		gen.writeNumber(getServerPort(exc));

		if (exc.getRequest() != null && exc.getRequestContentLength() != -1) {
			gen.writeName("reqContentLength");
			gen.writeNumber(exc.getRequestContentLength());
		} else {
			gen.writeName("reqContentLength");
			gen.writeNull();
		}

		gen.writeName("respContentType");
		gen.writeString(exc.getResponseContentType());

		if (exc.getStatus() == ExchangeState.RECEIVED || exc.getStatus() == ExchangeState.COMPLETED) {
			if (exc.getResponse() != null && exc.getResponseContentLength() != -1) {
				gen.writeName("respContentLength");
				gen.writeNumber(exc.getResponseContentLength());
			} else {
				gen.writeName("respContentLength");
				gen.writeNull();
			}
		} else {
			gen.writeName("respContentLength");
			gen.writeString("Not finished");
		}

		gen.writeName("duration");
		gen.writeNumber(exc.getTimeResReceived() - exc.getTimeReqSent());

		gen.writeName("msgFilePath");
		gen.writeString(JDBCUtil.getFilePath(exc));

		gen.writeEndObject();
	}

	public static String getClientAddr(boolean useXForwardedForAsClientAddr, AbstractExchange exc) {
        return AdminApiInterceptor.getClientAddr(useXForwardedForAsClientAddr, exc);
	}

	private int getServerPort(AbstractExchange exc) {
		return exc.getProxy()instanceof AbstractServiceProxy?((AbstractServiceProxy) exc.getProxy()).getTargetPort():-1;
	}

	private List<AbstractServiceProxy> getServiceProxies() {
		List<AbstractServiceProxy> rules = new LinkedList<>();
		for (Proxy r : router.getRuleManager().getRules()) {
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
