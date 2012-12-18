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
package com.predic8.membrane.core.interceptor.authentication.session;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

import javax.xml.stream.XMLStreamReader;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;

import com.google.common.collect.Lists;

public class TelekomSMSTokenProvider extends SMSTokenProvider {
	private static Log log = LogFactory.getLog(TelekomSMSTokenProvider.class.getName());

	private String user, password;

	private HttpClient hc = new DefaultHttpClient();
	private String smsAuthorToken;

	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		super.parseAttributes(token);
		user = token.getAttributeValue("", "user");
		password = token.getAttributeValue("", "password");
	}

	protected void sendSMS(String text, String recipientNumber) {
		// tries to send the SMS. if failing, tries once again with a new auth token
		try {
			synchronized(this) {
				if (smsAuthorToken == null)
					smsAuthorToken = getAuthToken(user, password, hc);
				try {
					sendSMS(hc, smsAuthorToken, recipientNumber, text);
				} catch (InvalidAuthTokenException e) {
					smsAuthorToken = getAuthToken(user, password, hc);
					sendSMS(hc, smsAuthorToken, recipientNumber, text);
				} catch (Exception e) {
					log.error(e);
					smsAuthorToken = getAuthToken(user, password, hc);
					sendSMS(hc, smsAuthorToken, recipientNumber, text);
				}
			}
		} catch (Exception e2) {
			throw new RuntimeException(e2);
		}
	}

	private static void sendSMS(HttpClient hc, String token, String recipientNumber, String text) throws InvalidAuthTokenException {
		HttpPost p = new HttpPost("https://gateway.developer.telekom.com/p3gw-mod-odg-sms/rest/production/sms");
		try {
			p.addHeader("Authorization", "TAuth realm=\"https://odg.t-online.de\",tauth_token=\"" + token + "\"");
			p.addHeader("Accept", "application/json");
			p.addHeader("Content-Type", "application/x-www-form-urlencoded");

			p.setEntity(new UrlEncodedFormEntity(Lists.newArrayList(
					new BasicNameValuePair("number", recipientNumber),
					new BasicNameValuePair("message", text)
			)));
			HttpResponse response = hc.execute(p);

			if (response.getStatusLine().getStatusCode() != 200) {
				String body = StringUtils.defaultString(EntityUtils.toString(response.getEntity()));
				// statusCode 0090 "Token is invalid."
				if (body.contains("\"statusCode\":\"0090\""))
					throw new InvalidAuthTokenException();
				throw new RuntimeException("Sending SMS failed: " + body);
			}

			log.debug("sent SMS to " + recipientNumber);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			p.releaseConnection();
		}
	}

	/**
	 * @return a HashMap containing the keys "tokenFormat", "tokenEncoding" and "token".
	 * @throws Exception when an error occurs (system, network or authentication)
	 */
	private static String getAuthToken(String user, String password, HttpClient hc)
			throws UnsupportedEncodingException, IOException, Exception, JsonParseException {
		HttpGet g = new HttpGet("https://sts.idm.telekom.com/rest-v1/tokens/odg");
		g.addHeader("Authorization",
				"Basic " + new String(Base64.encodeBase64((user + ":" + password).getBytes("UTF-8")), "UTF-8"));
		g.addHeader("Accept", "application/json");
		HttpResponse response = hc.execute(g);
		
		if (response.getStatusLine().getStatusCode() != 200)
			throw new Exception("Authentication failed: " + EntityUtils.toString(response.getEntity()));

		HashMap<String, String> values = parseJSONResponse(response);
		
		if (!values.containsKey("token"))
			throw new Exception("Telekom Authentication: Received 200 and JSON body, but no token.");
		
		return values.get("token");
	}

	private static HashMap<String, String> parseJSONResponse(HttpResponse g) throws IOException, JsonParseException {
		HashMap<String, String> values = new HashMap<String, String>();

		Header contentType = g.getFirstHeader("Content-Type");
		if (contentType != null && "application/json".equals(contentType.getValue())) {
			final JsonFactory jsonFactory = new JsonFactory();
			final JsonParser jp = jsonFactory.createJsonParser(new InputStreamReader(g.getEntity().getContent()));
			String name = null;
			while (jp.nextToken() != null) {
				switch (jp.getCurrentToken()) {
				case FIELD_NAME:
					name = jp.getCurrentName();
					break;
				case VALUE_STRING:
					values.put(name, jp.getText());
					break;
				default:
					break;
				}
			}
		}
		return values;
	}

	private static class InvalidAuthTokenException extends Exception {
		private static final long serialVersionUID = 1L;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
}
