/* Copyright 2012,2014 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.authentication.session;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.util.HashMap;

import javax.annotation.concurrent.GuardedBy;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.springframework.beans.factory.annotation.Required;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.rules.NullRule;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.SSLProvider;
import com.predic8.membrane.core.util.URLParamUtil;
import com.predic8.membrane.core.util.Util;

/**
 * @explanation A <i>token provider</i> using <i>Deutsche Telekom's</i> REST interface <a
 *              href="http://www.developergarden.com/">developer garden</a> to send a randomly generated numeric token
 *              to the user via text message.
 * @description <p>
 *              The <i>telekomSMSTokenProvider</i> randomly generates a 6-digit token after the user entered her correct
 *              password.
 *              </p>
 *              <p>
 *              The token is then sent to the user via text message. The user's attribute <i>sms</i> is used as the
 *              recipient phone number. If this attribute has not been provided by the <i>user data provider</i>, the
 *              login attempt fails.
 *              </p>
 *              <p>
 *              The text message is sent via <a href="http://www.developergarden.com/">Deutsche Telekom's developer
 *              garden</a> REST API. To use this API, a registered user account with sufficient balance is necessary and
 *              the <i>Send SMS</i> API has to be enabled for this account. Membrane Service Proxy must be registered as an
 *              "application" on the developer garden website, and the "Global SMS API" must be enabled both for the user account
 *              as well as the registered application. Once completed, the <i>scope</i>, <i>clientId</i> and <i>clientSecret</i>
 *              settings must be copied from the website into Membrane's proxies.xml configuration file. Membrane uses these three
 *              parameters to identify itself when connecting to the Telekom API Gateway.
 *              </p>
 *              <p>
 *              When using a non-standard <i>environment</i> (see https://www.developergarden.com/apis/documentation/bundle/telekom-api-rest/html/sendsms.html#environmental_infos for more information),
 *              the parameters <i>senderName</i> and <i>senderAddress</i> may be used to set the SMS sender address and name.
 *              </p>
 *              <p>
 *              The token is prepended by <i>prefixText</i> to generate the text message's text.
 *              </p>
 *              <p>
 *              If <i>normalizeTelephoneNumber</i> is set, the user's <i>sms</i> attribute will be normalized according
 *              to the following rules before using it:
 *              <ul>
 *              <li>'<tt>+</tt>' is replaced by '<tt>00</tt>'.</li>
 *              <li>Any characters within round brackets, '<tt>(</tt>' and '<tt>)</tt>', are removed.</li>
 *              <li>'<tt>-</tt>' and '<tt>&#160;</tt>' are removed.</li>
 *              </ul>
 *              </p>
 */
@MCElement(name="telekomSMSTokenProvider", topLevel=false)
public class TelekomSMSTokenProvider extends SMSTokenProvider {
	private static Log log = LogFactory.getLog(TelekomSMSTokenProvider.class.getName());

	private HttpClient hc;

	private String scope, clientId, clientSecret;
	private String senderAddress = "0191011";
	private String senderName;
	private EnvironmentType environment = EnvironmentType.BUDGET;
	
	public enum EnvironmentType {
		BUDGET,
		PREMIUM,
		MOCK,
		SANDBOX,
	}
	
	@GuardedBy("this")
	private String token;
	@GuardedBy("this")
	private long tokenExpiration;

	@Override
	public void init(Router router) {
		hc = router.getResolverMap().getHTTPSchemaResolver().getHttpClient();
	}
	
