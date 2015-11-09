/* Copyright 2009, 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.swagger;

import java.net.URL;
import java.util.regex.Pattern;

import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.SwaggerProxy;
import org.springframework.http.MediaType;

import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import io.swagger.util.Json;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.ServiceProxy;

/**
 * @description Allow Swagger proxying
 */
@MCElement(name = "swaggerRewriter")
public class SwaggerRewriterInterceptor extends AbstractInterceptor {

	private Swagger swagger;
	private boolean rewriteUI = true;
	private String swaggerUrl;
	private String swaggerJson = "swagger.json";

	public SwaggerRewriterInterceptor() { this(null, true, "swagger.json"); } // 0-parameter ctor needed because of MCElement
	public SwaggerRewriterInterceptor(Swagger swag) {
		this(swag, true, "swagger.json");
	}
	public SwaggerRewriterInterceptor(Swagger swag, boolean rewrite) {
		this(swag, rewrite, "swagger.json");
	}
	public SwaggerRewriterInterceptor(Swagger swag, boolean rewrite, String json) {
		name = "Swagger Rewriter";
		this.swagger = swag;
		this.rewriteUI = rewrite;
		this.swaggerJson = json;
	}
	public SwaggerRewriterInterceptor(Swagger swag, String swagUrl) {
		this(swag);
		this.swaggerUrl = swagUrl;
	}

	@Override
	public void init() throws Exception {
		// inherit wsdl="..." from SoapProxy
		if (this.swagger == null) {
			Rule parent = router.getParentProxy(this);
			if (parent instanceof SwaggerProxy) {
				setSwagger(((SwaggerProxy)parent).getSwagger());
			}
		}
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		((ServiceProxy) exc.getRule()).setTargetHost(swagger.getHost());
		URL url = new URL(swaggerUrl);
		exc.getDestinations().set(0, url.getProtocol() + "://" + url.getHost() + (url.getPort() < 0 ? "" : ":"+url.getPort()) + exc.getOriginalRequestUri());
		return super.handleRequest(exc);
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {

		// replacement in swagger.json
		if (exc.getRequest().getUri().endsWith(swaggerJson) && exc.getResponseContentType().equalsIgnoreCase(MediaType.APPLICATION_JSON_VALUE)) {
			Swagger swagBody = new SwaggerParser().parse(exc.getResponse().getBodyAsStringDecoded());
			swagBody.setHost(exc2originalHostPort(exc));
			exc.getResponse().setBodyContent(Json.pretty(swagBody).getBytes(exc.getResponse().getCharset()));
		}

		// replacement in json and javascript (specifically UI)
		if (rewriteUI &&
				(exc.getRequest().getUri().matches("/.*.js(on)?")
					|| exc.getResponse().getHeader().getContentType() != null
						&& exc.getResponse().getHeader().getContentType().equals(MediaType.TEXT_HTML_VALUE)
				)) {
			String from = "(http(s)?://)" + Pattern.quote(((ServiceProxy) exc.getRule()).getTarget().getHost()) + "(/.*\\.js(on)?)";
			String to = "$1" + exc2originalHostPort(exc) + "$3";
			byte[] body = exc.getResponse().getBodyAsStringDecoded().replaceAll(from, to).getBytes(exc.getResponse().getCharset());
			exc.getResponse().setBodyContent(body);
		}

		return super.handleResponse(exc);
	}

	private String exc2originalHostPort(Exchange exc) {
		return exc.getOriginalHostHeaderHost() + (exc.getOriginalHostHeaderPort().length() > 0 ? ":" + exc.getOriginalHostHeaderPort() : "");
	}

	@Override
	public String getShortDescription() {
		String uipath = "http://" + swagger.getHost();
		String jsonpath = "http://" + swagger.getHost() + swagger.getBasePath() + "/" + swaggerJson;
		return "Rewriting <b>" + swagger.getHost() + "</b><br/>"
				+ "Allow and Rewrite UI = " + rewriteUI + "<br/>"
				+ (rewriteUI ? ("Swagger UI: <a target='_blank' href='" + uipath + "'>" + uipath + "</a><br/>") : "")
				+ "JSON Specification: <a target='_blank' href='" + jsonpath + "'>" + jsonpath + "</a><br/>";
	}

	public Swagger getSwagger() {
		return swagger;
	}
	public void setSwagger(Swagger swagger) {
		this.swagger = swagger;
	}

	public boolean isRewriteUI() {
		return rewriteUI;
	}
	/**
	 * @description Whether a Swagger-UI should also be rewritten.
	 * @default true
	 */
	@MCAttribute
	public void setRewriteUI(boolean rewriteUI) {
		this.rewriteUI = rewriteUI;
	}

	public String getSwaggerJson() {
		return swaggerJson;
	}
	/**
	 * @description Swagger specification filename. The default is 'swagger.json', which is also recommended.
	 * @default swagger.json
	 */
	@MCAttribute
	public void setSwaggerJson(String swaggerJson) {
		this.swaggerJson = swaggerJson;
	}

}
