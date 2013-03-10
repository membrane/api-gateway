package com.predic8.membrane.core.interceptor.administration;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.ExchangesUtil;
import com.predic8.membrane.core.exchangestore.ClientStatistics;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.rest.JSONContent;
import com.predic8.membrane.core.interceptor.rest.QueryParameter;
import com.predic8.membrane.core.interceptor.rest.RESTInterceptor;
import com.predic8.membrane.core.interceptor.statistics.util.JDBCUtil;
import com.predic8.membrane.core.rules.AbstractServiceProxy;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.util.ComparatorFactory;
import com.predic8.membrane.core.util.TextUtil;

public class AdminRESTInterceptor extends RESTInterceptor {

	@Mapping("/admin/rest/clients(/?\\?.*)?")
	public Response getClients(QueryParameter params, String relativeRootPath) throws Exception {
		final List<? extends ClientStatistics> clients = getRouter().getExchangeStore().getClientStatistics();

		Collections.sort(
				clients,
				ComparatorFactory.getClientStatisticsComparator(params.getString("sort", "name"),
						params.getString("order", "asc")));

		int offset = params.getInt("offset", 0);
		int max = params.getInt("max", clients.size());

		final int total = clients.size();
		final List<? extends ClientStatistics> paginated = clients.subList(offset,
				Math.min(offset + max, clients.size()));

		return json( new JSONContent() {
			public void write(JsonGenerator gen) throws Exception {
				gen.writeStartObject();
					gen.writeArrayFieldStart("clients");
						for (ClientStatistics s : paginated) {
							gen.writeStartObject();
								gen.writeStringField("name", s.getClient());
								gen.writeNumberField("count", s.getCount());
								gen.writeNumberField("min", s.getMinDuration());
								gen.writeNumberField("max", s.getMaxDuration());
								gen.writeNumberField("avg", s.getAvgDuration());
							gen.writeEndObject();
						}					
					gen.writeEndArray();
					gen.writeNumberField("total", total);
				gen.writeEndObject();
			}
		});
	}
	
	@Mapping("/admin/rest/proxies(/?\\?.*)?")
	public Response getProxies(final QueryParameter params, String relativeRootPath) throws Exception {
		final List<AbstractServiceProxy> proxies = getServiceProxies();

		if ("order".equals(params.getString("sort"))) {
			if (params.getString("order", "asc").equals("desc"))
				Collections.reverse(proxies);
		} else {
			Collections.sort(
					proxies,
					ComparatorFactory.getAbstractServiceProxyComparator(params.getString("sort", "name"),
							params.getString("order", "asc")));
		}

		final int offset = params.getInt("offset", 0);
		int max = params.getInt("max", proxies.size());

		final List<AbstractServiceProxy> paginated = proxies.subList(offset,
				Math.min(offset + max, proxies.size()));
		
		return json( new JSONContent() {
			public void write(JsonGenerator gen) throws Exception {
				gen.writeStartObject();
				gen.writeArrayFieldStart("proxies");
					int i = offset;
					if (params.getString("order", "asc").equals("desc"))
						i = proxies.size() - i + 1;
					for (AbstractServiceProxy p : paginated) {
						gen.writeStartObject();
						gen.writeNumberField("order", i += params.getString("order", "asc").equals("desc") ? -1 : 1);
						gen.writeStringField("name", p.toString());
						gen.writeNumberField("listenPort", p.getKey().getPort());
						gen.writeStringField("virtualHost", p.getKey().getHost());
						gen.writeStringField("method", p.getKey().getMethod());
						gen.writeStringField("path", p.getKey().getPath());
						gen.writeStringField("targetHost", p.getTargetHost());
						gen.writeNumberField("targetPort", p.getTargetPort());
						gen.writeNumberField("count", p.getCount());
						gen.writeObjectFieldStart("actions");
							if (!isReadOnly()) {
								gen.writeStringField("delete", "/admin/service-proxy/delete?name="+URLEncoder.encode(RuleUtil.getRuleIdentifier(p),"UTF-8"));
							}						
						gen.writeEndObject();
						gen.writeEndObject();
					}					
					gen.writeEndArray();
					gen.writeNumberField("total", proxies.size());
				gen.writeEndObject();
			}
		});
	}
	
