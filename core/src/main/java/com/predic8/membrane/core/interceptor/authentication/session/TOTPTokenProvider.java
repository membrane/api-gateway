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

import java.util.Map;
import java.util.NoSuchElementException;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.authentication.session.totp.OtpProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @description A <i>token provider</i> using the Time-based One-time Password (TOTP) algorithm specified in RFC 6238 to
 *              verify tokens using a pre-shared secret.
 * @explanation <p>
 *              The <i>totpTokenProvider</i> uses the Time-based One-time Password (TOTP) algorithm specified in <a
 *              href="http://tools.ietf.org/html/rfc6238">RFC 6238</a> to verify tokens using a pre-shared secret.
 *              </p>
 *              <p>
 *              The tokens consist of 6 digits.
 *              </p>
 *              <p>
 *              The user's attribute <i>secret</i> is used as the pre-shared secret. If this attribute is missing, the
 *              login attempt fails.
 *              </p>
 *              <p>
 *              Note that the server's system time is taken into account when verifying tokens.
 *              </p>
 *              <p>
 *              It is possible, for example, to use the <a href="http://code.google.com/p/google-authenticator">Google
 *              Authenticator App</a> to store the pre-shared secret and generate such tokens.
 *              </p>
 */
@MCElement(name="totpTokenProvider", topLevel=false)
public class TOTPTokenProvider implements TokenProvider {

	Logger log = LoggerFactory.getLogger(TOTPTokenProvider.class);

	@Override
	public void init(Router router) {
		// does nothing
	}

	@Override
	public void requestToken(Map<String, String> userAttributes) {
		// does nothing
	}

	@Override
	public void verifyToken(Map<String, String> userAttributes, String token) {
		OtpProvider otpp = new OtpProvider();
		String secret;
		synchronized (userAttributes) {
			secret = userAttributes.get("secret");
		}
		long curTime = System.currentTimeMillis();
		if (!otpp.verifyCode(secret, curTime, token, 1)) {
			log.info("The given token was not equal to generated token.\nGenerated token: \"" + otpp.getNextCode(secret,curTime) + "\"\nGiven token: \"" + token + "\"\nUser: \"" + userAttributes.get("username")+ "\"");
			throw new NoSuchElementException("INVALID_TOKEN");
		}
	}

}
