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

import java.security.InvalidParameterException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.predic8.membrane.annot.MCAttribute;

public abstract class SMSTokenProvider extends NumericTokenProvider {
	private static final Logger log = LoggerFactory.getLogger(SMSTokenProvider.class.getName());

	protected String prefixText = "Token: ";
	private boolean simulate;

	@Override
	public void requestToken(Map<String, String> userAttributes) {
		String token = generateToken(userAttributes);
		String recipientNumber;

		synchronized (userAttributes) {
			recipientNumber = userAttributes.get("sms");
		}

		if (recipientNumber == null)
			throw new InvalidParameterException("User does not have the 'sms' attribute");

		recipientNumber = normalizeNumber(recipientNumber);

		String text = prefixText + token;

		if (simulate)
			log.error("Send SMS '" + text + "' to " + recipientNumber);
		else
			sendSMS(text, recipientNumber);
	}

	protected abstract void sendSMS(String text, String recipientNumber);
	protected abstract String normalizeNumber(String number);

	public String getPrefixText() {
		return prefixText;
	}
	/**
	 * @description A string that will be prepended to the token when creating the text message.
	 * @example "Token: "
	 * @default "Token: "
	 */
	@MCAttribute
	public void setPrefixText(String prefixText) {
		this.prefixText = prefixText;
	}

	public boolean isSimulate() {
		return simulate;
	}
	/**
	 * @description Don't send any text messages, only write tokens to the log.
	 */
	@MCAttribute
	public void setSimulate(boolean simulate) {
		this.simulate = simulate;
	}

}
