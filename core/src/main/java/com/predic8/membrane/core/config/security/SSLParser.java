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
import com.predic8.membrane.core.config.security.acme.Acme;

/**
 * @description Configures a TLS context: the identity (key and certificate) presented to the
 * peer, the certificates trusted from it, and handshake parameters such as protocol, ciphers,
 * and client-certificate policy. Attach it to a <code>serviceProxy</code>/<code>api</code> to
 * terminate inbound TLS, or to a <code>target</code> to make an outbound connection over TLS;
 * most attributes and child elements apply to both directions, but a few - such as
 * <code>clientAuth</code> and <code>useAsDefault</code> - only take effect on an inbound context.
 * See <tt>tutorials/ssl-tls/10-TLS-Termination.yaml</tt> for inbound termination and
 * <tt>tutorials/ssl-tls/20-Central-SSL-Config.yaml</tt> for sharing one <code>ssl</code> across
 * several APIs via <code>$ref</code>.
 * <pre><code>
 * ssl:
 *   keystore: ... | key: ... | keyGenerator: ...    # this side's identity (pick one)
 *   [ truststore: ... | trust: ... ]                 # CAs trusted from the peer
 *   [ acme: ... ]                                    # obtain identity via ACME instead
 *   [ clientAuth: want | need ]                      # default: not set (inbound only)
 *   [ protocols: &lt;protocol&gt;[,&lt;protocol&gt;...] ]
 *   [ ciphers: &lt;cipher&gt;[,&lt;cipher&gt;...] ]
 *   [ insecureValidation: true | false ]             # default: false
 *   ...
 * </code></pre>
 * @topic 3. Security and Validation
 * @yaml <pre><code>
 * api:
 *   port: 8443
 *   ssl:
 *     key:
 *       private:
 *         location: membrane-key.pem
 *       certificates:
 *         - location: membrane.pem
 *   flow:
 *     - log: {}
 *   target:
 *     url: https://api.predic8.de
 * </code></pre>
 */
@MCElement(name="ssl")
public class SSLParser {

	private Acme acme;
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
	private boolean insecureValidation;
	private String endpointIdentificationAlgorithm = "HTTPS";
	private String serverName;
	private boolean showSSLExceptions = false;
	private boolean useAsDefault = true;
	private boolean useExperimentalHttp2;

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof SSLParser))
			return false;
		SSLParser other = (SSLParser)obj;
		return Objects.equal(acme, other.acme)
				&& Objects.equal(keyStore, other.keyStore)
				&& Objects.equal(key, other.key)
				&& Objects.equal(keyGenerator, other.keyGenerator)
				&& Objects.equal(trustStore, other.trustStore)
				&& Objects.equal(trust, other.trust)
				&& Objects.equal(algorithm, other.algorithm)
				&& Objects.equal(protocol, other.protocol)
				&& Objects.equal(protocols, other.protocols)
				&& Objects.equal(ciphers, other.ciphers)
				&& Objects.equal(clientAuth, other.clientAuth)
				&& Objects.equal(ignoreTimestampCheckFailure, other.ignoreTimestampCheckFailure)
				&& Objects.equal(insecureValidation, other.insecureValidation)
				&& Objects.equal(endpointIdentificationAlgorithm, other.endpointIdentificationAlgorithm)
				&& Objects.equal(serverName, other.serverName)
				&& Objects.equal(showSSLExceptions, other.showSSLExceptions)
				&& Objects.equal(useAsDefault, other.useAsDefault)
				&& Objects.equal(useExperimentalHttp2, other.useExperimentalHttp2);
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(acme, keyStore, key, keyGenerator, trustStore, trust, algorithm, protocol,
				protocols, ciphers, clientAuth, ignoreTimestampCheckFailure, insecureValidation, endpointIdentificationAlgorithm,
				serverName, showSSLExceptions, useAsDefault, useExperimentalHttp2);
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
	 * @description Comma-separated list of enabled TLS protocol versions. See <a href="http://docs.oracle.com/javase/6/docs/api/javax/net/ssl/SSLSocket.html#setEnabledProtocols%28java.lang.String[]%29">SSLSocket.setEnabledProtocols()</a>.
	 * @default all protocols the JVM enables by default, except <tt>SSLv3</tt> and <tt>SSLv2Hello</tt>
	 * @example TLSv1.2,TLSv1.3
	 */
	@MCAttribute
	public void setProtocols(String protocols) {
		this.protocols = protocols;
	}

	public String getCiphers() {
		return ciphers;
	}

	/**
	 * @description Comma-separated list of cipher suites to allow; an unknown name is rejected at
	 * startup. See <a href="http://docs.oracle.com/javase/6/docs/api/javax/net/ssl/SSLSocketFactory.html#getSupportedCipherSuites%28%29">getSupportedCipherSuites()</a>
	 * for the names the JVM supports.
	 * @default the JVM's default cipher suites, excluding <tt>RC4</tt> and <tt>3DES</tt>, ordered
	 * by preference (forward secrecy first, then AEAD, then key/hash strength)
	 * @example TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
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

	/**
	 * @description Accepts a peer certificate that is expired or not yet valid; the rest of chain
	 * validation (issuer signature, trust anchor) is unaffected. For disabling all certificate
	 * validation, see <code>insecureValidation</code> instead.
	 * @default false
	 */
	@MCAttribute
	public void setIgnoreTimestampCheckFailure(boolean ignoreTimestampCheckFailure) {
		this.ignoreTimestampCheckFailure = ignoreTimestampCheckFailure;
	}

	public boolean isInsecureValidation() {
		return insecureValidation;
	}

	/**
	 * @description Disables all certificate validation (chain-of-trust and hostname) for this
	 * SSL context, equivalent to <tt>curl -k</tt>; this also makes <code>ignoreTimestampCheckFailure</code>
	 * redundant. A configured <tt>&lt;truststore&gt;</tt> or <tt>&lt;trust&gt;</tt> is ignored
	 * while this is set. On an inbound (server) context with <tt>clientAuth="need"</tt>, a client
	 * certificate is still required but no longer validated.
	 * Only use for testing; never in production, as it removes all protection against
	 * man-in-the-middle attacks.
	 * @default false
	 */
	@MCAttribute
	public void setInsecureValidation(boolean insecureValidation) {
		this.insecureValidation = insecureValidation;
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
     * @description Hostname sent via the TLS Server Name Indication (SNI, <a href="http://www.rfc-base.org/txt/rfc-3546.txt">RFC 3546</a>)
     * extension on outbound connections, telling the server which certificate to present. Set to
     * an empty string to send no SNI extension at all.
     * @default the target's hostname
     */
	@MCAttribute
	public void setServerName(String serverName) {
		this.serverName = serverName;
	}

	public boolean isShowSSLExceptions() {
		return showSSLExceptions;
	}

	/**
	 * @description Logs SSL/TLS handshake exceptions (e.g. an untrusted or expired peer
	 * certificate) instead of only failing the connection silently.
	 * @default false
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

	public Acme getAcme() {
		return acme;
	}

	@MCChildElement(order=6)
	public void setAcme(Acme acme) {
		this.acme = acme;
	}
}
