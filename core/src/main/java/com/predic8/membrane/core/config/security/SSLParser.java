/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.config.security;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

/**
 * @description <p>Configures inbound or outbound SSL connections.</p>
 * @topic 6. Security
 */
@MCElement(name="ssl")
public class SSLParser {

	private KeyStore keyStore;
	private TrustStore trustStore;
	private String algorithm;
	private String protocol;
	private String ciphers;
	private String clientAuth;
	private boolean ignoreTimestampCheckFailure;

	public KeyStore getKeyStore() {
		return keyStore;
	}

	@MCChildElement
	public void setKeyStore(KeyStore keyStore) {
		this.keyStore = keyStore;
	}

	public TrustStore getTrustStore() {
		return trustStore;
	}

	@MCChildElement
	public void setTrustStore(TrustStore trustStore) {
		this.trustStore = trustStore;
	}
	
	public String getAlgorithm() {
		return algorithm;
	}

	/**
	 * @description <a href="http://docs.oracle.com/javase/6/docs/api/javax/net/ssl/KeyManagerFactory.html#getDefaultAlgorithm%28%29">getDefaultAlgorithm()</a>
	 * @default java default
	 * @example SunX509
	 */
	@MCAttribute
	public void setAlgorithm(String algorithm) {
		this.algorithm = algorithm;
	}
	
	public String getProtocol() {
		return protocol;
	}

	/**
	 * @description <a href="http://docs.oracle.com/javase/6/docs/api/javax/net/ssl/SSLContext.html#getInstance%28java.lang.String%29">SSLContext.getInstance()</a>
	 * @default TLS
	 */
	@MCAttribute
	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}
	
	public String getCiphers() {
		return ciphers;
	}

	/**
	 * @description Space separated list of ciphers to allow. <a href="http://docs.oracle.com/javase/6/docs/api/javax/net/ssl/SSLSocketFactory.html#getSupportedCipherSuites%28%29">getSupportedCipherSuites()</a>
	 * @default all system default ciphers
	 * @example TLS_ECDH_anon_WITH_RC4_128_SHA
	 */
	@MCAttribute
	public void setCiphers(String ciphers) {
		this.ciphers = ciphers;
	}
	
	public String getClientAuth() {
		return clientAuth;
	}

	/**
	 * @description Either not set (=no), or <tt>want</tt> or <tt>need</tt>.
	 * @default <i>not set</i>
	 * @example <tt>need</tt>
	 */
	@MCAttribute
	public void setClientAuth(String clientAuth) {
		this.clientAuth = clientAuth;
	}

	public boolean isIgnoreTimestampCheckFailure() {
		return ignoreTimestampCheckFailure;
	}
	
	@MCAttribute
	public void setIgnoreTimestampCheckFailure(boolean ignoreTimestampCheckFailure) {
		this.ignoreTimestampCheckFailure = ignoreTimestampCheckFailure;
	}

}
