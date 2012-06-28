package com.predic8.membrane.core.interceptor.administration;

import static com.predic8.membrane.core.util.URLParamUtil.createQueryString;

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
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.rest.JSONContent;
import com.predic8.membrane.core.interceptor.rest.QueryParameter;
import com.predic8.membrane.core.interceptor.rest.RESTInterceptor;
import com.predic8.membrane.core.interceptor.statistics.util.JDBCUtil;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.util.ComparatorFactory;
import com.predic8.membrane.core.util.TextUtil;

public class AdminRESTInterceptor extends RESTInterceptor {

	@Mapping("/admin/rest/proxies/?(\\?.*)?")
	public Response getProxies(QueryParameter params, String relativeRootPath) throws Exception {
		final List<ServiceProxy> proxies = getServiceProxies();

		Collections.sort(
				proxies,
				ComparatorFactory.getServiceProxyComparator(params.getString("sort"),
						params.getString("order")));

		int offset = params.getInt("offset", 0);
		int max = params.getInt("max", proxies.size());

		final List<ServiceProxy> paginated = proxies.subList(offset,
				Math.min(offset + max, proxies.size()));
		
		return json( new JSONContent() {
			public void write(JsonGenerator gen) throws Exception {
				gen.writeStartObject();
				gen.writeArrayFieldStart("proxies");
					for (ServiceProxy p : paginated) {
						gen.writeStartObject();
						gen.writeStringField("name", p.toString());
						gen.writeNumberField("listenPort", p.getKey().getPort());
						gen.writeStringField("virtualHost", p.getKey().getHost());
						gen.writeStringField("method", p.getKey().getMethod());
						gen.writeStringField("path", p.getKey().getPath());
						gen.writeStringField("targetHost", p.getTargetHost());
						gen.writeNumberField("targetPort", p.getTargetPort());
						gen.writeNumberField("count", p.getCount());
						gen.writeStringField("details", AdminPageBuilder.createHRef("service-proxy", "show", createQueryString("name",RuleUtil.getRuleIdentifier(p))));
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
	
	private List<ServiceProxy> getServiceProxies() {
		List<ServiceProxy> rules = new LinkedList<ServiceProxy>();
		for (Rule r : router.getRuleManager().getRules()) {
			if (!(r instanceof ServiceProxy)) continue;
			rules.add((ServiceProxy) r);
		}			
		return rules;
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

//		System.out.println("********************");
//		synchronized (router.getExchangeStore()) {
//			for (AbstractExchange ae : router.getExchangeStore().getAllExchangesAsList()) {
//				System.out.println(ae.hashCode());
//			}
//		}
//		System.out.println("********************");

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
				writeRecord(exc, gen);
			}
		});
	}

	@Mapping("/admin/rest/exchanges/?(\\?.*)?")
	public Response getExchanges(QueryParameter params, String relativeRootPath) throws Exception {
		
		List<AbstractExchange> exchanges = new ArrayList<AbstractExchange>(
				getRouter().getExchangeStore().getAllExchangesAsList());
		
		exchanges = filter(params.getString("proxy"), exchanges);
		
		Collections.sort(
				exchanges,
				ComparatorFactory.getAbstractExchangeComparator(params.getString("sort"),
						params.getString("order")));

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
						writeRecord(e, gen);
					}					
					gen.writeEndArray();
					gen.writeNumberField("total", total);
				gen.writeEndObject();
			}
		});
	}

	private List<AbstractExchange> filter(String proxy,
			List<AbstractExchange> exchanges) throws Exception {

		if (proxy == null) {
			return exchanges;
		}

		List<AbstractExchange> list = new ArrayList<AbstractExchange>();
		synchronized (exchanges) {
			for (AbstractExchange e : exchanges) {
				if (e.getRule().toString().equals(proxy)) {
					list.add(e);
				}
			}
		}
		return list;
	}

	private void writeRecord(AbstractExchange exc, JsonGenerator jsonGen)
			throws IOException, JsonGenerationException, SQLException {
		jsonGen.writeStartObject();
		jsonGen.writeNumberField("id", exc.hashCode());
		jsonGen.writeNumberField("statusCode", exc.getResponse()
				.getStatusCode());
		jsonGen.writeStringField("time", ExchangesUtil.getTime(exc));
		jsonGen.writeStringField("rule", exc.getRule().toString());
		jsonGen.writeStringField("method", exc.getRequest().getMethod());
		jsonGen.writeStringField("path", exc.getRequest().getUri());
		jsonGen.writeStringField("client", exc.getSourceHostname());
		jsonGen.writeStringField("server", exc.getServer());
		jsonGen.writeStringField("reqContentType", exc.getRequestContentType());
		jsonGen.writeNumberField("reqContentLenght",
				exc.getRequestContentLength());
		jsonGen.writeStringField("respContentType",
				exc.getResponseContentType());
		jsonGen.writeNumberField("respContentLenght",
				exc.getResponseContentLength());
		jsonGen.writeNumberField("duration",
				exc.getTimeResReceived() - exc.getTimeReqSent());
		jsonGen.writeStringField("msgFilePath", JDBCUtil.getFilePath(exc));
		jsonGen.writeEndObject();
	}

}