	@Mapping("/admin/rest/exchanges/(\\d+)/(response|request)/raw")
	public Response getRaw(QueryParameter params, String relativeRootPath) throws Exception {
		AbstractExchange exc = router.getExchangeStore().getExchangeById(params.getGroupInt(1));

		if (exc== null) {
			return Response.notFound().build();
		}

		Message msg = params.getGroup(2).equals("response")?exc.getResponse():exc.getRequest();
		
		if (msg == null) {
			return Response.noContent().build();
		}
		return Response.ok().contentType(MimeType.TEXT_PLAIN_UTF8).body(msg.toString()).build();//TODO uses body.toString that doesn't handle different encodings than utf-8
	}
	
	@Mapping("/admin/web/exchanges/(\\d+)/(response|request)/body")
	public Response getBeautifiedBody(QueryParameter params, String relativeRootPath) throws Exception {
		AbstractExchange exc = router.getExchangeStore().getExchangeById(params.getGroupInt(1));

		if (exc== null) {
			return Response.notFound().build();
		}

		Message msg = params.getGroup(2).equals("response")?exc.getResponse():exc.getRequest();
		
		if (msg== null || msg.isBodyEmpty()) {
			return Response.noContent().build();
		}
		return Response.ok().contentType(MimeType.TEXT_HTML_UTF8).body(TextUtil.formatXML(new InputStreamReader(msg.getBodyAsStream()), true)).build();
	}

	@Mapping("/admin/rest/exchanges/(\\d+)/(response|request)/body")
	public Response getRequestBody(QueryParameter params, String relativeRootPath) throws Exception {
		AbstractExchange exc = router.getExchangeStore().getExchangeById(params.getGroupInt(1));

		if (exc== null) {
			return Response.notFound().build();
		}

		Message msg = params.getGroup(2).equals("response")?exc.getResponse():exc.getRequest();
		String ct = params.getGroup(2).equals("response")?exc.getResponseContentType():exc.getRequestContentType();
		
		if (msg== null || msg.isBodyEmpty()) {
			return Response.noContent().build();
		}
		return Response.ok().contentType(ct).body(new String(msg.getBody().getContent())).build();//TODO use right charset to create string
	}

	@Mapping("/admin/rest/exchanges/(\\d+)/(response|request)/header")
	public Response getRequestHeader(QueryParameter params, String relativeRootPath) throws Exception {
		final AbstractExchange exc = router.getExchangeStore().getExchangeById(params.getGroupInt(1));

		if (exc== null) {
			return Response.notFound().build();
		}

		final Message msg = params.getGroup(2).equals("response")?exc.getResponse():exc.getRequest();

		if (msg== null) {
			return Response.noContent().build();
		}

		return json( new JSONContent() {
			public void write(JsonGenerator gen) throws Exception {
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
			}
		});
	}

	@Mapping("/admin/rest/exchanges/(\\d+)")
	public Response getExchange(QueryParameter params, String relativeRootPath) throws Exception {

		final AbstractExchange exc = router.getExchangeStore().getExchangeById(params.getGroupInt(1));

		if (exc== null) {
			return Response.notFound().build();
		}

		return json( new JSONContent() {
			public void write(JsonGenerator gen) throws Exception {
				writeExchange(exc, gen);
			}
		});
	}

