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
import java.security.SecureRandom;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.xml.stream.XMLStreamReader;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.config.AbstractXmlElement;

public abstract class SMSTokenProvider extends AbstractXmlElement implements TokenProvider {
	private static Log log = LogFactory.getLog(SMSTokenProvider.class.getName());

	private final SecureRandom r = new SecureRandom();
	protected String prefixText = "Token: ";
	private boolean simulate;
	private boolean normalizeTelephoneNumber;
	
	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		prefixText = StringUtils.defaultIfBlank(token.getAttributeValue("", "prefixText"), "Token: ");
		simulate = token.getAttributeValue("", "simulate") != null;
		normalizeTelephoneNumber = token.getAttributeValue("", "normalizeTelephoneNumber") != null;
	}

	
	private long hash(Map<String, String> userAttributes) {
		long hash = 0;
		for (Map.Entry<String, String> entry : userAttributes.entrySet()) {
			hash += entry.getKey().hashCode();
			hash += 3 * entry.getValue().hashCode();
		}
		return hash;
	}
	
	@Override
	public void requestToken(Map<String, String> userAttributes) {
		int t = (int) hash(userAttributes);
		synchronized(r) {
			t = t ^ r.nextInt();
		}
		t = Math.abs(t % 1000000);
		String token = String.format("%06d", t);		
		String recipientNumber;
		synchronized (userAttributes) {
			recipientNumber = userAttributes.get("sms");
			userAttributes.put("token", token);
		}
		
		if (recipientNumber == null)
			throw new InvalidParameterException("User does not have the 'sms' attribute");
		
		if (normalizeTelephoneNumber)
			recipientNumber = recipientNumber.replaceAll("\\+", "00").replaceAll("[- ]|\\(.*\\)", "");

		String text = prefixText + token;
		
		if (simulate)
			log.error("Send SMS '" + text + "' to " + recipientNumber);
		else
			sendSMS(text, recipientNumber);
	}
	
	protected abstract void sendSMS(String text, String recipientNumber);

	@Override
	public void verifyToken(Map<String, String> userAttributes, String token) {
		String t1;
		synchronized (userAttributes) {
			t1 = userAttributes.get("token");
		}
		if (t1 == null || !t1.equals(token))
			throw new NoSuchElementException();
	}


	public String getPrefixText() {
		return prefixText;
	}


	public void setPrefixText(String prefixText) {
		this.prefixText = prefixText;
	}


	public boolean isSimulate() {
		return simulate;
	}


	public void setSimulate(boolean simulate) {
		this.simulate = simulate;
	}


	public boolean isNormalizeTelephoneNumber() {
		return normalizeTelephoneNumber;
	}


	public void setNormalizeTelephoneNumber(boolean normalizeTelephoneNumber) {
		this.normalizeTelephoneNumber = normalizeTelephoneNumber;
	}


}
