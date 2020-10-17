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

import com.google.common.base.Objects;
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
	private Key key;
	private KeyGenerator keyGenerator;
	private TrustStore trustStore;
	private Trust trust;
	private String algorithm;
	private String protocol;
	private String protocols;
	private String ciphers;
	private String clientAuth;
	private boolean ignoreTimestampCheckFailure;
	private String endpointIdentificationAlgorithm = "HTTPS";
	private String serverName;
	private boolean showSSLExceptions = true;
	private boolean useAsDefault = true;
	private boolean useExperimentalHttp2;

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof SSLParser))
			return false;
		SSLParser other = (SSLParser)obj;
		return Objects.equal(keyStore, other.keyStore)
				&& Objects.equal(key, other.key)
				&& Objects.equal(trustStore, other.trustStore)
				&& Objects.equal(trust, other.trust)
				&& Objects.equal(algorithm, other.algorithm)
				&& Objects.equal(protocol, other.protocol)
				&& Objects.equal(ciphers, other.ciphers)
				&& Objects.equal(clientAuth, other.clientAuth)
				&& Objects.equal(ignoreTimestampCheckFailure, other.ignoreTimestampCheckFailure)
				&& Objects.equal(endpointIdentificationAlgorithm, other.endpointIdentificationAlgorithm)
				&& Objects.equal(serverName, other.serverName)
				&& Objects.equal(showSSLExceptions, other.showSSLExceptions);
	}


	public KeyStore getKeyStore() {
		return keyStore;
	}

	@MCChildElement(order=1)
	public void setKeyStore(KeyStore keyStore) {
		this.keyStore = keyStore;
	}

	public Key getKey() {
		return key;
	}

	/**
	 * @description Used to manually compose the keystore.
	 */
	@MCChildElement(order=2)
	public void setKey(Key key) {
		this.key = key;
	}

	public KeyGenerator getKeyGenerator() {
		return keyGenerator;
	}

	/**
	 * @description Used to dynamically generate a key for the incoming connection on the fly.
	 */
	@MCChildElement(order=3)
	public void setKeyGenerator(KeyGenerator keyGenerator) {
		this.keyGenerator = keyGenerator;
	}

	public TrustStore getTrustStore() {
		return trustStore;
	}

	@MCChildElement(order=4)
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

	public String getProtocols() {
		return protocols;
	}

	/**
	 * @description <a href="http://docs.oracle.com/javase/6/docs/api/javax/net/ssl/SSLSocket.html#setEnabledProtocols%28java.lang.String[]%29">SSLSocket.setEnabledProtocols()</a>
	 * @default TLS*
	 */
	@MCAttribute
	public void setProtocols(String protocols) {
		this.protocols = protocols;
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

	public Trust getTrust() {
		return trust;
	}

	/**
	 * @description Used to manually compose the truststore.
     */
	@MCChildElement(order=5)
	public void setTrust(Trust trust) {
		this.trust = trust;
	}

	public String getEndpointIdentificationAlgorithm() {
		return endpointIdentificationAlgorithm;
	}

	/**
	 * @description See <a href="http://docs.oracle.com/javase/7/docs/api/javax/net/ssl/SSLParameters.html#setEndpointIdentificationAlgorithm%28java.lang.String%29">setEndpointIdentificationAlgorithm()</a>.
	 * @default HTTPS
     */
	@MCAttribute
	public void setEndpointIdentificationAlgorithm(String endpointIdentificationAlgorithm) {
		this.endpointIdentificationAlgorithm = endpointIdentificationAlgorithm;
	}

	public String getServerName() {
		return serverName;
	}

	/**
	* @description Setting the serverName tells Java to use the SNI (http://www.rfc-base.org/txt/rfc-3546.txt) on outbound
	*		 TLS connections to indicate to the TLS server, which hostname the client wants to connect to.
	* @default same as target hostname.
	 */
	@MCAttribute
	public void setServerName(String serverName) {
		this.serverName = serverName;
	}

	public boolean isShowSSLExceptions() {
		return showSSLExceptions;
	}

	/**
	 * @description Tells Membrane to show SSL exceptions in its log
	 * @default true
	 */
	@MCAttribute
	public void setShowSSLExceptions(boolean showSSLExceptions) {
		this.showSSLExceptions = showSSLExceptions;
	}

	public boolean isUseAsDefault() {
		return useAsDefault;
	}

	/**
	 * @description whether to use the SSLContext built from this SSLParser when no SNI header was transmitted.
	 * @default true
	 */
	@MCAttribute
	public void setUseAsDefault(boolean useAsDefault) {
		this.useAsDefault = useAsDefault;
	}

	public boolean isUseExperimentalHttp2() {
		return useExperimentalHttp2;
	}

	/**
	 * @description whether to enable receiving HTTP/2 requests. (experimental)
	 * @default false
	 */
	@MCAttribute
	public void setUseExperimentalHttp2(boolean useHttp2) {
		this.useExperimentalHttp2 = useHttp2;
	}
}
