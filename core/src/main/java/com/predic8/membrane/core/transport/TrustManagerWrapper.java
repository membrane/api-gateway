/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

public class TrustManagerWrapper implements X509TrustManager {
	private final boolean ignoreTimestampCheckFailure;
	private final X509TrustManager tm;

	public TrustManagerWrapper(TrustManager[] trustManagers, boolean ignoreTimestampCheckFailure) throws KeyManagementException {
		this.ignoreTimestampCheckFailure = ignoreTimestampCheckFailure;

		X509TrustManager tm2 = null;

		if (trustManagers != null)
			tm2 = chooseTrustManager(trustManagers);

		if (tm2 == null) {
			TrustManagerFactory tmf;
			try {
				tmf = TrustManagerFactory.getInstance(
						TrustManagerFactory.getDefaultAlgorithm());
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
			try {
				tmf.init((KeyStore)null);
			} catch (KeyStoreException e) {
				throw new RuntimeException(e);
			}
			tm2 = chooseTrustManager(tmf.getTrustManagers());
		}

		if (tm2 == null)
			throw new RuntimeException("No trust manager passed and could not create one.");

		tm = tm2;
	}

	private X509TrustManager chooseTrustManager(TrustManager[] tm)
			throws KeyManagementException {
		for (int i = 0; tm != null && i < tm.length; i++)
			if (tm[i] instanceof X509TrustManager)
				return (X509TrustManager) tm[i];

		return null;
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		adjustChain(chain);
		tm.checkClientTrusted(chain, authType);
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		adjustChain(chain);
		tm.checkServerTrusted(chain, authType);
	}

	private void adjustChain(X509Certificate[] chain) {
		for (int i = 0; i < chain.length; i++) {
			final X509Certificate x509 = chain[i];
			chain[i] = new X509Certificate() {

				public boolean hasUnsupportedCriticalExtension() {
					return x509.hasUnsupportedCriticalExtension();
				}

				public Set<String> getCriticalExtensionOIDs() {
					return x509.getCriticalExtensionOIDs();
				}

				@Override
				public boolean equals(Object other) {
					return x509.equals(other);
				}

				@Override
				public int hashCode() {
					return x509.hashCode();
				}

				public Set<String> getNonCriticalExtensionOIDs() {
					return x509.getNonCriticalExtensionOIDs();
				}

				@Override
				public byte[] getEncoded() throws CertificateEncodingException {
					return x509.getEncoded();
				}

				@Override
				public void verify(PublicKey key) throws CertificateException, NoSuchAlgorithmException,
				InvalidKeyException, NoSuchProviderException, SignatureException {
					x509.verify(key);
				}

				public byte[] getExtensionValue(String oid) {
					return x509.getExtensionValue(oid);
				}

				@Override
				public void verify(PublicKey key, String sigProvider) throws CertificateException,
				NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, SignatureException {
					x509.verify(key, sigProvider);
				}

				@Override
				public int getVersion() {
					return x509.getVersion();
				}

				@Override
				public BigInteger getSerialNumber() {
					return x509.getSerialNumber();
				}

				@Override
				public String toString() {
					return x509.toString();
				}

				@Override
				public PublicKey getPublicKey() {
					return x509.getPublicKey();
				}

				@Override
				public Principal getIssuerDN() {
					return x509.getIssuerDN();
				}

				@Override
				public X500Principal getIssuerX500Principal() {
					return x509.getIssuerX500Principal();
				}

				@Override
				public Principal getSubjectDN() {
					return x509.getSubjectDN();
				}

				@Override
				public X500Principal getSubjectX500Principal() {
					return x509.getSubjectX500Principal();
				}

				@Override
				public Date getNotBefore() {
					return x509.getNotBefore();
				}

				@Override
				public Date getNotAfter() {
					return x509.getNotAfter();
				}

				@Override
				public byte[] getTBSCertificate() throws CertificateEncodingException {
					return x509.getTBSCertificate();
				}

				@Override
				public byte[] getSignature() {
					return x509.getSignature();
				}

				@Override
				public String getSigAlgName() {
					return x509.getSigAlgName();
				}

				@Override
				public String getSigAlgOID() {
					return x509.getSigAlgOID();
				}

				@Override
				public byte[] getSigAlgParams() {
					return x509.getSigAlgParams();
				}

				@Override
				public boolean[] getIssuerUniqueID() {
					return x509.getIssuerUniqueID();
				}

				@Override
				public boolean[] getSubjectUniqueID() {
					return x509.getSubjectUniqueID();
				}

				@Override
				public boolean[] getKeyUsage() {
					return x509.getKeyUsage();
				}

				@Override
				public List<String> getExtendedKeyUsage() throws CertificateParsingException {
					return x509.getExtendedKeyUsage();
				}

				@Override
				public int getBasicConstraints() {
					return x509.getBasicConstraints();
				}

				@Override
				public Collection<List<?>> getSubjectAlternativeNames() throws CertificateParsingException {
					return x509.getSubjectAlternativeNames();
				}

				@Override
				public Collection<List<?>> getIssuerAlternativeNames() throws CertificateParsingException {
					return x509.getIssuerAlternativeNames();
				}

				@Override
				public void checkValidity(Date date) throws CertificateExpiredException, CertificateNotYetValidException {
					if (ignoreTimestampCheckFailure)
						return;
					x509.checkValidity(date);
				}

				@Override
				public void checkValidity() throws CertificateExpiredException, CertificateNotYetValidException {
					if (ignoreTimestampCheckFailure)
						return;
					x509.checkValidity();
				}
			};
		}
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return tm.getAcceptedIssuers();
	}

}
