/* Copyright 2009, 2011. 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.rules;

//import io.swagger.models.Swagger;
//import io.swagger.parser.SwaggerParser;
import com.predic8.membrane.core.interceptor.swagger.OpenAPIAdapter;
import com.predic8.membrane.core.interceptor.swagger.SwaggerAdapter;
import com.predic8.membrane.core.interceptor.swagger.SwaggerCompatibleOpenAPI;
import io.swagger.parser.SwaggerParser;
import io.swagger.v3.parser.OpenAPIV3Parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.predic8.membrane.annot.Required;

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
	private static Logger log = LoggerFactory.getLogger(SwaggerProxy.class.getName());

	private String swaggerUrl;
	private boolean allowUI = true;
	private SwaggerCompatibleOpenAPI swagger;

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
		HttpClient hc = router.getHttpClientFactory().createClient(router.getHttpClientConfig());
		Exchange ex = hc.call(new Request.Builder().get(swaggerUrl).buildExchange());
		if (ex.getResponse().getStatusCode() != 200) {
		log.error("Couldn't fetch Swagger URL!");
			throw new Exception("Couldn't fetch Swagger URL!");
		}
		// parse swaggerUrl
		swagger = new OpenAPIAdapter(new OpenAPIV3Parser().readContents(ex.getResponse().getBodyAsStringDecoded(), null, null).getOpenAPI());
		if (swagger.isNull()) {
			swagger = new SwaggerAdapter(new SwaggerParser().parse(ex.getResponse().getBodyAsStringDecoded()));
			if (swagger.isNull()) throw new Exception("couldn't parse Swagger definition");
		}
		// pass swagger specification to Swagger Key
		((SwaggerProxyKey)key).setSwagger(swagger);
		((SwaggerProxyKey)key).setAllowUI(allowUI);

		// add interceptor to position 0.
		SwaggerRewriterInterceptor sri = new SwaggerRewriterInterceptor(swagger, swaggerUrl);
		interceptors.add(0, sri);
	}

	public String getUrl() {
		return swaggerUrl;
	}
	/**
     * @description The Swagger URL. Preferably ends with 'swagger.json'.
     * @example <a href="http://petstore.swagger.io/v2/swagger.json">...</a>
     */
	@Required
	@MCAttribute
	public void setUrl(String swaggerUrl) {
		this.swaggerUrl = swaggerUrl;
	}


	public boolean isAllowUI() {
		return allowUI;
	}
	/**
	 * @description Whether to allow Swagger UI forwarding
	 * @default true
	 */
	@MCAttribute
	public void setAllowUI(boolean allowUI) {
		this.allowUI = allowUI;
	}

	public SwaggerCompatibleOpenAPI getSwagger() {
		return swagger;
	}

}