	protected void sendSMS(String text, String recipientNumber) {
		recipientNumber = recipientNumber.replaceAll("^00", "\\+");
		
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			JsonFactory jsonFactory = new JsonFactory();
			JsonGenerator jg = jsonFactory.createJsonGenerator(baos, JsonEncoding.UTF8);
			
			jg.writeStartObject();
			jg.writeObjectFieldStart("outboundSMSMessageRequest");
			jg.writeArrayFieldStart("address");
			jg.writeString("tel:" + recipientNumber);
			jg.writeEndArray();
			jg.writeStringField("senderAddress", senderAddress);
			jg.writeObjectFieldStart("outboundSMSTextMessage");
			jg.writeStringField("message", text);
			jg.writeEndObject();
			jg.writeStringField("outboundEncoding", "7bitGSM");
			jg.writeStringField("clientCorrelator", "" + ((long)(Math.random() * Long.MAX_VALUE)));
			if (senderName != null)
				jg.writeStringField("senderName", senderName);
			jg.writeEndObject();
			jg.writeEndObject();
			
			jg.close();
			
			Exchange exc = new Request.Builder().method(Request.METHOD_POST).
					url("https://gateway.developer.telekom.com/plone/sms/rest/" + environment.name().toLowerCase()
							+ "/smsmessaging/v1/outbound/" + URLEncoder.encode(senderAddress, "UTF-8") + "/requests").
					header("Host", "gateway.developer.telekom.com").
					header("Authorization", "OAuth realm=\"developergarden.com\",oauth_token=\"" + getAccessToken() + "\"").
					header("Accept", "application/json").
					header("Content-Type", "application/json").
					body(baos.toByteArray()).
					buildExchange();

			exc.setRule(new NullRule() {
				@Override
				public SSLProvider getSslOutboundContext() {
					return new SSLContext(new SSLParser(), new ResolverMap(), null);
				}
			});
			hc.call(exc, false, true);
			
			if (exc.getResponse().getStatusCode() != 201)
				throw new RuntimeException("Could not send SMS: " + exc.getResponse());
			
			log.debug("sent SMS to " + recipientNumber);
		} catch (Exception e2) {
			throw new RuntimeException(e2);
		}
	}

	private synchronized String getAccessToken() throws Exception {
		long now = System.currentTimeMillis();
		if (token == null || tokenExpiration < now) {
			Exchange exc = new Request.Builder().
					method(Request.METHOD_POST).
					url("https://global.telekom.com/gcp-web-api/oauth").
					header(Header.HOST, "global.telekom.com").
					header(Header.AUTHORIZATION, "Basic " + new String(Base64.encodeBase64((clientId + ":" + clientSecret).getBytes("UTF-8")), "UTF-8")).
					header(Header.ACCEPT, "application/json").
					header(Header.USER_AGENT, Constants.PRODUCT_NAME + " " + Constants.VERSION).
					header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded").
					body(new URLParamUtil.ParamBuilder().add("grant_type", "client_credentials").add("scope", scope).build()).
					buildExchange();

			exc.setRule(new NullRule() {
				@Override
				public SSLProvider getSslOutboundContext() {
					return new SSLContext(new SSLParser(), new ResolverMap(), null);
				}
			});
			new HttpClient().call(exc, false, true);
			if (exc.getResponse().getStatusCode() != 200)
				throw new RuntimeException("Telekom Authentication Server returned: " + exc.getResponse());

			HashMap<String, String> values = Util.parseSimpleJSONResponse(exc.getResponse());

			if (!values.containsKey("access_token") || !values.containsKey("expires_in"))
				throw new Exception("Telekom Authentication: Received 200 and JSON body, but no access_token or no expires_in.");

			token = values.get("access_token");
			tokenExpiration = Long.parseLong(values.get("expires_in")) + System.currentTimeMillis() - 2000;
		}

		return token;
	}
	
	public String getScope() {
		return scope;
	}
	
	@Required
	@MCAttribute
	public void setScope(String scope) {
		this.scope = scope;
	}
	
	public String getClientId() {
		return clientId;
	}
	
	@Required
	@MCAttribute
	public void setClientId(String clientId) {
		this.clientId = clientId;
	}
	
	public String getClientSecret() {
		return clientSecret;
	}
	
	@Required
	@MCAttribute
	public void setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
	}

	public String getSenderName() {
		return senderName;
	}
	
	@MCAttribute
	public void setSenderName(String senderName) {
		this.senderName = senderName;
	}
	
	public String getSenderAddress() {
		return senderAddress;
	}
	
	@MCAttribute
	public void setSenderAddress(String senderAddress) {
		this.senderAddress = senderAddress;
	}
	
	public EnvironmentType getEnvironment() {
		return environment;
	}
	
	@MCAttribute
	public void setEnvironment(EnvironmentType environment) {
		this.environment = environment;
	}
	

}
