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

				public boolean equals(Object other) {
					return x509.equals(other);
				}

				public int hashCode() {
					return x509.hashCode();
				}

				public Set<String> getNonCriticalExtensionOIDs() {
					return x509.getNonCriticalExtensionOIDs();
				}

				public byte[] getEncoded() throws CertificateEncodingException {
					return x509.getEncoded();
				}

				public void verify(PublicKey key) throws CertificateException, NoSuchAlgorithmException,
						InvalidKeyException, NoSuchProviderException, SignatureException {
					x509.verify(key);
				}

				public byte[] getExtensionValue(String oid) {
					return x509.getExtensionValue(oid);
				}

				public void verify(PublicKey key, String sigProvider) throws CertificateException,
						NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, SignatureException {
					x509.verify(key, sigProvider);
				}

				public int getVersion() {
					return x509.getVersion();
				}

				public BigInteger getSerialNumber() {
					return x509.getSerialNumber();
				}

				public String toString() {
					return x509.toString();
				}

				public PublicKey getPublicKey() {
					return x509.getPublicKey();
				}

				public Principal getIssuerDN() {
					return x509.getIssuerDN();
				}

				public X500Principal getIssuerX500Principal() {
					return x509.getIssuerX500Principal();
				}

				public Principal getSubjectDN() {
					return x509.getSubjectDN();
				}

				public X500Principal getSubjectX500Principal() {
					return x509.getSubjectX500Principal();
				}

				public Date getNotBefore() {
					return x509.getNotBefore();
				}

				public Date getNotAfter() {
					return x509.getNotAfter();
				}

				public byte[] getTBSCertificate() throws CertificateEncodingException {
					return x509.getTBSCertificate();
				}

				public byte[] getSignature() {
					return x509.getSignature();
				}

				public String getSigAlgName() {
					return x509.getSigAlgName();
				}

				public String getSigAlgOID() {
					return x509.getSigAlgOID();
				}

				public byte[] getSigAlgParams() {
					return x509.getSigAlgParams();
				}

				public boolean[] getIssuerUniqueID() {
					return x509.getIssuerUniqueID();
				}

				public boolean[] getSubjectUniqueID() {
					return x509.getSubjectUniqueID();
				}

				public boolean[] getKeyUsage() {
					return x509.getKeyUsage();
				}

				public List<String> getExtendedKeyUsage() throws CertificateParsingException {
					return x509.getExtendedKeyUsage();
				}

				public int getBasicConstraints() {
					return x509.getBasicConstraints();
				}

				public Collection<List<?>> getSubjectAlternativeNames() throws CertificateParsingException {
					return x509.getSubjectAlternativeNames();
				}

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