	@Mapping("/admin/rest/exchanges(/?\\?.*)?")
	public Response getExchanges(QueryParameter params, String relativeRootPath) throws Exception {
		
		List<AbstractExchange> exchanges;		
		synchronized (getRouter().getExchangeStore().getAllExchangesAsList()) {
			exchanges = new ArrayList<AbstractExchange>(
					getRouter().getExchangeStore().getAllExchangesAsList());			
		}
		
		exchanges = filter(params, exchanges);
		
		Collections.sort(
				exchanges,
				ComparatorFactory.getAbstractExchangeComparator(params.getString("sort", "time"),
						params.getString("order", "desc")));

		int offset = params.getInt("offset", 0);
		int max = params.getInt("max", exchanges.size());

		final int total = exchanges.size();		
		final List<AbstractExchange> paginated = exchanges.subList(offset,
				Math.min(offset + max, exchanges.size()));
		
		return json( new JSONContent() {
			public void write(JsonGenerator gen) throws Exception {
				gen.writeStartObject();
				gen.writeArrayFieldStart("exchanges");
					for (AbstractExchange e : paginated) {
						writeExchange(e, gen);
					}					
					gen.writeEndArray();
					gen.writeNumberField("total", total);
				gen.writeEndObject();
			}
		});
	}

	private List<AbstractExchange> filter(QueryParameter params,
			List<AbstractExchange> exchanges) throws Exception {

		List<AbstractExchange> list = new ArrayList<AbstractExchange>();
		for (AbstractExchange e : exchanges) {
			if ((!params.has("proxy") || e.getRule().toString().equals(params.getString("proxy"))) &&
			    (!params.has("statuscode") || e.getResponse().getStatusCode() == params.getInt("statuscode")) &&
				(!params.has("client") || e.getSourceHostname().equals(params.getString("client"))) &&
				(!params.has("server") || params.getString("server").equals(e.getServer()==null?"":e.getServer())) &&
				(!params.has("method") || e.getRequest().getMethod().equals(params.getString("method"))) &&
				(!params.has("reqcontenttype") || e.getRequestContentType().equals(params.getString("reqcontenttype"))) &&
				(!params.has("respcontenttype") || e.getResponseContentType().equals(params.getString("respcontenttype")))) {
				list.add(e);
			}
		}
		return list;
	}

	private void writeExchange(AbstractExchange exc, JsonGenerator gen)
			throws IOException, JsonGenerationException, SQLException {
		gen.writeStartObject();
		gen.writeNumberField("id", exc.hashCode());
		gen.writeNumberField("statusCode", exc.getResponse()
				.getStatusCode());
		gen.writeStringField("time", ExchangesUtil.getTime(exc));
		gen.writeStringField("proxy", exc.getRule().toString());
		gen.writeNumberField("listenPort", exc.getRule().getKey().getPort());
		gen.writeStringField("method", exc.getRequest().getMethod());
		gen.writeStringField("path", exc.getRequest().getUri());
		gen.writeStringField("client", exc.getSourceHostname());
		gen.writeStringField("server", exc.getServer());
		gen.writeNumberField("serverPort",  getServerPort(exc));		
		gen.writeStringField("reqContentType", exc.getRequestContentType());
		if (exc.getRequestContentLength()!=-1) {
			gen.writeNumberField("reqContentLength", exc.getRequestContentLength());
		} else {
			gen.writeNullField("reqContentLength");
		}

		gen.writeStringField("respContentType", exc.getResponseContentType());
		gen.writeStringField("respContentLength",
				exc.getResponseContentType());
		if (exc.getResponseContentLength()!=-1) {
			gen.writeNumberField("respContentLength", exc.getResponseContentLength());
		} else {
			gen.writeNullField("respContentLength");
		}

		gen.writeNumberField("duration",
				exc.getTimeResReceived() - exc.getTimeReqSent());
		gen.writeStringField("msgFilePath", JDBCUtil.getFilePath(exc));
		gen.writeEndObject();
	}

	private int getServerPort(AbstractExchange exc) {
		return exc.getRule()instanceof AbstractServiceProxy?((AbstractServiceProxy) exc.getRule()).getTargetPort():-1;
	}

	private List<AbstractServiceProxy> getServiceProxies() {
		List<AbstractServiceProxy> rules = new LinkedList<AbstractServiceProxy>();
		for (Rule r : router.getRuleManager().getRules()) {
			if (!(r instanceof AbstractServiceProxy)) continue;
			rules.add((AbstractServiceProxy) r);
		}			
		return rules;
	}
}
