/*
 * Copyright 2010 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.predic8.membrane.core.interceptor.authentication.session.totp;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.predic8.membrane.core.interceptor.authentication.session.totp.PasscodeGenerator.Signer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class containing implementation of HOTP/TOTP. Generates OTP codes for one or
 * more accounts.
 *
 * Source: http://code.google.com/p/google-authenticator
 * License: ASL 2.0
 *
 * @author Steve Weis (sweis@google.com)
 * @author Cem Paya (cemp@google.com)
 */
public class OtpProvider {
	private static Logger log = LoggerFactory.getLogger(OtpProvider.class.getName());

	private static final int PIN_LENGTH = 6; // HOTP or TOTP

	public String getNextCode(String secret, long time) {
		return computePin(secret, mTotpCounter.getValueAtTime(time / 1000));
	}

	public OtpProvider() {
		this(DEFAULT_INTERVAL);
	}

	public OtpProvider(int interval) {
		mTotpCounter = new TotpCounter(interval);
	}

	private static byte[] decodeKey(String secret) {
		return Base32String.decode(secret);
	}

	static Signer getSigningOracle(String secret) {
		try {
			byte[] keyBytes = decodeKey(secret);
			final Mac mac = Mac.getInstance("HMACSHA1");
			mac.init(new SecretKeySpec(keyBytes, ""));

			// Create a signer object out of the standard Java MAC
			// implementation.
			return new Signer() {
				@Override
				public byte[] sign(byte[] data) {
					return mac.doFinal(data);
				}
			};
		} catch (NoSuchAlgorithmException error) {
			log.error("", error);
		} catch (InvalidKeyException error) {
			log.error("", error);
		}

		return null;
	}

	/**
	 * Computes the one-time PIN given the secret key.
	 *
	 * @param secret
	 *            the secret key
	 * @param otp_state
	 *            current token state (counter or time-interval)
	 * @return the PIN
	 */
	private String computePin(String secret, long otp_state) {
		if (secret == null || secret.length() == 0) {
			throw new RuntimeException("Null or empty secret");
		}

		try {
			Signer signer = getSigningOracle(secret);
			PasscodeGenerator pcg = new PasscodeGenerator(signer, PIN_LENGTH);

			return pcg.generateResponseCode(otp_state);
		} catch (GeneralSecurityException e) {
			throw new RuntimeException("Crypto failure", e);
		}
	}

	/** Default passcode timeout period (in seconds) */
	public static final int DEFAULT_INTERVAL = 30;

	/** Counter for time-based OTPs (TOTP). */
	private final TotpCounter mTotpCounter;

	public boolean verifyCode(String secret, long time, String code, int window) {
		long t = mTotpCounter.getValueAtTime(time / 1000);
		for (int i = -window; i <= window; i++)
			if (code.equals(computePin(secret, t + i)))
				return true;
		return false;
	}
}
