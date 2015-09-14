package com.predic8.membrane.core.rules;

import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.swagger.SwaggerRewriterInterceptor;
import com.predic8.membrane.core.transport.http.HttpClient;

/**
 * @description <p>
 *              A service proxy that handles Swagger REST API calls.
 *              It includes a SwaggerRewriterInterceptor ex factory.
 *              </p>
 */
@MCElement(name="swaggerProxy")
public class SwaggerProxy extends ServiceProxy {
	private static Log log = LogFactory.getLog(SwaggerProxy.class.getName());

	private String swaggerUrl;

	private Swagger swagger;

	public SwaggerProxy() {
		this.key = new SwaggerProxyKey(80);
	}

	public SwaggerProxy(SwaggerProxyKey ruleKey, String targetHost, int targetPort) {
		this.key = ruleKey;
		setTargetHost(targetHost);
		setTargetPort(targetPort);
	}

	@Override
	protected AbstractProxy getNewInstance() {
		return new SwaggerProxy();
	}

	@Override
	public void init() throws Exception {
		super.init();
		// download swaggerUrl
		HttpClient hc = new HttpClient(router.getHttpClientConfig());
		Exchange ex = hc.call(new Request.Builder().get(swaggerUrl).buildExchange());
		if (ex.getResponse().getStatusCode() != 200) {
			log.fatal("Couldn't fetch Swagger URL!");
			throw new Exception("Couldn't fetch Swagger URL!");
		}
		// parse swaggerUrl
		swagger = new SwaggerParser().parse(ex.getResponse().getBodyAsStringDecoded());
		// pass swagger specification to Swagger Key
		((SwaggerProxyKey)key).setSwagger(swagger);
		interceptors.add(new SwaggerRewriterInterceptor());
	}

	public String getUrl() {
		return swaggerUrl;
	}
	/**
	 * @description The Swagger URL. Preferably ends with 'swagger.json'.
	 * @example http://petstore.swagger.io/v2/swagger.json
	 */
	@Required
	@MCAttribute
	public void setUrl(String swaggerUrl) {
		this.swaggerUrl = swaggerUrl;
	}

}
