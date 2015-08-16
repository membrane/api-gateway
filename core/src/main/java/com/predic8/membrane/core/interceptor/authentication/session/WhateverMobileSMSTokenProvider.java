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

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.rules.NullRule;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.SSLProvider;

/**
 * @explanation A <i>token provider</i> using <i><a
 *              href="https://whatevermobile.com/">WhateverMobile</a>'s</i> REST
 *              interface to send a randomly generated numeric token to the
 *              user's phone via text message.
 * @description <p>
 *              The <i>whateverMobileSMSTokenProvider</i> randomly generates a
 *              6-digit token after the user entered her correct password.
 *              </p>
 *              <p>
 *              The token is then sent to the user via text message. The user's
 *              attribute <i>sms</i> is used as the recipient phone number. If
 *              this attribute has not been provided by the <i>user data
 *              provider</i>, the login attempt fails.
 *              </p>
 *              <p>
 *              The text message is sent via <a
 *              href="https://whatevermobile.com/">WhateverMobile</a> REST API.
 *              To use this API, a registered user account with sufficient
 *              balance is necessary. Membrane uses the specified user name and
 *              password to identify itself when connecting to the
 *              WhateverMobile SMS Gateway.
 *              </p>
 */
@MCElement(name="whateverMobileSMSTokenProvider", topLevel=false)
public class WhateverMobileSMSTokenProvider extends SMSTokenProvider {

	private static Log log = LogFactory.getLog(WhateverMobileSMSTokenProvider.class.getName());

	private HttpClient hc;

	private String user;
	private String password;
	private String senderName;
	private boolean backupServiceAvailable;

	private static final String HOST = "http.secure.api.whatevermobile.com:7011";
	private static final String GATEWAY = "https://" + HOST + "/sendsms";
	private static final String HOST2 = "http.secure.api.fra.whatevermobile.com:7011";
	private static final String GATEWAY2 = "https://" + HOST2 + "/sendsms";

	@Override
	public void init(Router router) {
		hc = router.getResolverMap().getHTTPSchemaResolver().getHttpClient();
	}

	@Override
	protected String normalizeNumber(String number) {
		return number.replaceAll("\\+", "00").replaceAll("[- ]|\\(.*\\)", "");
	}

	@Override
	protected void sendSMS(String text, String recipientNumber) {
		sendSmsToGateway(true, text, recipientNumber);
	}

	private void logSmsOK(boolean primary, String recipientNumber) {
		String tail = primary ? "Primary Gateway" : "Secondary Gateway";
		log.debug("Successfully sent SMS to " + recipientNumber + " via whateverMobile " + tail);
	}

	private void logSmsError(boolean primary, Exchange exc) {
		String head = primary ? "Primary Gateway" : "Both Primary and Secondary Gateway";
		log.error(head + " failed when sending SMS." +
			" REQUEST: " + exc.getRequest().toString() +
			( (exc.getResponse() != null)
			  ? " RESPONSE: " + exc.getResponse().toString()
			  : "")
		);
	}

	/**
	 * Send an SMS to a specific Gateway.
	 * @param primary
	 *        This specifies whether the primary gateway is used.
	 *        When false, the secondary gateway is used.
	 * @return
	 * @throws Exception
	 */
	private void sendSmsToGateway(boolean primary, String text, String recipientNumber) {
		Exchange exc = null;
		try {
			exc = new Request.Builder() // uses HTTP/1.1 which is exactly what we need here
					.post(primary ? GATEWAY : GATEWAY2)
					.header("Host", primary ? HOST : HOST2)
					.header("Content-Type", "application/x-www-form-urlencoded")
					.body(generateRequestData(senderName, recipientNumber, text))
					.buildExchange();
		} catch (URISyntaxException e1) {
			logSmsError(primary, exc);
		}
		exc.setRule(new NullRule() {
			@Override
			public SSLProvider getSslOutboundContext() {
				return new SSLContext(new SSLParser(), new ResolverMap(), null);
			}
		});
		// TODO: maybe reduce Exchange timeout

		try {
			hc.call(exc, false, true);
			// Everything went well
			if (exc.getResponse().getStatusCode() == 200) {
				logSmsOK(primary, recipientNumber);
			} else { // If we got an error
				logSmsError(primary, exc); // log it
				// try secondary gateway if possible
				if (primary && backupServiceAvailable) {
					sendSmsToGateway(false, text, recipientNumber);
				}
			}
		} catch (Exception e) {
			// Exception from hc.call(..), most probably timeout
			logSmsError(primary, exc);
		}

	}

	private String generateRequestData(String from, String to, String body) {
		String request = "";
		try {
			request = "user=" + URLEncoder.encode(this.user, "ISO-8859-15");
			request += "&password=" + URLEncoder.encode(this.password, "ISO-8859-15");
			request += "&from=" + URLEncoder.encode(from, "ISO-8859-15");
			request += "&to=" + URLEncoder.encode(to, "ISO-8859-15");
			request += "&body=" + URLEncoder.encode(body, "ISO-8859-15");
		} catch (UnsupportedEncodingException e) {
			log.error("Invalid encoding in generateRequestData.", e);
		}
		return request;
	}


	public String getGatewayUserName() {
		return user;
	}
	/**
	 * @description Your whatevermobile.com gateway user name.
	 */
	@Required
	@MCAttribute
	public void setGatewayUserName(String user) {
		this.user = user;
	}


	public String getGatewayPassword() {
		return password;
	}
	/**
	 * @description Your whatevermobile.com gateway password.
	 */
	@Required
	@MCAttribute
	public void setGatewayPassword(String pw) {
		this.password = pw;
	}


	public String getSenderName() {
		return senderName;
	}
	/**
	 * @description The sender name of the text messages. This string is displayed as the sender on the recipient's phone.
	 * @example Your Organization Name
	 * @default Membrane
	 */
	@MCAttribute
	public void setSenderName(String senderName) {
		this.senderName = senderName;
	}


	public boolean isBackupServiceAvailable() {
		return backupServiceAvailable;
	}
	/**
	 * @description Specify whether the alternative gateway is available for the configured account
	 */
	@MCAttribute
	public void setBackupServiceAvailable(boolean backup) {
		this.backupServiceAvailable = backup;
	}


}
